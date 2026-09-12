package com.harmen.pafta.dxf

import com.harmen.pafta.geometry.Aabb
import com.harmen.pafta.geometry.Segment2
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3

/**
 * The line segments a measurement can snap to.
 *
 * Measuring by eye on a tablet is guesswork: a finger covers several
 * centimetres of drawing, so a wall measured by touch is never the wall's real
 * length. Snapping is what turns a tap into a corner. These are the candidates
 * it snaps to.
 *
 * @param limit a ceiling on how many segments are produced. A plan expanded
 *   from blocks can carry hundreds of thousands of entities, and snapping walks
 *   the candidates on every tap; past this many the nearest corner is not what
 *   is slowing the app down. When the cap is hit the result is still usable —
 *   it simply covers the entities that came first in the file.
 * @param arcSegments how finely arcs are approximated. Coarser than drawing,
 *   because a snap target every few degrees is beyond what a finger can pick.
 */
public fun DxfDrawing.snapSegments(
    limit: Int = 20_000,
    arcSegments: Int = 12,
    visibleLayers: Set<String>? = null,
): List<Segment2> {
    val out = ArrayList<Segment2>(minOf(limit, entities.size * 2))

    for (entity in entities) {
        if (out.size >= limit) break
        if (visibleLayers != null && entity.layer !in visibleLayers) continue
        // Text has a position but no edge to measure along; an unexpanded block
        // reference is a marker, not geometry.
        if (entity is DxfEntity.Text || entity is DxfEntity.Insert) continue

        val points = entity.outline(arcSegments)
        if (points.size < 2) continue
        for (i in 0 until points.size - 1) {
            if (out.size >= limit) break
            val a = points[i]
            val b = points[i + 1]
            // A zero-length segment offers the same point three times over and
            // makes the nearest-snap search slower for nothing.
            if (a != b) out += Segment2(a, b)
        }
    }

    return out
}

/**
 * Segments near [around], for snapping without walking the whole drawing.
 *
 * A tap only ever snaps to something within a few finger-widths of itself, so
 * candidates further away than [radius] cannot win and need not be measured.
 */
public fun List<Segment2>.near(around: Vec2, radius: Double): List<Segment2> {
    if (radius <= 0.0) return this
    val box = Aabb.EMPTY
        .encompass(Vec3(around.x - radius, around.y - radius, 0.0))
        .encompass(Vec3(around.x + radius, around.y + radius, 0.0))
    return filter { segment ->
        val minX = minOf(segment.a.x, segment.b.x)
        val maxX = maxOf(segment.a.x, segment.b.x)
        val minY = minOf(segment.a.y, segment.b.y)
        val maxY = maxOf(segment.a.y, segment.b.y)
        maxX >= box.min.x && minX <= box.max.x && maxY >= box.min.y && minY <= box.max.y
    }
}
