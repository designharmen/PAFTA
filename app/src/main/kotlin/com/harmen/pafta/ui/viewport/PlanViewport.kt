package com.harmen.pafta.ui.viewport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.harmen.pafta.dxf.DxfDrawing
import com.harmen.pafta.dxf.DxfEntity
import com.harmen.pafta.dxf.DxfTextAlign
import com.harmen.pafta.geometry.Aabb
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Viewport2D
import com.harmen.pafta.geometry.tessellateArc
import com.harmen.pafta.measure.Measurement
import com.harmen.pafta.measure.MeasurementDisplay
import com.harmen.pafta.measure.label
import com.harmen.pafta.project.LayerState
import com.harmen.pafta.project.OpeningPlan
import com.harmen.pafta.project.WallBand
import com.harmen.pafta.project.WallMaterial
import com.harmen.pafta.project.ZonePlan
import com.harmen.pafta.ui.theme.HarmenColours
import com.harmen.pafta.ui.theme.HarmenType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** A room name drawn on the plan. */
public data class RoomLabel(val name: String, val position: Vec2)

/**
 * The drawing surface.
 *
 * Everything is drawn from model coordinates through a [Viewport2D], so a pinch
 * or drag only changes that one transform — the linework, the dimensions and the
 * labels all stay registered to each other at any zoom.
 */
