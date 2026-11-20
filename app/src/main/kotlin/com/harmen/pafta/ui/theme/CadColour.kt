package com.harmen.pafta.ui.theme

import androidx.compose.ui.graphics.Color
import com.harmen.pafta.dxf.AciColours

/**
 * What a colour out of a CAD file looks like on PAFTA's canvas.
 *
 * A drawing stores colours as numbers, and on a plan those numbers are
 * information: which layer, which trade, which weight the plotter will use. So
 * they are honoured rather than replaced — with two corrections that the file
 * itself asks for.
 */
public fun cadColour(entityColour: Int, layerColour: Int): Color {
    val rgb = AciColours.resolve(entityColour, layerColour) ?: return HarmenColours.Linework
    // Colour 7 means "the opposite of the background", which on a dark canvas
    // is white. PAFTA's off-white is that same answer in the house palette, so
    // the default layer keeps looking like PAFTA instead of going stark white.
    if (rgb == PLAIN_WHITE) return HarmenColours.Linework
    return Color(rgb or OPAQUE).liftedIntoView()
}

/**
 * The same colour, lifted until it can be seen.
 *
 * The colour table holds colours meant for white paper as well as for a screen,
 * and a line drawn in near-black would be invisible on PAFTA's dark canvas —
 * which looks exactly like a layer that failed to draw. The hue is kept; only
 * the brightness is raised, and only when it has to be.
 */
internal fun Color.liftedIntoView(): Color {
    val brightest = maxOf(red, green, blue)
    if (brightest >= MINIMUM_BRIGHTNESS) return this
    if (brightest <= 0.001f) return HarmenColours.Linework
    val lift = MINIMUM_BRIGHTNESS / brightest
    return Color(
        red = (red * lift).coerceAtMost(1f),
        green = (green * lift).coerceAtMost(1f),
        blue = (blue * lift).coerceAtMost(1f),
        alpha = alpha,
    )
}

private const val PLAIN_WHITE = 0xFFFFFF

private val OPAQUE = 0xFF000000L.toInt()

/** Below this, a line on PAFTA's canvas reads as absent rather than as dark. */
private const val MINIMUM_BRIGHTNESS = 0.42f
