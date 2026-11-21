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


/**
 * How big this entity is, in drawing units: the longer side of the box round
 * it.
 *
 * For deciding how much detail it is worth. Zoomed out to the whole building, a
 * door's arc is two pixels across and tessellating it into sixty-four points
 * draws the same two pixels at sixty-four times the cost. Like [mayBeVisible]
 * this is deliberately cheap: no tessellation, no allocation.
 */
public fun DxfEntity.modelSpan(): Double = when (this) {
    is DxfEntity.Line -> maxOf(abs(end.x - start.x), abs(end.y - start.y))
    is DxfEntity.Point -> 0.0
    is DxfEntity.Insert -> 0.0
    is DxfEntity.Circle -> abs(radius) * 2.0
    is DxfEntity.Arc -> abs(radius) * 2.0
    is DxfEntity.Text -> abs(height)
    is DxfEntity.Polyline -> spanOf(vertices.size, { vertices[it].x }, { vertices[it].y })
    is DxfEntity.Face -> spanOf(points.size, { points[it].x }, { points[it].y })
}

private inline fun spanOf(count: Int, x: (Int) -> Double, y: (Int) -> Double): Double {
    if (count == 0) return 0.0
    var minX = Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    var maxY = -Double.MAX_VALUE
    for (i in 0 until count) {
        val px = x(i)
        val py = y(i)
        if (px < minX) minX = px
        if (px > maxX) maxX = px
        if (py < minY) minY = py
        if (py > maxY) maxY = py
    }
    return maxOf(maxX - minX, maxY - minY)
}