@Composable
public fun PlanViewport(
    drawing: DxfDrawing,
    layers: List<LayerState>,
    measurements: List<Measurement>,
    roomLabels: List<RoomLabel>,
    display: MeasurementDisplay,
    unitLabel: String,
    gridVisible: Boolean,
    gridSpacingMm: Double,
    modifier: Modifier = Modifier,
    /** Points of a measurement the user has started but not finished. */
    pendingPicks: List<Vec2> = emptyList(),
    /**
     * Geometry the user has drawn since the file was opened.
     *
     * Kept as a separate list rather than folded into [drawing] on purpose: the
     * viewport keys its zoom-to-fit on the drawing, so rebuilding that object
     * every time a wall is added would jerk the view back to fit on every tap.
     */
    drawn: List<DxfEntity> = emptyList(),
    /**
     * The walls, as solid bands.
     *
     * Walls are drawn filled rather than as outlines, which is both how a plan
     * shows a wall and what makes a corner read as a corner: two filled bands
     * that overlap are one solid shape, while two outlines that overlap are four
     * lines crossing in the corner.
     */
    walls: List<WallBand> = emptyList(),
    /**
     * Doors and windows, already worked out against the walls they are in.
     *
     * They are handed in beside the walls rather than with them because each
     * one takes a piece out of a wall before anything is drawn: a doorway is a
     * hole, and a hole drawn on top of a wall is a rectangle on a wall.
     */
    openings: List<OpeningPlan> = emptyList(),
    /** Rooms, with the floor they cover and what to write on it. */
    zones: List<ZonePlan> = emptyList(),
    /**
     * The name and area written on a room, already in Turkish.
     *
     * Worked out by the screen, not here: the words and the way a number is
     * written belong in `strings.xml` and in the unit settings, and the
     * viewport knows neither.
     */
    zoneLabel: (ZonePlan) -> List<String> = { emptyList() },
    /** The selected shape's geometry, drawn again on top in the accent colour. */
    highlighted: List<DxfEntity> = emptyList(),
    /** The selected wall, whose band is outlined in the accent colour. */
    highlightedWallId: String? = null,
    /** A point the finger has been pulled onto, marked so the user can see it. */
    snapAt: Vec2? = null,
    /**
     * What the opening view is framed around.
     *
     * Normally the file's own extent, but a project may hold hand-drawn walls
     * that reach past it — or have come from a file with nothing in it at all —
     * and opening onto an empty corner of the sheet would look like the work
     * had been lost.
     */
    fitBounds: Aabb? = null,
    /** The shape following the finger right now; not part of the drawing yet. */
    preview: List<DxfEntity> = emptyList(),
    /** The length of that shape, already worded and in the user's unit. */
    previewLabel: String? = null,
    /**
     * Whether a drawing tool is active.
     *
     * It changes what one finger means: with a drawing tool it draws, without
     * one it moves the view. Two fingers always move the view, so there is
     * always a way to get around the plan.
     */
    drawEnabled: Boolean = false,
    /**
     * A tap, as a model point plus the snap tolerance in model units.
     *
     * The tolerance is computed here rather than in the view model because only
     * the viewport knows the current zoom: a finger covers a fixed distance on
     * glass and a wildly different distance in the drawing depending on scale.
     */
    onPick: (Vec2, Double) -> Unit = { _, _ -> },
    /** The finger went down with a drawing tool active. */
    onDrawBegin: (Vec2, Double) -> Unit = { _, _ -> },
    /** The finger moved; the shape follows it. */
    onDrawMove: (Vec2, Double) -> Unit = { _, _ -> },
    /** The finger lifted: draw the shape at the size it was dragged. */
    onDrawEnd: () -> Unit = {},
    /** The drag became a pinch, or ended without going anywhere. */
    onDrawCancel: () -> Unit = {},
    /**
     * Takes hold of whatever is under the finger, answering whether it caught
     * anything.
     *
     * The viewport cannot know: it has pixels, and what is under them is the
     * drawing's business. So it asks, and moves the shape when the answer is
     * yes and the view when it is no.
     */
    onGrab: (Vec2, Double) -> Boolean = { _, _ -> false },
    /** The held shape follows the finger. */
    onMoveTo: (Vec2) -> Unit = {},
    /** The finger lifted, so the move is done. */
    onMoveEnd: () -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    var surface by remember { mutableStateOf(IntSize.Zero) }
    var viewport by remember { mutableStateOf<Viewport2D?>(null) }

    // Fit once the surface is measured, and re-fit if the drawing is replaced.
    val frame = fitBounds ?: drawing.bounds
    val fitted = remember(frame, surface) {
        if (surface.width == 0 || surface.height == 0) {
            null
        } else {
            Viewport2D.fit(
                frame,
                surface.width.toDouble(),
                surface.height.toDouble(),
                paddingPixels = 72.0,
            )
        }
    }
    val active = viewport ?: fitted

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HarmenColours.Canvas)
            // Without this the plan is drawn straight over the tool rail, the
            // panel and the top bar: a canvas in Compose is not held inside its
            // own box unless it is told to be. Zooming in made the drawing crawl
            // out across the whole screen.
            .clipToBounds()
            .onSizeChanged { newSize ->
                if (newSize != surface) {
                    surface = newSize
                    // A resize (rotation, split screen) re-fits rather than
                    // leaving the drawing half off the edge.
                    viewport = null
                }
            }
            .pointerInput(drawing, drawEnabled) {
                val snapRadiusPx = SNAP_RADIUS.toPx().toDouble()

                /** Where on the plan a point of glass is, and how far a snap reaches there. */
                fun at(screen: Offset): Pair<Vec2, Double>? {
                    val current = viewport ?: fitted ?: return null
                    return current.toModel(Vec2(screen.x.toDouble(), screen.y.toDouble())) to
                        current.lengthToModel(snapRadiusPx)
                }

                planGestures(
                    drawEnabled = drawEnabled,
                    onTransform = { centroid, pan, zoom ->
                        val current = viewport ?: fitted
                        if (current != null) {
                            val panned = current.pannedBy(pan.x.toDouble(), pan.y.toDouble())
                            viewport = if (zoom == 1f) {
                                panned
                            } else {
                                panned.zoomedAbout(
                                    Vec2(centroid.x.toDouble(), centroid.y.toDouble()),
                                    zoom.toDouble(),
                                    // Keep the drawing between roughly 1% and 100x
                                    // of fit so a stray pinch cannot lose it.
                                    minScale = (fitted?.scale ?: 1.0) * 0.01,
                                    maxScale = (fitted?.scale ?: 1.0) * 100.0,
                                )
                            }
                        }
                    },
                    onTap = { tap -> at(tap)?.let { (point, tol) -> onPick(point, tol) } },
                    onDrawBegin = { down -> at(down)?.let { (p, t) -> onDrawBegin(p, t) } },
                    onDrawMove = { move -> at(move)?.let { (p, t) -> onDrawMove(p, t) } },
                    onDrawEnd = onDrawEnd,
                    onDrawCancel = onDrawCancel,
                    onGrab = { down -> at(down)?.let { (p, t) -> onGrab(p, t) } ?: false },
                    onMoveTo = { move -> at(move)?.let { (p, _) -> onMoveTo(p) } },
                    onMoveEnd = onMoveEnd,
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val v = active ?: return@Canvas

            if (gridVisible) drawGrid(v, gridSpacingMm, size)
            drawEntities(drawing.entities, layers, v)
            // What the user drew is linework like any other: same layer rules,
            // same visibility, same opacity.
            // Floors go under everything: a room is what the walls stand on.
            if (zones.isNotEmpty()) drawZones(zones, layers, v, measurer, zoneLabel)
            if (drawn.isNotEmpty()) drawEntities(drawn, layers, v)
            if (walls.isNotEmpty()) drawWalls(walls, layers, v, highlightedWallId, openings)
            if (openings.isNotEmpty()) drawOpenings(openings, layers, v, highlightedWallId)
            drawDrawingText(drawing, layers, v, measurer)
            measurements.forEach { drawMeasurement(it, v, display, measurer) }
            // Selection is drawn over the linework rather than instead of it, so
            // the shape stays legible while it is picked.
            highlighted.forEach { drawEntity(it, v, HarmenColours.Accent) }
            if (pendingPicks.isNotEmpty()) drawPending(pendingPicks, v)
            // The shape under the finger, drawn in the accent colour with its
            // length beside it: the number has to be visible while the hand is
            // still moving, or there is no way to draw to a size.
            if (preview.isNotEmpty()) {
                preview.forEach { drawEntity(it, v, HarmenColours.Accent) }
                if (previewLabel != null) {
                    drawLabel(
                        text = previewLabel,
                        at = previewAnchor(preview, v),
                        style = HarmenType.DimensionLabel.copy(color = HarmenColours.Text),
                        measurer = measurer,
                        centred = true,
                        background = HarmenColours.Canvas,
                    )
                }
            }
            if (snapAt != null) drawSnapMark(v.toScreen(snapAt))
            roomLabels.forEach { drawRoomLabel(it, v, measurer) }
        }

        StatusCorner(unitLabel, Modifier.align(Alignment.BottomStart).padding(12.dp))
        CompassCorner(Modifier.align(Alignment.BottomEnd).padding(12.dp))
    }
}

