package com.harmen.pafta.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** An axis-aligned bounding box in model space. Invalid/empty when [isEmpty]. */
public data class Aabb(val min: Vec3, val max: Vec3) {

    public val isEmpty: Boolean get() = min.x > max.x || min.y > max.y || min.z > max.z

    public val center: Vec3 get() = (min + max) * 0.5
    public val size: Vec3 get() = max - min
    public val diagonal: Double get() = size.length

    public fun encompass(p: Vec3): Aabb = Aabb(
        Vec3(min(min.x, p.x), min(min.y, p.y), min(min.z, p.z)),
        Vec3(max(max.x, p.x), max(max.y, p.y), max(max.z, p.z)),
    )

    public fun union(o: Aabb): Aabb = when {
        isEmpty -> o
        o.isEmpty -> this
        else -> Aabb(
            Vec3(min(min.x, o.min.x), min(min.y, o.min.y), min(min.z, o.min.z)),
            Vec3(max(max.x, o.max.x), max(max.y, o.max.y), max(max.z, o.max.z)),
        )
    }

    public fun contains(p: Vec3): Boolean = !isEmpty &&
        p.x in min.x..max.x && p.y in min.y..max.y && p.z in min.z..max.z

    public companion object {
        /** The empty box; [union] and [encompass] treat it as the identity. */
        public val EMPTY: Aabb = Aabb(
            Vec3(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY),
            Vec3(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY),
        )

        public fun of(points: Iterable<Vec3>): Aabb =
            points.fold(EMPTY) { box, p -> box.encompass(p) }
    }
}

/** A finite 2D segment. */
public data class Segment2(val a: Vec2, val b: Vec2) {
    public val length: Double get() = a.distanceTo(b)
    public val direction: Vec2 get() = (b - a).normalized()
    public val midpoint: Vec2 get() = (a + b) * 0.5

    /** Point on the segment nearest to [p]. */
    public fun closestPointTo(p: Vec2): Vec2 {
        val ab = b - a
        val l2 = ab.lengthSquared
        if (l2 < EPS) return a
        val t = (((p - a) dot ab) / l2).coerceIn(0.0, 1.0)
        return a + ab * t
    }

    public fun distanceTo(p: Vec2): Double = closestPointTo(p).distanceTo(p)
}

/** Result of intersecting two segments. */
public sealed interface Intersection2 {
    public data class Point(val point: Vec2) : Intersection2
    public data object None : Intersection2
    public data object Collinear : Intersection2
}

/**
 * Intersects two segments. Returns [Intersection2.Point] only when the
 * crossing lies within both segments.
 */
public fun intersect(s1: Segment2, s2: Segment2): Intersection2 {
    val r = s1.b - s1.a
    val s = s2.b - s2.a
    val denom = r cross s
    val qp = s2.a - s1.a
    if (abs(denom) < EPS) {
        return if (abs(qp cross r) < EPS) Intersection2.Collinear else Intersection2.None
    }
    val t = (qp cross s) / denom
    val u = (qp cross r) / denom
    return if (t in 0.0..1.0 && u in 0.0..1.0) {
        Intersection2.Point(s1.a + r * t)
    } else {
        Intersection2.None
    }
}

/**
 * Signed area of the polygon described by [points] (shoelace formula).
 * Positive for counter-clockwise winding.
 */
public fun signedArea(points: List<Vec2>): Double {
    if (points.size < 3) return 0.0
    var sum = 0.0
    for (i in points.indices) {
        val p = points[i]
        val q = points[(i + 1) % points.size]
        sum += p cross q
    }
    return sum * 0.5
}

/** Absolute area of the polygon described by [points]. */
public fun area(points: List<Vec2>): Double = abs(signedArea(points))

/** Perimeter of the closed polygon described by [points]. */
public fun perimeter(points: List<Vec2>): Double {
    if (points.size < 2) return 0.0
    var sum = 0.0
    for (i in points.indices) {
        sum += points[i].distanceTo(points[(i + 1) % points.size])
    }
    return sum
}

/** Length of the open polyline described by [points]. */
public fun polylineLength(points: List<Vec2>): Double {
    var sum = 0.0
    for (i in 0 until points.size - 1) sum += points[i].distanceTo(points[i + 1])
    return sum
}

/** Area-weighted centroid of the polygon; falls back to the vertex average. */
public fun centroid(points: List<Vec2>): Vec2 {
    if (points.isEmpty()) return Vec2.ZERO
    val a = signedArea(points)
    if (abs(a) < EPS) {
        return points.fold(Vec2.ZERO) { acc, p -> acc + p } / points.size.toDouble()
    }
    var cx = 0.0
    var cy = 0.0
    for (i in points.indices) {
        val p = points[i]
        val q = points[(i + 1) % points.size]
        val f = p cross q
        cx += (p.x + q.x) * f
        cy += (p.y + q.y) * f
    }
    val s = 1.0 / (6.0 * a)
    return Vec2(cx * s, cy * s)
}

/** True when [p] lies inside the polygon (ray-casting, even-odd rule). */
public fun containsPoint(polygon: List<Vec2>, p: Vec2): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        if ((pi.y > p.y) != (pj.y > p.y)) {
            val xAt = pi.x + (p.y - pi.y) / (pj.y - pi.y) * (pj.x - pi.x)
            if (p.x < xAt) inside = !inside
        }
        j = i
    }
    return inside
}

/**
 * Samples a circular arc into [segments] + 1 points.
 *
 * Angles are in radians, measured counter-clockwise from +X. When
 * [endAngle] <= [startAngle] a full turn is added, matching DXF arc semantics.
 */
public fun tessellateArc(
    center: Vec2,
    radius: Double,
    startAngle: Double,
    endAngle: Double,
    segments: Int = 32,
): List<Vec2> {
    require(segments >= 1) { "segments must be >= 1, was $segments" }
    var sweep = endAngle - startAngle
    if (sweep <= 0.0) sweep += 2.0 * PI
    return (0..segments).map { i ->
        val t = startAngle + sweep * i / segments
        Vec2(center.x + radius * cos(t), center.y + radius * sin(t))
    }
}

/** Samples a full circle as a closed ring of [segments] points. */
public fun tessellateCircle(center: Vec2, radius: Double, segments: Int = 64): List<Vec2> {
    require(segments >= 3) { "segments must be >= 3, was $segments" }
    return (0 until segments).map { i ->
        val t = 2.0 * PI * i / segments
        Vec2(center.x + radius * cos(t), center.y + radius * sin(t))
    }
}
