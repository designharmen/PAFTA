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

    /**
     * The same transform, one coordinate at a time.
     *
     * [toScreen] is the readable form and is right nearly everywhere. These two
     * exist for the drawing loop, which runs over every point of every visible
     * entity sixty times a second: there, returning a point means allocating
     * one, and a plan with a hundred thousand lines in it allocates millions of
     * them per second — which is most of what makes a large drawing stutter.
     */
    public fun screenX(modelX: Double): Double = modelX * scale + panX

    public fun screenY(modelY: Double): Double = -modelY * scale + panY

    /**
     * The part of the drawing a screen of this size is showing, in model
     * coordinates.
     *
     * What it is for: an entity outside this rectangle cannot be seen, so it
     * does not have to be transformed, tessellated or drawn. On a plan zoomed
     * in on one room that is the difference between touching a hundred
     * thousand entities per frame and touching a few hundred.
     */
    public fun visibleBounds(widthPx: Double, heightPx: Double): Aabb {
        val left = (0.0 - panX) / scale
        val right = (widthPx - panX) / scale
        // Screen y grows downwards and model y grows upwards, so the top of the
        // screen is the larger model y.
        val top = -(0.0 - panY) / scale
        val bottom = -(heightPx - panY) / scale
        return Aabb(
            Vec3(minOf(left, right), minOf(top, bottom), 0.0),
            Vec3(maxOf(left, right), maxOf(top, bottom), 0.0),
        )
    }

    /** Converts a screen length in pixels back to model units. */
    public fun lengthToModel(screenLength: Double): Double = screenLength / scale

    /** Drags the view by a screen-space delta. */
    public fun pannedBy(dxPixels: Double, dyPixels: Double): Viewport2D =
        copy(panX = panX + dxPixels, panY = panY + dyPixels)

    /**
     * The same view after the surface it is drawn on changes size.
     *
     * Whatever was in the middle stays in the middle, at the same scale. This
     * is the difference between a window that gets bigger and a drawing that
     * jumps: re-fitting on every resize threw away wherever the user had
     * scrolled to, and on a tablet the surface resizes constantly — a settings
     * strip appears under the tools, a panel slides out beside the sheet — so
     * the plan kept zooming itself back out while somebody was working in it.
     */
    public fun resized(
        fromWidth: Double,
        fromHeight: Double,
        toWidth: Double,
        toHeight: Double,
    ): Viewport2D = pannedBy((toWidth - fromWidth) / 2.0, (toHeight - fromHeight) / 2.0)

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