/**
 * The reference grid.
 *
 * Every tenth line is drawn in the brighter tone. The grid is skipped entirely
 * once the spacing falls under 4px, because below that it turns into a flat wash
 * that only muddies the linework.
 */
private fun DrawScope.drawGrid(v: Viewport2D, spacingMm: Double, canvas: Size) {
    val step = v.lengthToScreen(spacingMm)
    if (step < 4.0) return

    val originScreen = v.toScreen(Vec2.ZERO)

    var i = Math.floor((0.0 - originScreen.x) / step).toInt()
    var x = originScreen.x + i * step
    while (x <= canvas.width) {
        if (x >= 0) {
            drawLine(
                color = if (i % 10 == 0) HarmenColours.GridMajor else HarmenColours.Grid,
                start = Offset(x.toFloat(), 0f),
                end = Offset(x.toFloat(), canvas.height),
                strokeWidth = 1f,
            )
        }
        i++
        x = originScreen.x + i * step
    }

    var j = Math.floor((0.0 - originScreen.y) / step).toInt()
    var y = originScreen.y + j * step
    while (y <= canvas.height) {
        if (y >= 0) {
            drawLine(
                color = if (j % 10 == 0) HarmenColours.GridMajor else HarmenColours.Grid,
                start = Offset(0f, y.toFloat()),
                end = Offset(canvas.width, y.toFloat()),
                strokeWidth = 1f,
            )
        }
        j++
        y = originScreen.y + j * step
    }
}

/**
 * Draws the walls as filled bands.
 *
 * Same layer rules as any other linework: a hidden layer hides its walls, and a
 * faded layer fades them. The selected one is outlined rather than filled in the
 * accent colour, so it stays readable as a wall while it is picked.
 */
