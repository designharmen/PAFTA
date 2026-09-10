package com.harmen.pafta.geometry

import kotlin.math.max
import kotlin.math.min

/**
 * The 2D drawing camera: a uniform scale plus a translation.
 *
 * Model space is Y-up (as DXF is) and screen space is Y-down, so the transform
 * flips Y. Keeping this as plain Kotlin rather than a Compose matrix means the
 * behaviour that actually bites — fit, zoom about a pivot, round-tripping a
 * pick back to model coordinates — is unit-tested rather than eyeballed.
 *
 * @param scale screen pixels per model unit; always > 0
 * @param panX screen-space horizontal offset, in pixels
 * @param panY screen-space vertical offset, in pixels
 */
public data class Viewport2D(
    val scale: Double = 1.0,
    val panX: Double = 0.0,
    val panY: Double = 0.0,
) {
    init {
        require(scale > 0.0 && scale.isFinite()) { "scale must be finite and > 0, was $scale" }
    }

    /** Model point -> screen pixel. */
    public fun toScreen(p: Vec2): Vec2 = Vec2(p.x * scale + panX, -p.y * scale + panY)

    /** Screen pixel -> model point. */
    public fun toModel(p: Vec2): Vec2 = Vec2((p.x - panX) / scale, -(p.y - panY) / scale)

    /** Converts a model length to its on-screen length in pixels. */
    public fun lengthToScreen(modelLength: Double): Double = modelLength * scale

    /** Converts a screen length in pixels back to model units. */
    public fun lengthToModel(screenLength: Double): Double = screenLength / scale

    /** Drags the view by a screen-space delta. */
    public fun pannedBy(dxPixels: Double, dyPixels: Double): Viewport2D =
        copy(panX = panX + dxPixels, panY = panY + dyPixels)

    /**
     * Zooms by [factor] while holding the model point under [pivotScreen] fixed,
     * which is what a pinch gesture has to do to feel attached to the drawing.
     *
     * The resulting scale is clamped to [minScale]..[maxScale]; zooming past a
     * limit leaves the view unchanged rather than drifting.
     */
    public fun zoomedAbout(
        pivotScreen: Vec2,
        factor: Double,
        minScale: Double = 1e-6,
        maxScale: Double = 1e6,
    ): Viewport2D {
        if (!factor.isFinite() || factor <= 0.0) return this
        val target = (scale * factor).coerceIn(minScale, maxScale)
        if (target == scale) return this
        // Hold the pivot: solve pivot = model * target + pan' for pan'.
        val model = toModel(pivotScreen)
        return Viewport2D(
            scale = target,
            panX = pivotScreen.x - model.x * target,
            panY = pivotScreen.y + model.y * target,
        )
    }

    public companion object {
        /**
         * Builds a viewport that fits [bounds] inside a [viewWidth] x
         * [viewHeight] surface with a [paddingPixels] margin.
         *
         * An empty or degenerate box yields a viewport centred on the box at
         * [fallbackScale], because a zero-size drawing still has to be openable.
         */
        public fun fit(
            bounds: Aabb,
            viewWidth: Double,
            viewHeight: Double,
            paddingPixels: Double = 48.0,
            fallbackScale: Double = 1.0,
        ): Viewport2D {
            if (viewWidth <= 0.0 || viewHeight <= 0.0) return Viewport2D(fallbackScale)

            val usableWidth = max(1.0, viewWidth - 2 * paddingPixels)
            val usableHeight = max(1.0, viewHeight - 2 * paddingPixels)

            val centre: Vec2
            val scale: Double
            if (bounds.isEmpty) {
                centre = Vec2.ZERO
                scale = fallbackScale
            } else {
                val size = bounds.size
                centre = Vec2(bounds.center.x, bounds.center.y)
                scale = when {
                    size.x <= EPS && size.y <= EPS -> fallbackScale
                    size.x <= EPS -> usableHeight / size.y
                    size.y <= EPS -> usableWidth / size.x
                    else -> min(usableWidth / size.x, usableHeight / size.y)
                }
            }

            // Place the drawing's centre at the centre of the surface.
            return Viewport2D(
                scale = scale,
                panX = viewWidth / 2 - centre.x * scale,
                panY = viewHeight / 2 + centre.y * scale,
            )
        }
    }
}
