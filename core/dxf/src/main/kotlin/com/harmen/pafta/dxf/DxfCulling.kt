package com.harmen.pafta.dxf

import com.harmen.pafta.geometry.Aabb
import kotlin.math.abs

/**
 * Whether an entity can be seen at all.
 *
 * The drawing loop runs over every entity sixty times a second, and on a real
 * plan nearly all of them are off the screen: a plan zoomed in on one room
 * still carries the whole building. Asking this first is what makes a large
 * drawing usable, so it has to be cheap — which means no tessellation (an arc
 * is its centre and radius, not sixty-four points) and no allocation at all.
 *
 * It answers conservatively: a true answer means "might be visible", so a
 * diagonal line whose corner box overlaps the window is drawn even if the line
 * itself misses it. Drawing one line too many costs nothing; dropping one that
 * should be there is a missing wall.
 */
public fun DxfEntity.mayBeVisible(window: Aabb): Boolean {
    if (window.isEmpty) return true
    val left = window.min.x
    val bottom = window.min.y
    val right = window.max.x
    val top = window.max.y

    return when (this) {
        is DxfEntity.Line -> overlaps(
            minOf(start.x, end.x), minOf(start.y, end.y),
            maxOf(start.x, end.x), maxOf(start.y, end.y),
            left, bottom, right, top,
        )

        is DxfEntity.Point -> position.x in left..right && position.y in bottom..top

        is DxfEntity.Circle, is DxfEntity.Arc -> {
            val centre = if (this is DxfEntity.Circle) this.centre else (this as DxfEntity.Arc).centre
            val r = abs(if (this is DxfEntity.Circle) this.radius else (this as DxfEntity.Arc).radius)
            overlaps(
                centre.x - r, centre.y - r, centre.x + r, centre.y + r,
                left, bottom, right, top,
            )
        }

        is DxfEntity.Polyline -> {
            if (vertices.isEmpty()) return false
            var minX = Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE
            for (p in vertices) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }
            overlaps(minX, minY, maxX, maxY, left, bottom, right, top)
        }

        is DxfEntity.Face -> {
            if (points.isEmpty()) return false
            var minX = Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE
            for (p in points) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }
            overlaps(minX, minY, maxX, maxY, left, bottom, right, top)
        }

        // A piece of text is drawn from its insertion point and reaches up and
        // to the right of it; how far to the right depends on the font, so the
        // height is used for both, generously.
        is DxfEntity.Text -> {
            val reach = abs(height) * TEXT_REACH
            overlaps(
                position.x - abs(height), position.y - abs(height),
                position.x + reach, position.y + abs(height) * 2.0,
                left, bottom, right, top,
            )
        }

        is DxfEntity.Insert -> position.x in left..right && position.y in bottom..top
    }
}

private fun overlaps(
    aMinX: Double,
    aMinY: Double,
    aMaxX: Double,
    aMaxY: Double,
    bMinX: Double,
    bMinY: Double,
    bMaxX: Double,
    bMaxY: Double,
): Boolean = aMaxX >= bMinX && aMinX <= bMaxX && aMaxY >= bMinY && aMinY <= bMaxY

/** How many times its own height a line of text may run on for. */
private const val TEXT_REACH = 80.0