private fun DrawScope.drawWalls(
    walls: List<WallBand>,
    layers: List<LayerState>,
    v: Viewport2D,
    selectedId: String?,
    openings: List<OpeningPlan>,
) {
    val byName = layers.associateBy { it.name }

    fun pathOf(corners: List<Vec2>): Path {
        val path = Path()
        val first = v.toScreen(corners.first())
        path.moveTo(first.x.toFloat(), first.y.toFloat())
        for (k in 1 until corners.size) {
            val p = v.toScreen(corners[k])
            path.lineTo(p.x.toFloat(), p.y.toFloat())
        }
        path.close()
        return path
    }

    val drawable = walls.filter { band ->
        if (band.corners.size < 3) return@filter false
        val state = byName[band.layer]
        state == null || (state.visible && state.opacity > 0.01)
    }

    // One shape per layer, not one per wall. Two translucent bands laid over
    // each other come out brighter where they overlap, which put a pale patch
    // in every corner — the join was right and still looked wrong. Merged into
    // a single outline the corner is simply part of the wall.
    for ((layer, bands) in drawable.groupBy { it.layer }) {
        val state = byName[layer]
        val alpha = (state?.opacity ?: 1.0).toFloat()
        val colour = (state?.colour?.let { parseHex(it) } ?: HarmenColours.Linework)
            .let { it.copy(alpha = it.alpha * alpha) }

        var merged = pathOf(bands.first().corners)
        var mergedCleanly = true
        for (band in bands.drop(1)) {
            val next = Path()
            if (next.op(merged, pathOf(band.corners), PathOperation.Union)) {
                merged = next
            } else {
                // The union failed, which the platform may do on degenerate
                // geometry. Adding the shape still fills correctly; only the
                // outline is skipped, and a filled wall with no outline is far
                // better than no wall.
                merged.addPath(pathOf(band.corners))
                mergedCleanly = false
            }
        }

        // Every doorway and window takes its piece out before anything is
        // drawn, so the wall has real holes in it rather than symbols painted
        // over a solid band.
        for (opening in openings) {
            if (opening.cut.size < 3) continue
            val cut = Path()
            if (cut.op(merged, pathOf(opening.cut), PathOperation.Difference)) {
                merged = cut
            } else {
                mergedCleanly = false
            }
        }

        drawPath(merged, colour.copy(alpha = colour.alpha * 0.45f))
        // The hatch a plan gives the material. Every wall on one layer is one
        // material — the layer name is built from it — so the group answers for
        // all of them, and the pattern is clipped to the merged outline so it
        // stops at the wall's faces and inside every doorway.
        bands.firstNotNullOfOrNull { it.material }?.let { material ->
            clipPath(merged) { hatch(material, colour.copy(alpha = colour.alpha * 0.55f)) }
        }
        if (mergedCleanly) {
            drawPath(merged, colour, style = Stroke(width = 1.2f, cap = StrokeCap.Round))
        }
    }

    // The selected wall is outlined on its own, over the rest.
    if (selectedId != null) {
        drawable.firstOrNull { it.id == selectedId }?.let {
            drawPath(
                pathOf(it.corners),
                HarmenColours.Accent,
                style = Stroke(width = 1.6f, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * The pattern a material is drawn with, laid across whatever is clipped.
 *
 * Measured in pixels rather than millimetres on purpose: a hatch is a
 * convention for reading the drawing, not a thing in the building, so it stays
 * the same weight however far the plan is zoomed in. Hatching in model units
 * would give a solid block when zoomed out and three lines when zoomed in.
 */
private fun DrawScope.hatch(material: WallMaterial, colour: Color) {
    val span = size.width + size.height
    val step = 9f
    val thin = 0.8f

    /** Parallel lines across the whole clipped area, at 45 degrees one way. */
    fun diagonal(down: Boolean, spacing: Float, from: Float = 0f) {
        var at = -size.height + from
        while (at < size.width + size.height) {
            if (down) {
                drawLine(colour, Offset(at, 0f), Offset(at + size.height, size.height), thin)
            } else {
                drawLine(colour, Offset(at, size.height), Offset(at + size.height, 0f), thin)
            }
            at += spacing
        }
    }

    when (material) {
        // Brick: the one everybody reads without being told.
        WallMaterial.BRICK -> diagonal(down = true, spacing = step)

        // Concrete: hatched both ways, which is the structural convention.
        WallMaterial.CONCRETE -> {
            diagonal(down = true, spacing = step * 1.6f)
            diagonal(down = false, spacing = step * 1.6f)
        }

        // Aerated block: the same diagonal, opened out, so a block wall and a
        // brick wall are not the same picture at a glance.
        WallMaterial.AERATED -> diagonal(down = true, spacing = step * 2.2f)

        // Timber: the grain, drawn upright so it cannot be confused with either
        // diagonal.
        WallMaterial.TIMBER -> {
            var x = 0f
            while (x < span) {
                drawLine(colour, Offset(x, 0f), Offset(x, size.height), thin)
                x += step * 1.3f
            }
        }
    }
}

/**
 * Doors and windows: the frame, the glass or the leaf, and the swing.
 *
 * The hole itself was already taken out of the wall, so what is drawn here is
 * only what stands in the hole.
 */
private fun DrawScope.drawOpenings(
    openings: List<OpeningPlan>,
    layers: List<LayerState>,
    v: Viewport2D,
    selectedId: String?,
) {
    val byName = layers.associateBy { it.name }

    for (opening in openings) {
        val state = byName[opening.layer]
        if (state != null && !state.visible) continue
        val alpha = (state?.opacity ?: 1.0).toFloat()
        if (alpha <= 0.01f) continue

        val base = (state?.colour?.let { parseHex(it) } ?: HarmenColours.Linework)
            .let { it.copy(alpha = it.alpha * alpha) }
        val colour = if (opening.id == selectedId) HarmenColours.Accent else base

        for (line in opening.jambs + opening.leaf) {
            val a = v.toScreen(line.a)
            val b = v.toScreen(line.b)
            drawLine(
                colour,
                Offset(a.x.toFloat(), a.y.toFloat()),
                Offset(b.x.toFloat(), b.y.toFloat()),
                strokeWidth = 1.2f,
            )
        }

        opening.swing?.let { arc ->
            // Thinner and quieter than the leaf: the swing is where the door
            // will be, not where it is.
            val points = tessellateArc(
                arc.centre,
                arc.radiusMm,
                Math.toRadians(arc.startDegrees),
                Math.toRadians(arc.endDegrees),
                segments = 24,
            )
            val path = Path()
            val first = v.toScreen(points.first())
            path.moveTo(first.x.toFloat(), first.y.toFloat())
            for (k in 1 until points.size) {
                val p = v.toScreen(points[k])
                path.lineTo(p.x.toFloat(), p.y.toFloat())
            }
            drawPath(path, colour.copy(alpha = colour.alpha * 0.6f), style = Stroke(width = 1f))
        }
    }
}

/**
 * Rooms: the floor, and its name and area written in the middle.
 *
 * A room whose walls no longer close is not drawn as a shape at all — there is
 * no shape — but its name is still written where the user put it, so nothing
 * they typed disappears while they are still building the walls.
 */
private fun DrawScope.drawZones(
    zones: List<ZonePlan>,
    layers: List<LayerState>,
    v: Viewport2D,
    measurer: TextMeasurer,
    label: (ZonePlan) -> List<String>,
) {
    val byName = layers.associateBy { it.name }

    for (zone in zones) {
        val state = byName[zone.layer]
        if (state != null && !state.visible) continue
        val alpha = (state?.opacity ?: 1.0).toFloat()
        if (alpha <= 0.01f) continue

        if (zone.outline.size >= 3) {
            val path = Path()
            val first = v.toScreen(zone.outline.first())
            path.moveTo(first.x.toFloat(), first.y.toFloat())
            for (k in 1 until zone.outline.size) {
                val p = v.toScreen(zone.outline[k])
                path.lineTo(p.x.toFloat(), p.y.toFloat())
            }
            path.close()
            // Barely there: a floor is a wash the linework sits on, not a
            // colour that competes with it.
            drawPath(path, HarmenColours.Accent.copy(alpha = 0.07f * alpha))
        }

        val lines = label(zone)
        if (lines.isEmpty()) continue

        val at = v.toScreen(zone.anchor)
        var y = at.y - (lines.size - 1) * 9.0
        for ((index, text) in lines.withIndex()) {
            drawLabel(
                text = text,
                at = Vec2(at.x, y),
                style = HarmenType.RoomLabel.copy(
                    color = if (index == 0) {
                        HarmenColours.Text.copy(alpha = 0.75f * alpha)
                    } else {
                        HarmenColours.TextMuted.copy(alpha = alpha)
                    },
                ),
                measurer = measurer,
                centred = true,
                background = null,
            )
            y += 18.0
        }
    }
}

/**
 * The mark under a caught point.
 *
 * A small square is the drawing-office sign for "this is an end point", and it
 * is the only way the user can tell, while the finger is still down, that the
 * wall has taken hold of the corner instead of landing beside it.
 */
private fun DrawScope.drawSnapMark(at: Vec2) {
    val r = 9f
    val c = Offset(at.x.toFloat(), at.y.toFloat())
    drawRect(
        color = HarmenColours.Accent,
        topLeft = Offset(c.x - r, c.y - r),
        size = Size(r * 2, r * 2),
        style = Stroke(width = 1.6f),
    )
}

/** Draws every entity on a visible layer, honouring the layer's opacity. */
private fun DrawScope.drawEntities(
    entities: List<DxfEntity>,
    layers: List<LayerState>,
    v: Viewport2D,
) {
    val byName = layers.associateBy { it.name }

    for (entity in entities) {
        val state = byName[entity.layer]
        if (state != null && !state.visible) continue
        val alpha = (state?.opacity ?: 1.0).toFloat()
        if (alpha <= 0.01f) continue

        val colour = state?.colour?.let { parseHex(it) } ?: HarmenColours.Linework
        drawEntity(entity, v, colour.copy(alpha = colour.alpha * alpha))
    }
}

private fun DrawScope.drawEntity(entity: DxfEntity, v: Viewport2D, colour: Color) {
    // Arc segment count follows the on-screen radius: a small arc needs few
    // segments, a zoomed-in one needs many to stay smooth.
    val segments = when (entity) {
        is DxfEntity.Arc -> arcSegments(v.lengthToScreen(entity.radius))
        is DxfEntity.Circle -> arcSegments(v.lengthToScreen(entity.radius))
        else -> 32
    }

    when (entity) {
        is DxfEntity.Point -> {
            val p = v.toScreen(entity.position.toVec2())
            drawCircle(colour, radius = 1.5f, center = Offset(p.x.toFloat(), p.y.toFloat()))
        }

        // Text is handled in its own pass: it needs a TextMeasurer, which the
        // geometry pass does not carry.
        is DxfEntity.Text -> Unit

        is DxfEntity.Insert -> {
            // A block reference with no expanded geometry is marked with a tick
            // so the user can see that something is there.
            val p = v.toScreen(entity.position.toVec2())
            val r = 3f
            drawLine(
                colour,
                Offset(p.x.toFloat() - r, p.y.toFloat()),
                Offset(p.x.toFloat() + r, p.y.toFloat()),
                strokeWidth = 1f,
            )
            drawLine(
                colour,
                Offset(p.x.toFloat(), p.y.toFloat() - r),
                Offset(p.x.toFloat(), p.y.toFloat() + r),
                strokeWidth = 1f,
            )
        }

        else -> {
            val points = entity.outline(segments)
            if (points.size < 2) return
            val path = Path()
            val first = v.toScreen(points.first())
            path.moveTo(first.x.toFloat(), first.y.toFloat())
            for (k in 1 until points.size) {
                val p = v.toScreen(points[k])
                path.lineTo(p.x.toFloat(), p.y.toFloat())
            }
            drawPath(path, colour, style = Stroke(width = 1.2f, cap = StrokeCap.Round))
        }
    }
}

/**
 * Draws the drawing's own TEXT and MTEXT entities.
 *
 * The size comes from the entity's model height, so labels scale with the
 * drawing exactly as they do in a CAD viewport. Text that would render below
 * 6px is skipped: at that size it is illegible noise over the linework, and
 * measuring it for every entity costs more than it shows.
 */
private fun DrawScope.drawDrawingText(
    drawing: DxfDrawing,
    layers: List<LayerState>,
    v: Viewport2D,
    measurer: TextMeasurer,
) {
    val byName = layers.associateBy { it.name }

    for (entity in drawing.entities) {
        if (entity !is DxfEntity.Text) continue
        val state = byName[entity.layer]
        if (state != null && !state.visible) continue

        val alpha = (state?.opacity ?: 1.0).toFloat()
        if (alpha <= 0.01f) continue

        val heightPx = v.lengthToScreen(entity.height)
        if (heightPx < 6.0) continue

        val colour = (state?.colour?.let { parseHex(it) } ?: HarmenColours.Linework)
            .let { it.copy(alpha = it.alpha * alpha) }

        val style = HarmenType.RoomLabel.copy(
            color = colour,
            fontSize = heightPx.toFloat().toSp(),
            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
        )
        val layout = measurer.measure(entity.value, style)
        val at = v.toScreen(entity.position.toVec2())

        // DXF anchors text by its baseline; Compose draws from the top-left.
        val x = when (entity.align) {
            DxfTextAlign.LEFT -> at.x.toFloat()
            DxfTextAlign.CENTRE -> at.x.toFloat() - layout.size.width / 2f
            DxfTextAlign.RIGHT -> at.x.toFloat() - layout.size.width
        }
        drawText(layout, topLeft = Offset(x, at.y.toFloat() - layout.size.height))
    }
}

private fun arcSegments(screenRadius: Double): Int =
    (screenRadius / 3.0).toInt().coerceIn(8, 192)

/**
 * Draws a measurement in the accent colour: extension lines, a dimension line
 * with arrow heads, and the value.
 */
private fun DrawScope.drawMeasurement(
    m: Measurement,
    v: Viewport2D,
    display: MeasurementDisplay,
    measurer: TextMeasurer,
) {
    // Bronze draws the dimension line and its arrow heads; the value itself is
    // set in ivory on a knocked-out ground. The guideline is explicit that
    // bronze below 24px is a line colour, not a text colour — and a dimension
    // string that cannot be read at a glance defeats the measurement.
    val accent = HarmenColours.Accent
    val labelStyle = HarmenType.DimensionLabel.copy(color = HarmenColours.Text)
    when (m) {
        is Measurement.Distance -> {
            val a = v.toScreen(m.from.toVec2())
            val b = v.toScreen(m.to.toVec2())
            drawDimensionLine(a, b, accent)
            drawLabel(
                text = m.label(display),
                at = Vec2((a.x + b.x) / 2, (a.y + b.y) / 2),
                style = labelStyle,
                measurer = measurer,
                centred = true,
                background = HarmenColours.Canvas,
            )
        }

        is Measurement.Polyline -> {
            val pts = m.points.map { v.toScreen(it.toVec2()) }
            for (i in 0 until pts.size - 1) {
                drawLine(
                    accent,
                    Offset(pts[i].x.toFloat(), pts[i].y.toFloat()),
                    Offset(pts[i + 1].x.toFloat(), pts[i + 1].y.toFloat()),
                    strokeWidth = 1.2f,
                )
            }
            drawLabel(
                text = m.label(display),
                at = pts[pts.size / 2],
                style = labelStyle,
                measurer = measurer,
                centred = true,
                background = HarmenColours.Canvas,
            )
        }

        is Measurement.Angle -> {
            val vertex = v.toScreen(m.vertex.toVec2())
            listOf(m.from, m.to).forEach { end ->
                val e = v.toScreen(end.toVec2())
                drawLine(
                    accent,
                    Offset(vertex.x.toFloat(), vertex.y.toFloat()),
                    Offset(e.x.toFloat(), e.y.toFloat()),
                    strokeWidth = 1.2f,
                )
            }
            drawLabel(
                text = m.label(display),
                at = Vec2(vertex.x + 14, vertex.y - 14),
                style = labelStyle,
                measurer = measurer,
                centred = true,
                background = HarmenColours.Canvas,
            )
        }

        is Measurement.Area -> {
            val pts = m.points.map { v.toScreen(it.toVec2()) }
            val path = Path().apply {
                moveTo(pts[0].x.toFloat(), pts[0].y.toFloat())
                pts.drop(1).forEach { lineTo(it.x.toFloat(), it.y.toFloat()) }
                close()
            }
            drawPath(path, accent.copy(alpha = 0.10f))
            drawPath(path, accent, style = Stroke(width = 1.2f))
            drawLabel(
                text = m.label(display),
                at = v.toScreen(m.anchor.toVec2()),
                style = labelStyle,
                measurer = measurer,
                centred = true,
                background = HarmenColours.Canvas,
            )
        }
    }
}

/**
 * How far a snap may reach from the finger.
 *
 * In dp, not pixels — and that distinction is the whole point. It was written
 * in pixels at first, and on a tablet screen 28 pixels is about a millimetre
 * and a half of glass: corners almost never caught, so walls would not meet
 * end to end. In dp it is the same 10mm of glass on every screen, roughly a
 * fingertip, which is what a snap radius has to be to be usable at all.
 */
private val SNAP_RADIUS = 28.dp

/**
 * The measurement in progress: the points taken so far, and the line they
 * imply.
 *
 * Drawn differently from a finished measurement — hollow marks and a thin
 * line — so that "still picking" never looks like a recorded dimension.
 */
private fun DrawScope.drawPending(points: List<Vec2>, v: Viewport2D) {
    val accent = HarmenColours.Accent
    val screen = points.map { v.toScreen(it) }

    for (i in 0 until screen.size - 1) {
        drawLine(
            accent,
            Offset(screen[i].x.toFloat(), screen[i].y.toFloat()),
            Offset(screen[i + 1].x.toFloat(), screen[i + 1].y.toFloat()),
            strokeWidth = 1f,
        )
    }

    for (p in screen) {
        val c = Offset(p.x.toFloat(), p.y.toFloat())
        drawCircle(accent, radius = 4f, center = c, style = Stroke(width = 1.2f))
        // A cross through the ring: on a busy drawing a small ring alone
        // disappears into the linework.
        drawLine(accent, Offset(c.x - 7f, c.y), Offset(c.x + 7f, c.y), strokeWidth = 1f)
        drawLine(accent, Offset(c.x, c.y - 7f), Offset(c.x, c.y + 7f), strokeWidth = 1f)
    }
}

/** A dimension line with a tick at each end and a gap for the label. */
private fun DrawScope.drawDimensionLine(a: Vec2, b: Vec2, colour: Color) {
    drawLine(
        colour,
        Offset(a.x.toFloat(), a.y.toFloat()),
        Offset(b.x.toFloat(), b.y.toFloat()),
        strokeWidth = 1.2f,
    )
    val angle = atan2(b.y - a.y, b.x - a.x)
    drawArrowHead(a, angle, colour)
    drawArrowHead(b, angle + Math.PI, colour)
}

private fun DrawScope.drawArrowHead(tip: Vec2, angle: Double, colour: Color) {
    val length = 7.0
    val spread = 0.38
    for (sign in listOf(-1.0, 1.0)) {
        val t = angle + sign * spread
        drawLine(
            colour,
            Offset(tip.x.toFloat(), tip.y.toFloat()),
            Offset(
                (tip.x + cos(t) * length).toFloat(),
                (tip.y + sin(t) * length).toFloat(),
            ),
            strokeWidth = 1.1f,
        )
    }
}

/** Room names: thin, tracked, and deliberately quiet against the linework. */
private fun DrawScope.drawRoomLabel(label: RoomLabel, v: Viewport2D, measurer: TextMeasurer) {
    drawLabel(
        text = label.name.uppercase(),
        at = v.toScreen(label.position),
        style = HarmenType.RoomLabel.copy(color = HarmenColours.Text.copy(alpha = 0.45f)),
        measurer = measurer,
        centred = true,
        background = null,
    )
}

/**
 * Draws text at a screen position.
 *
 * A [background] knocks a panel out behind the text so a dimension value stays
 * readable where it crosses linework.
 */
private fun DrawScope.drawLabel(
    text: String,
    at: Vec2,
    style: TextStyle,
    measurer: TextMeasurer,
    centred: Boolean,
    background: Color?,
) {
    if (text.isEmpty()) return
    val layout = measurer.measure(text, style)
    val w = layout.size.width.toFloat()
    val h = layout.size.height.toFloat()
    val x = if (centred) at.x.toFloat() - w / 2 else at.x.toFloat()
    val y = if (centred) at.y.toFloat() - h / 2 else at.y.toFloat()

    if (background != null) {
        val pad = 3f
        drawRect(
            color = background,
            topLeft = Offset(x - pad, y - pad),
            size = Size(w + pad * 2, h + pad * 2),
        )
    }
    drawText(layout, topLeft = Offset(x, y))
}

private fun parseHex(hex: String): Color {
    val t = hex.trim().removePrefix("#")
    val parsed = when (t.length) {
        6 -> t.toLongOrNull(16)?.let { it or 0xFF000000L }
        8 -> t.toLongOrNull(16)
        else -> null
    }
    return parsed?.let { Color(it) } ?: HarmenColours.Linework
}

/** `Unit 101 – Lvl 2` in the bottom-left corner. */
@Composable
private fun StatusCorner(unitLabel: String, modifier: Modifier = Modifier) {
    Text(
        text = unitLabel,
        style = HarmenType.Status,
        color = HarmenColours.TextMuted,
        modifier = modifier,
    )
}

/** The orientation marker in the bottom-right corner. */
@Composable
private fun CompassCorner(modifier: Modifier = Modifier) {
    Canvas(modifier.size(28.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 1f
        drawCircle(HarmenColours.Hairline, radius = r, center = c, style = Stroke(width = 1f))
        // The north needle, in the accent colour.
        drawLine(
            HarmenColours.Accent,
            start = Offset(c.x, c.y + r * 0.55f),
            end = Offset(c.x, c.y - r * 0.75f),
            strokeWidth = 1.4f,
        )
        drawLine(
            HarmenColours.Accent,
            start = Offset(c.x - r * 0.22f, c.y - r * 0.35f),
            end = Offset(c.x, c.y - r * 0.75f),
            strokeWidth = 1.4f,
        )
        drawLine(
            HarmenColours.Accent,
            start = Offset(c.x + r * 0.22f, c.y - r * 0.35f),
            end = Offset(c.x, c.y - r * 0.75f),
            strokeWidth = 1.4f,
        )
    }
}

/**
 * Every touch on the plan, in one place.
 *
 * One handler rather than several, because the fingers have to be shared: with
 * a drawing tool active a single finger draws, so it cannot also move the view,
 * and a second finger has to be able to take a half-drawn shape back. Separate
 * detectors each see their own gesture and would fight over the same finger.
 *
 * The rules it implements:
 *  - one finger, drawing tool active  → draw, following the finger
 *  - one finger on a shape            → drag the shape
 *  - one finger on bare paper         → move the view
 *  - two fingers, always              → move and zoom the view
 *  - a press that never moves         → a tap, wherever it happened
 */
private suspend fun PointerInputScope.planGestures(
    drawEnabled: Boolean,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onTap: (Offset) -> Unit,
    onDrawBegin: (Offset) -> Unit,
    onDrawMove: (Offset) -> Unit,
    onDrawEnd: () -> Unit,
    onDrawCancel: () -> Unit,
    onGrab: (Offset) -> Boolean,
    onMoveTo: (Offset) -> Unit,
    onMoveEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var travelled = 0f
        var drawing = false
        var moving = false
        var pinched = false
        var last = down.position
        var previous: Map<PointerId, Offset> = mapOf(down.id to down.position)

        if (drawEnabled) {
            drawing = true
            onDrawBegin(down.position)
        } else {
            // Nothing is moved yet — this only asks what is under the finger,
            // and selects it. The move itself starts when the finger does.
            moving = onGrab(down.position)
        }

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break

            if (pressed.size > 1 && (drawing || moving)) {
                // A second finger means "move the view", not "draw" and not
                // "drag this": the half-drawn shape is taken back rather than
                // left stretched across the screen by the pinch.
                if (drawing) onDrawCancel()
                if (moving) onMoveEnd()
                drawing = false
                moving = false
                pinched = true
            }

            val current = pressed.associate { it.id to it.position }
            val shared = current.keys.filter { previous.containsKey(it) }

            if (shared.isNotEmpty()) {
                val before = centroidOf(shared.map { previous.getValue(it) })
                val after = centroidOf(shared.map { current.getValue(it) })
                travelled += (after - before).getDistance()

                if (drawing) {
                    last = after
                    onDrawMove(after)
                    pressed.forEach { it.consume() }
                } else if (moving) {
                    last = after
                    // Only once the finger has really set off: without the
                    // slop a tap that wobbles by a pixel nudges the wall.
                    if ((after - down.position).getDistance() > slop) onMoveTo(after)
                    pressed.forEach { it.consume() }
                } else if (travelled > slop) {
                    // Zoom is how far apart the fingers are now against how far
                    // apart they were a frame ago; with one finger it is 1.
                    val zoom = if (shared.size > 1) {
                        val spreadBefore = spreadOf(shared.map { previous.getValue(it) }, before)
                        val spreadAfter = spreadOf(shared.map { current.getValue(it) }, after)
                        if (spreadBefore > 0.01f) spreadAfter / spreadBefore else 1f
                    } else {
                        1f
                    }
                    onTransform(after, after - before, zoom)
                    pressed.forEach { it.consume() }
                }
            }
            previous = current
        }

        if (drawing) {
            // Straight-line distance, not the length of the path: a finger that
            // wandered and came back to where it started drew nothing, and must
            // not leave a shape of no size behind.
            if ((last - down.position).getDistance() > slop) {
                onDrawEnd()
            } else {
                // A press that went nowhere is a tap, not a drag of no length.
                // Two taps still place a shape, for anyone who would rather
                // pick two points than hold a steady drag.
                onDrawCancel()
                onTap(down.position)
            }
        } else if (moving) {
            onMoveEnd()
            // A press on a shape that never moved is still a tap: it should
            // reach the tool, so measuring and placing keep working on top of
            // something already drawn.
            if ((last - down.position).getDistance() <= slop) onTap(down.position)
        } else if (!pinched && travelled <= slop) {
            onTap(down.position)
        }
    }
}

private fun centroidOf(points: List<Offset>): Offset {
    var x = 0f
    var y = 0f
    for (p in points) {
        x += p.x
        y += p.y
    }
    return Offset(x / points.size, y / points.size)
}

/** Mean distance of the fingers from their centre. */
private fun spreadOf(points: List<Offset>, centre: Offset): Float {
    var total = 0f
    for (p in points) total += hypot(p.x - centre.x, p.y - centre.y)
    return total / points.size
}

/**
 * Where the length of the shape being drawn is written.
 *
 * The middle of everything drawn so far, lifted clear of the linework so the
 * number does not sit on top of the wall it is measuring.
 */
private fun previewAnchor(preview: List<DxfEntity>, v: Viewport2D): Vec2 {
    val points = preview.flatMap { it.outline(16) }
    if (points.isEmpty()) return Vec2.ZERO
    val centre = Vec2(points.sumOf { it.x } / points.size, points.sumOf { it.y } / points.size)
    val screen = v.toScreen(centre)
    return Vec2(screen.x, screen.y - 22.0)
}
