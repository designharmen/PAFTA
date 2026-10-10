package com.harmen.pafta.ui.viewport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.harmen.pafta.geometry.Camera3D
import com.harmen.pafta.geometry.Vec3
import com.harmen.pafta.project.Face3
import com.harmen.pafta.project.FaceKind
import com.harmen.pafta.project.LayerState
import com.harmen.pafta.project.bounds
import com.harmen.pafta.ui.theme.HarmenColours

/**
 * The building, seen from outside.
 *
 * Everything PAFTA has been storing about height since the first wall — a
 * wall's height, a door's head and sill, a column, a slab's level — finally
 * showing. The plan is still the drawing; this is a way of looking at it.
 *
 * Drawn by sorting the faces back to front and filling them in that order,
 * which is the oldest trick there is and exactly right here: a house is a few
 * hundred flat faces, none of them passing through another, and the alternative
 * — a depth buffer and a graphics pipeline — would be a native dependency, a
 * licence question and a thing that cannot be tested on a machine with no
 * screen. Every piece of the arithmetic here is in `core` and has tests.
 */
@Composable
public fun ModelViewport(
    faces: List<Face3>,
    layers: List<LayerState>,
    modifier: Modifier = Modifier,
    /** The shape picked out in the plan, shown picked out here too. */
    highlightedId: String? = null,
) {
    var surface by remember { mutableStateOf(IntSize.Zero) }
    var camera by remember { mutableStateOf<Camera3D?>(null) }

    // Framed round whatever has been drawn, and re-framed only when what is
    // drawn changes shape enough to matter — not on every edit, which would
    // pull the view back every time a wall grew by a millimetre.
    val model = remember(faces) { faces.bounds() }
    val fitted = remember(model) { Camera3D.framing(model) }
    val active = camera ?: fitted

    val visible = remember(faces, layers) {
        val byName = layers.associateBy { it.name }
        faces.filter { face ->
            val state = byName[face.layer]
            state == null || (state.visible && state.opacity > 0.01)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HarmenColours.Canvas)
            .clipToBounds()
            .onSizeChanged { surface = it }
            .pointerInput(Unit) {
                // The same primitives the plan's own gestures are built from,
                // and deliberately not the `calculatePan`/`calculateZoom`
                // helpers: one way of reading two fingers in this project is
                // one way to get wrong.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var previous: Map<PointerId, Offset> = mapOf(down.id to down.position)

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        val current = pressed.associate { it.id to it.position }
                        val shared = current.keys.filter { previous.containsKey(it) }
                        if (shared.isNotEmpty()) {
                            val before = middleOf(shared.map { previous.getValue(it) })
                            val after = middleOf(shared.map { current.getValue(it) })
                            val pan = after - before
                            val now = camera ?: fitted

                            camera = if (shared.size > 1) {
                                // Two fingers: pinch to come closer, drag to
                                // slide the building across the glass.
                                val spreadBefore = spreadFrom(
                                    shared.map { previous.getValue(it) },
                                    before,
                                )
                                val spreadAfter = spreadFrom(
                                    shared.map { current.getValue(it) },
                                    after,
                                )
                                val zoom =
                                    if (spreadBefore > 0.01f) spreadAfter / spreadBefore else 1f
                                now
                                    .pannedBy(
                                        -now.lengthFor(pan.x.toDouble(), surface),
                                        now.lengthFor(pan.y.toDouble(), surface),
                                    )
                                    .zoomedBy(zoom.toDouble())
                            } else {
                                // One finger walks round it. A quarter of a
                                // degree per pixel puts a full turn at about
                                // the width of a tablet, which is the amount of
                                // drag a hand expects to go once round
                                // something.
                                now.orbitedBy(
                                    yawDelta = -pan.x.toDouble() * 0.25,
                                    pitchDelta = pan.y.toDouble() * 0.25,
                                )
                            }
                            pressed.forEach { it.consume() }
                        }
                        previous = current
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (visible.isEmpty()) return@Canvas
            drawFaces(visible, active, size.width.toDouble(), size.height.toDouble(), highlightedId)
        }
    }
}

