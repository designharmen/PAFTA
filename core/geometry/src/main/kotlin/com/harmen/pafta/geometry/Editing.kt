package com.harmen.pafta.geometry

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * The drawing-office edits: the operations every CAD program has had since the
 * eighties, because every one of them answers a question a hand asks.
 *
 * All pure geometry, all here rather than in the interface, all tested. None of
 * them touch an imported file: PAFTA's own drawn shapes are what they work on,
 * which is the same rule the rest of the project keeps.
 */

/** A corner turned into an arc, and the two segments trimmed back to meet it. */
public data class FilletResult(
    val first: Segment2,
    val second: Segment2,
    val centre: Vec2,
    val radiusMm: Double,
    val startDegrees: Double,
    val endDegrees: Double,
)

/**
 * Two segments with the corner between them rounded off.
 *
 * Both segments are cut back to where the arc touches them, and the arc runs
 * between those two points. The corner is the meeting of the two lines the
 * segments lie on, so it works whether they already touch, fall short of each
 * other, or cross — which is how fillet behaves in every drawing program, and
 * why it is usually the answer to "these two walls nearly meet".
 *
 * Returns null when the radius will not fit — a 2m fillet between two 300mm
 * walls has nowhere to go — or when the segments are parallel and have no
 * corner at all.
 */
public fun fillet(first: Segment2, second: Segment2, radiusMm: Double): FilletResult? {
    if (radiusMm <= 0.0) return null
    val corner = cornerOf(first, second) ?: return null

    // Each segment is pointed away from the corner: the arc sits in the wedge
    // between those two directions, whichever ends happened to be nearest.
    val awayFirst = directionAwayFrom(corner, first) ?: return null
    val awaySecond = directionAwayFrom(corner, second) ?: return null

    val halfAngle = angleBetween(awayFirst, awaySecond) / 2.0
    if (halfAngle <= 1e-9 || halfAngle >= Math.PI / 2 - 1e-9) return null

    // How far back along each segment the arc touches: the tangent length.
    val setback = radiusMm / tan(halfAngle)
    if (setback > lengthFrom(corner, first) || setback > lengthFrom(corner, second)) return null

    val touchFirst = corner + awayFirst * setback
    val touchSecond = corner + awaySecond * setback

    // The centre is out along the bisector, far enough that both touch points
    // are exactly one radius away.
    val bisector = (awayFirst + awaySecond).let {
        if (it.length < 1e-9) return null else it.normalized()
    }
    val centre = corner + bisector * (radiusMm / sin(halfAngle))

    val start = degreesOf(touchFirst - centre)
    val end = degreesOf(touchSecond - centre)

    return FilletResult(
        first = Segment2(farEndFrom(corner, first), touchFirst),
        second = Segment2(touchSecond, farEndFrom(corner, second)),
        centre = centre,
        radiusMm = radiusMm,
        // Anticlockwise from start to end, the short way: the long way round
        // would draw three quarters of a circle through the room.
        startDegrees = if (shortestTurn(start, end) >= 0.0) start else end,
        endDegrees = if (shortestTurn(start, end) >= 0.0) end else start,
    )
}

/** Two segments cut back, and the straight line that now bridges them. */
public data class ChamferResult(
    val first: Segment2,
    val second: Segment2,
    val bridge: Segment2,
)

/**
 * Two segments with the corner between them cut off straight.
 *
 * The same as [fillet] but with a flat instead of an arc: each segment is cut
 * back by its own distance, and a line joins the two cuts. Two distances rather
 * than one, because a chamfer is not always symmetrical — a 20x50 chamfer is an
 * ordinary thing to ask a joiner for.
 */
public fun chamfer(
    first: Segment2,
    second: Segment2,
    firstMm: Double,
    secondMm: Double,
): ChamferResult? {
    if (firstMm <= 0.0 || secondMm <= 0.0) return null
    val corner = cornerOf(first, second) ?: return null

    val awayFirst = directionAwayFrom(corner, first) ?: return null
    val awaySecond = directionAwayFrom(corner, second) ?: return null
    if (firstMm > lengthFrom(corner, first) || secondMm > lengthFrom(corner, second)) return null

    val cutFirst = corner + awayFirst * firstMm
    val cutSecond = corner + awaySecond * secondMm

    return ChamferResult(
        first = Segment2(farEndFrom(corner, first), cutFirst),
        second = Segment2(cutSecond, farEndFrom(corner, second)),
        bridge = Segment2(cutFirst, cutSecond),
    )
}

