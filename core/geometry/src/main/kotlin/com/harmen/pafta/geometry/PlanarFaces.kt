package com.harmen.pafta.geometry

import kotlin.math.abs
import kotlin.math.atan2

/**
 * One side of an enclosed area, and which of the original segments it came from.
 *
 * [source] is an index back into the list handed to [faceContaining]. A room is
 * measured to the faces of the walls around it, not to their centre lines, so
 * whoever asked for the face has to be able to look up how thick each wall on it
 * was — and only they know that.
 */
public data class FaceEdge(val a: Vec2, val b: Vec2, val source: Int)

/**
 * The smallest area enclosed by [segments] that contains [point], or null when
 * the point is not enclosed at all.
 *
 * This is what turns "tap inside a room" into a room. The segments are wall
 * centre lines; they are cut at every crossing into a proper planar map, the
 * faces of that map are walked, and the smallest one the tap fell inside is the
 * room. Walls that stop in mid-air are handled without special-casing: a spur
 * is walked out and back, encloses nothing, and is then dropped from the answer.
 *
 * @param tolerance how close two points must be to count as the same corner, in
 *   the same units as the segments. Millimetres, in PAFTA.
 */
public fun faceContaining(
    segments: List<Segment2>,
    point: Vec2,
    tolerance: Double = 1.0,
): List<FaceEdge>? {
    if (segments.size < 3) return null

    val pieces = splitAtCrossings(segments, tolerance)
    if (pieces.size < 3) return null

    val map = PlanarMap(pieces, tolerance)
    var best: List<FaceEdge>? = null
    var bestArea = Double.MAX_VALUE

    for (face in map.faces()) {
        val trimmed = withoutSpurs(face)
        if (trimmed.size < 3) continue

        val polygon = trimmed.map { it.a }
        // A bounded face comes out anticlockwise; the one unbounded face that
        // wraps the whole drawing comes out the other way and is skipped here.
        val signed = signedArea(polygon)
        if (signed <= 0.0) continue
        if (!containsPoint(polygon, point)) continue

        if (signed < bestArea) {
            bestArea = signed
            best = trimmed
        }
    }
    return best
}

/**
 * The polygon moved inward, edge by edge, by the distances in [insets].
 *
 * A room's floor area is measured to the plaster, so every edge of the face
 * moves in by half the thickness of the wall on it. Corners are the meeting of
 * the two moved edges, which is what keeps a room rectangular after the move
 * instead of rounding its corners off.
 *
 * Returns null when an inset would turn the polygon inside out — a 3000mm room
 * cannot have 2000mm walls on both sides — because a negative room is worse
 * than no room.
 */
public fun insetPolygon(polygon: List<Vec2>, insets: List<Double>): List<Vec2>? {
    if (polygon.size < 3 || insets.size != polygon.size) return null

    // Each edge becomes a line: a point on it, and the direction along it.
    val bases = ArrayList<Vec2>(polygon.size)
    val directions = ArrayList<Vec2>(polygon.size)
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        val along = b - a
        if (along.lengthSquared < 1e-12) return null
        val unit = along.normalized()
        // The polygon runs anticlockwise, so its inside is to the left of every
        // edge, and left of (x, y) is (-y, x).
        val inward = Vec2(-unit.y, unit.x)
        bases += a + inward * insets[i]
        directions += unit
    }

    val moved = ArrayList<Vec2>(polygon.size)
    for (i in polygon.indices) {
        val previous = (i - 1 + polygon.size) % polygon.size
        val corner = lineIntersection(
            bases[previous],
            directions[previous],
            bases[i],
            directions[i],
        )
        // Two edges in line with each other never cross; the moved corner is
        // then simply the moved point, which is exactly right.
        moved += corner ?: bases[i]
    }

    // An inset that has gone too far turns every edge round to face the other
    // way. Area alone does not catch it: a square turned inside out through its
    // own centre has the same area and the same handedness as it started with,
    // which is exactly what a room with walls thicker than itself produces.
    for (i in moved.indices) {
        val before = polygon[(i + 1) % polygon.size] - polygon[i]
        val after = moved[(i + 1) % moved.size] - moved[i]
        if ((before dot after) <= 0.0) return null
    }

    if (signedArea(moved) <= 0.0) return null
    return moved
}

/** Where two infinite lines meet, or null when they are parallel. */
private fun lineIntersection(p: Vec2, r: Vec2, q: Vec2, s: Vec2): Vec2? {
    val denominator = r cross s
    if (abs(denominator) < 1e-12) return null
    val t = ((q - p) cross s) / denominator
    return p + r * t
}

/**
 * Every segment cut at every point another segment touches or crosses it.
 *
 * Faces can only be walked on a map whose edges meet at their ends. Two walls
 * crossing in the middle are two segments to the eye and four to the walk.
 */
private fun splitAtCrossings(segments: List<Segment2>, tolerance: Double): List<FaceEdge> {
    val out = ArrayList<FaceEdge>()

    for (i in segments.indices) {
        val s = segments[i]
        val along = s.b - s.a
        val lengthSquared = along.lengthSquared
        if (lengthSquared < tolerance * tolerance) continue

        val cuts = sortedSetOf(0.0, 1.0)
        for (j in segments.indices) {
            if (i == j) continue
            val other = segments[j]

            crossingParameter(s, other)?.let { if (it > 0.0 && it < 1.0) cuts.add(it) }

            // A wall that ends against the middle of another one crosses
            // nothing, but it still divides it. Without this a T-junction
            // leaves the crossbar whole and the room around it open.
            for (end in listOf(other.a, other.b)) {
                if (s.closestPointTo(end).distanceTo(end) > tolerance) continue
                val t = ((end - s.a) dot along) / lengthSquared
                if (t > 0.0 && t < 1.0) cuts.add(t)
            }
        }

        val ordered = cuts.toList()
        for (k in 0 until ordered.size - 1) {
            val a = s.a + along * ordered[k]
            val b = s.a + along * ordered[k + 1]
            if (a.distanceTo(b) > tolerance) out += FaceEdge(a, b, i)
        }
    }
    return out
}