/** The middle of however many fingers are down. */
private fun middleOf(points: List<Offset>): Offset {
    if (points.isEmpty()) return Offset.Zero
    var x = 0f
    var y = 0f
    for (p in points) {
        x += p.x
        y += p.y
    }
    return Offset(x / points.size, y / points.size)
}

/** How far apart the fingers are, as their average distance from their middle. */
private fun spreadFrom(points: List<Offset>, middle: Offset): Float {
    if (points.isEmpty()) return 0f
    var total = 0f
    for (p in points) total += (p - middle).getDistance()
    return total / points.size
}

/** A screen distance as a model distance at the depth the camera is looking. */
private fun Camera3D.lengthFor(pixels: Double, surface: IntSize): Double {
    if (surface.height <= 0) return 0.0
    val focal = (surface.height / 2.0) / kotlin.math.tan(Math.toRadians(fovDegrees) / 2.0)
    return pixels * distanceMm / focal
}

/**
 * The faces, filled back to front.
 *
 * Sorted by how far the middle of each is from the eye. Two faces that pass
 * through each other would need cutting apart first; a building has none,
 * because walls meet at their faces and a floor stops at the wall it meets.
 */
private fun DrawScope.drawFaces(
    faces: List<Face3>,
    camera: Camera3D,
    width: Double,
    height: Double,
    highlightedId: String?,
) {
    val eye = camera.eye
    // Sorted once per frame; a house is a few hundred faces and this is
    // nothing next to filling them.
    val sorted = faces.sortedByDescending { (it.centre - eye).lengthSquared }

    for (face in sorted) {
        val screen = face.points.map { camera.project(it, width, height) }
        // Any corner behind the camera and the whole face is dropped: a face
        // half behind you projects to a shape stretched across the sky, which
        // is the classic way a 3D view goes wrong.
        if (screen.any { it == null }) continue

        val path = Path()
        screen.forEachIndexed { i, point ->
            val p = point!!.screen
            if (i == 0) path.moveTo(p.x.toFloat(), p.y.toFloat())
            else path.lineTo(p.x.toFloat(), p.y.toFloat())
        }
        path.close()

        val lit = face.shade()
        val base = face.kind.colour()
        drawPath(path, base.copy(alpha = base.alpha).let { it.darkened(lit) })
        drawPath(
            path,
            if (face.id == highlightedId) HarmenColours.Accent else HarmenColours.Hairline,
            style = Stroke(width = if (face.id == highlightedId) 2f else 0.8f),
        )
    }
}

/**
 * How much light a face catches, from 0.35 to 1.
 *
 * One fixed light, high and over the left shoulder — the direction every
 * architectural drawing has been lit from for two hundred years, because it
 * puts the shadow where the eye expects it. Nothing goes fully black: a face in
 * shadow still has to be read.
 */
private fun Face3.shade(): Float {
    val light = Vec3(-0.4, -0.5, 0.76).normalized()
    val facing = (normal dot light).toFloat()
    return (0.35f + 0.65f * ((facing + 1f) / 2f)).coerceIn(0.35f, 1f)
}

private fun Color.darkened(by: Float): Color =
    Color(red * by, green * by, blue * by, alpha)

/**
 * What each part of the building is made to look like.
 *
 * No new colours: the palette allows none. What separates a wall from a floor
 * from a piece of furniture is how light each one is, which is also how a
 * drawing does it.
 */
private fun FaceKind.colour(): Color = when (this) {
    FaceKind.WALL -> HarmenColours.Linework.copy(alpha = 0.92f)
    FaceKind.WALL_TOP -> HarmenColours.Linework.copy(alpha = 1f)
    FaceKind.OPENING_REVEAL -> HarmenColours.Linework.copy(alpha = 0.72f)
    FaceKind.COLUMN -> HarmenColours.Linework.copy(alpha = 0.98f)
    FaceKind.COLUMN_TOP -> HarmenColours.Linework.copy(alpha = 1f)
    FaceKind.SLAB -> HarmenColours.Linework.copy(alpha = 0.55f)
    FaceKind.FURNITURE -> HarmenColours.Linework.copy(alpha = 0.78f)
    // A face out of somebody else's file says nothing about itself, so it gets
    // the plain tone and lets the shading do all the work of showing its shape.
    FaceKind.MODEL -> HarmenColours.Linework.copy(alpha = 0.88f)
}