/**
 * A segment moved sideways by [distanceMm].
 *
 * A positive distance goes to the left of the segment's own direction, a
 * negative one to the right, so which side you get is a thing you can predict
 * rather than a thing you find out.
 */
public fun offset(segment: Segment2, distanceMm: Double): Segment2? {
    val along = segment.b - segment.a
    if (along.length < 1e-9) return null
    val unit = along.normalized()
    val sideways = Vec2(-unit.y, unit.x) * distanceMm
    return Segment2(segment.a + sideways, segment.b + sideways)
}

/**
 * A run of joined segments moved sideways, with its corners closed up.
 *
 * Offsetting each piece on its own leaves the corners either gaping or
 * overlapping, because the pieces move apart as well as sideways. Every corner
 * is put back where the two moved lines actually cross, which is what makes an
 * offset polyline a polyline rather than a row of separate sticks.
 *
 * @param closed treat the last point as joined to the first.
 */
public fun offsetPolyline(
    points: List<Vec2>,
    distanceMm: Double,
    closed: Boolean = false,
): List<Vec2>? {
    if (points.size < 2) return null

    val edges = ArrayList<Pair<Vec2, Vec2>>()
    val last = if (closed) points.size else points.size - 1
    for (i in 0 until last) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        val along = b - a
        if (along.length < 1e-9) return null
        val unit = along.normalized()
        val sideways = Vec2(-unit.y, unit.x) * distanceMm
        edges += (a + sideways) to unit
    }

    val out = ArrayList<Vec2>(points.size)
    for (i in points.indices) {
        val incoming = when {
            i > 0 -> i - 1
            closed -> edges.size - 1
            else -> -1
        }
        val outgoing = if (i < edges.size) i else -1

        val corner = when {
            // An end of an open run has only one edge, so it just moves.
            incoming < 0 -> edges[outgoing].first
            outgoing < 0 -> edges[incoming].let { (base, unit) -> base + unit * (points[i] - points[i - 1]).length }
            else -> {
                val (baseIn, unitIn) = edges[incoming]
                val (baseOut, unitOut) = edges[outgoing]
                // Two edges in line with each other never cross, and the moved
                // point is then already right.
                meetOf(baseIn, unitIn, baseOut, unitOut) ?: baseOut
            }
        }
        out += corner
    }
    return out
}

/**
 * [segment] cut back to where [boundary] crosses it, keeping the side [keep] is on.
 *
 * Trim is the tool a drawing is tidied with: two walls that overran each other
 * become two walls that meet. The point decides which half survives, because
 * pointing at the part you want is how everybody thinks about it.
 *
 * Returns null when the boundary does not cross the segment at all, so nothing
 * silently disappears.
 */
public fun trim(segment: Segment2, boundary: Segment2, keep: Vec2): Segment2? {
    val cut = crossingOf(segment, boundary) ?: return null

    val along = segment.b - segment.a
    if (along.length < 1e-9) return null
    val atCut = ((cut - segment.a) dot along) / along.lengthSquared
    if (atCut <= 1e-9 || atCut >= 1.0 - 1e-9) return null

    val atKeep = ((keep - segment.a) dot along) / along.lengthSquared
    return if (atKeep < atCut) Segment2(segment.a, cut) else Segment2(cut, segment.b)
}

/**
 * [segment] stretched until it reaches [boundary].
 *
 * The end nearer the boundary is the one that moves, which is what a hand
 * means by "make this reach that". Returns null when the two would never meet
 * however far the segment went, or when it already crosses the boundary — a
 * segment that is already there has nothing to extend.
 */