/** Where [other] crosses [s], as a fraction along [s], or null. */
private fun crossingParameter(s: Segment2, other: Segment2): Double? {
    val r = s.b - s.a
    val q = other.b - other.a
    val denominator = r cross q
    if (abs(denominator) < 1e-12) return null

    val offset = other.a - s.a
    val t = (offset cross q) / denominator
    val u = (offset cross r) / denominator
    return if (t in 0.0..1.0 && u in 0.0..1.0) t else null
}

/**
 * Drops the walls that lead nowhere.
 *
 * A wall ending in mid-air is walked down and straight back up, so it appears
 * twice in a row and adds nothing to the area. Left in, it would be counted as
 * two edges of the room and given a thickness inset it has no business having.
 */
private fun withoutSpurs(face: List<FaceEdge>): List<FaceEdge> {
    var current = face
    while (true) {
        val next = ArrayList<FaceEdge>(current.size)
        var dropped = false
        var i = 0
        while (i < current.size) {
            val here = current[i]
            val after = current[(i + 1) % current.size]
            val goesBack = here.a == after.b && here.b == after.a
            if (goesBack && i + 1 < current.size) {
                dropped = true
                i += 2
            } else {
                next += here
                i += 1
            }
        }
        if (!dropped) return next
        current = next
    }
}

/**
 * Segments turned into a map that can be walked: corners that meet are one
 * corner, and the edges at each corner are in order around it.
 */
private class PlanarMap(pieces: List<FaceEdge>, tolerance: Double) {

    private val points = ArrayList<Vec2>()
    private val cells = HashMap<Long, Int>()
    private val starts = ArrayList<Int>()
    private val ends = ArrayList<Int>()
    private val sources = ArrayList<Int>()

    /** Half-edges leaving each corner, in order around it. */
    private val leaving = ArrayList<MutableList<Int>>()

    /** Where each half-edge sits in its corner's order, so the walk is O(1). */
    private val placeInOrder = HashMap<Int, Int>()

    private val cellSize = tolerance

    init {
        val seen = HashSet<Long>()
        for (piece in pieces) {
            val u = cornerAt(piece.a)
            val v = cornerAt(piece.b)
            if (u == v) continue
            // The same wall drawn twice is one edge, not two; two edges between
            // the same pair of corners would enclose an area of nothing.
            val key = minOf(u, v).toLong() * PRIME + maxOf(u, v).toLong()
            if (!seen.add(key)) continue
            starts += u
            ends += v
            sources += piece.source
        }

        repeat(points.size) { leaving += mutableListOf<Int>() }
        for (half in 0 until starts.size * 2) leaving[from(half)] += half
        for (corner in leaving.indices) {
            val order = leaving[corner]
            order.sortBy { half ->
                val direction = points[to(half)] - points[from(half)]
                atan2(direction.y, direction.x)
            }
            order.forEachIndexed { index, half -> placeInOrder[half] = index }
        }
    }

    /** The corner at [p], reusing one already there. */
    private fun cornerAt(p: Vec2): Int {
        val column = Math.round(p.x / cellSize)
        val row = Math.round(p.y / cellSize)
        // The neighbouring cells too: two corners a hair apart must not land in
        // different cells and become two corners.
        for (dx in -1..1) {
            for (dy in -1..1) {
                cells[key(column + dx, row + dy)]?.let { return it }
            }
        }
        val id = points.size
        points += p
        cells[key(column, row)] = id
        return id
    }

    private fun key(column: Long, row: Long): Long = column * PRIME + row

    private fun from(half: Int): Int =
        if (half % 2 == 0) starts[half / 2] else ends[half / 2]

    private fun to(half: Int): Int =
        if (half % 2 == 0) ends[half / 2] else starts[half / 2]

    /**
     * The next half-edge walking a face.
     *
     * Arrive at a corner, turn to face back the way you came, then take the
     * first edge clockwise from there. Doing that everywhere walks each face
     * exactly once, and walks the bounded ones anticlockwise.
     */
    private fun nextInFace(half: Int): Int {
        val back = half xor 1
        val order = leaving[from(back)]
        val place = placeInOrder.getValue(back)
        return order[(place - 1 + order.size) % order.size]
    }

    /** Every face of the map, each as the edges around it in order. */
    fun faces(): List<List<FaceEdge>> {
        val walked = BooleanArray(starts.size * 2)
        val out = ArrayList<List<FaceEdge>>()

        for (start in walked.indices) {
            if (walked[start]) continue
            val face = ArrayList<FaceEdge>()
            var half = start
            while (!walked[half]) {
                walked[half] = true
                face += FaceEdge(points[from(half)], points[to(half)], sources[half / 2])
                half = nextInFace(half)
            }
            if (face.size >= 3) out += face
        }
        return out
    }

    private companion object {
        /** Odd and large, so two coordinates cannot collide by accident. */
        const val PRIME = 73_856_093L
    }
}
