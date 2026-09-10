package com.harmen.pafta.ui.viewport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Viewport2D
import com.harmen.pafta.measure.Measurement
import com.harmen.pafta.measure.MeasurementDisplay
import com.harmen.pafta.measure.label
import com.harmen.pafta.project.LayerState
import com.harmen.pafta.ui.theme.HarmenColours
import com.harmen.pafta.ui.theme.HarmenType
import kotlin.math.atan2
import kotlin.math.cos
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
    onPick: (Vec2) -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    var surface by remember { mutableStateOf(IntSize.Zero) }
    var viewport by remember { mutableStateOf<Viewport2D?>(null) }

    // Fit once the surface is measured, and re-fit if the drawing is replaced.
    val fitted = remember(drawing, surface) {
        if (surface.width == 0 || surface.height == 0) {
            null
        } else {
            Viewport2D.fit(
                drawing.bounds,
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
            .onSizeChanged { newSize ->
                if (newSize != surface) {
                    surface = newSize
                    // A resize (rotation, split screen) re-fits rather than
                    // leaving the drawing half off the edge.
                    viewport = null
                }
            }
            .pointerInput(drawing) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val current = viewport ?: fitted ?: return@detectTransformGestures
                    val panned = current.pannedBy(pan.x.toDouble(), pan.y.toDouble())
                    viewport = if (zoom == 1f) {
                        panned
                    } else {
                        panned.zoomedAbout(
                            Vec2(centroid.x.toDouble(), centroid.y.toDouble()),
                            zoom.toDouble(),
                            // Keep the drawing between roughly 1% and 100x of fit
                            // so a stray pinch cannot lose it entirely.
                            minScale = (fitted?.scale ?: 1.0) * 0.01,
                            maxScale = (fitted?.scale ?: 1.0) * 100.0,
                        )
                    }
                }
            }
            .pointerInput(drawing) {
                detectTapGestures { tap ->
                    val current = viewport ?: fitted ?: return@detectTapGestures
                    onPick(current.toModel(Vec2(tap.x.toDouble(), tap.y.toDouble())))
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val v = active ?: return@Canvas

            if (gridVisible) drawGrid(v, gridSpacingMm, size)
            drawDrawing(drawing, layers, v)
            measurements.forEach { drawMeasurement(it, v, display, measurer) }
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

/** Draws every entity on a visible layer, honouring the layer's opacity. */
private fun DrawScope.drawDrawing(drawing: DxfDrawing, layers: List<LayerState>, v: Viewport2D) {
    val byName = layers.associateBy { it.name }

    for (entity in drawing.entities) {
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

        is DxfEntity.Text -> Unit // Text is drawn by the annotation pass.

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
    val accent = HarmenColours.Accent
    when (m) {
        is Measurement.Distance -> {
            val a = v.toScreen(m.from.toVec2())
            val b = v.toScreen(m.to.toVec2())
            drawDimensionLine(a, b, accent)
            drawLabel(
                text = m.label(display),
                at = Vec2((a.x + b.x) / 2, (a.y + b.y) / 2),
                style = HarmenType.DimensionLabel.copy(color = accent),
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
                style = HarmenType.DimensionLabel.copy(color = accent),
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
                style = HarmenType.DimensionLabel.copy(color = accent),
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
                style = HarmenType.DimensionLabel.copy(color = accent),
                measurer = measurer,
                centred = true,
                background = HarmenColours.Canvas,
            )
        }
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