public fun extend(segment: Segment2, boundary: Segment2): Segment2? {
    val along = segment.b - segment.a
    if (along.length < 1e-9) return null

    // Where the segment's own line meets the boundary — beyond the segment's
    // ends, which is the whole point of extending.
    val boundaryAlong = boundary.b - boundary.a
    if (boundaryAlong.length < 1e-9) return null
    val meeting = meetOf(segment.a, along.normalized(), boundary.a, boundaryAlong.normalized())
        ?: return null

    // It has to land on the boundary itself, not somewhere out past its end.
    val onBoundary = ((meeting - boundary.a) dot boundaryAlong) / boundaryAlong.lengthSquared
    if (onBoundary < -1e-9 || onBoundary > 1.0 + 1e-9) return null

    val atMeeting = ((meeting - segment.a) dot along) / along.lengthSquared
    return when {
        atMeeting > 1.0 + 1e-9 -> Segment2(segment.a, meeting)
        atMeeting < -1e-9 -> Segment2(meeting, segment.b)
        // Already crosses it: there is nothing to reach for.
        else -> null
    }
}

/** [points] turned about [pivot] by [degrees], anticlockwise. */
public fun rotated(points: List<Vec2>, pivot: Vec2, degrees: Double): List<Vec2> {
    val radians = Math.toRadians(degrees)
    val c = cos(radians)
    val s = sin(radians)
    return points.map {
        val d = it - pivot
        pivot + Vec2(d.x * c - d.y * s, d.x * s + d.y * c)
    }
}

/** [points] reflected in the line through [through] along [direction]. */
public fun mirrored(points: List<Vec2>, through: Vec2, direction: Vec2): List<Vec2> {
    if (direction.length < 1e-9) return points
    val unit = direction.normalized()
    return points.map {
        val d = it - through
        // Twice the part along the line, minus the point: the standard
        // reflection, and the only one that keeps distances.
        val alongPart = unit * (d dot unit)
        through + alongPart * 2.0 - d
    }
}

/** [points] scaled about [about] by [factor]. */
public fun scaled(points: List<Vec2>, about: Vec2, factor: Double): List<Vec2> =
    points.map { about + (it - about) * factor }

// --- the small pieces the edits are built from -------------------------------

/** Where the two segments' own lines cross, however far outside either it is. */
private fun cornerOf(first: Segment2, second: Segment2): Vec2? {
    val a = first.b - first.a
    val b = second.b - second.a
    if (a.length < 1e-9 || b.length < 1e-9) return null
    return meetOf(first.a, a.normalized(), second.a, b.normalized())
}

/** Where the two segments actually cross, within both of them. */
private fun crossingOf(first: Segment2, second: Segment2): Vec2? {
    val r = first.b - first.a
    val q = second.b - second.a
    val denominator = r cross q
    if (abs(denominator) < 1e-12) return null

    val offset = second.a - first.a
    val t = (offset cross q) / denominator
    val u = (offset cross r) / denominator
    if (t < 0.0 || t > 1.0 || u < 0.0 || u > 1.0) return null
    return first.a + r * t
}

private fun meetOf(p: Vec2, r: Vec2, q: Vec2, s: Vec2): Vec2? {
    val denominator = r cross s
    if (abs(denominator) < 1e-12) return null
    return p + r * (((q - p) cross s) / denominator)
}

/** The way [segment] runs when it is walked starting from [corner]. */
private fun directionAwayFrom(corner: Vec2, segment: Segment2): Vec2? {
    val far = farEndFrom(corner, segment)
    val away = far - corner
    return if (away.length < 1e-9) null else away.normalized()
}

/** Whichever end of [segment] is further from [corner]. */
private fun farEndFrom(corner: Vec2, segment: Segment2): Vec2 =
    if (segment.a.distanceTo(corner) >= segment.b.distanceTo(corner)) segment.a else segment.b

private fun lengthFrom(corner: Vec2, segment: Segment2): Double =
    farEndFrom(corner, segment).distanceTo(corner)

private fun angleBetween(a: Vec2, b: Vec2): Double =
    abs(atan2(a cross b, a dot b))

private fun degreesOf(v: Vec2): Double = (Math.toDegrees(atan2(v.y, v.x)) + 360.0) % 360.0

/** Positive when the anticlockwise way round from [from] to [to] is the short one. */
private fun shortestTurn(from: Double, to: Double): Double =
    if ((to - from + 360.0) % 360.0 <= 180.0) 1.0 else -1.0
