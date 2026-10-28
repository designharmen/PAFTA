package com.harmen.pafta.dxf

import com.harmen.pafta.geometry.Aabb
import com.harmen.pafta.geometry.Vec3
import kotlin.math.abs

/**
 * The part of a drawing worth looking at when it first opens.
 *
 * A real CAD file almost always carries something a very long way from the
 * building: a block placed at three thousand times its size, a construction
 * line running off the sheet, a survey point left at the far corner of the
 * world. Framing the view to the outermost thing in the file then puts the
 * building in a fraction of a pixel — the drawing opens, and the screen is
 * blank. From the outside that is indistinguishable from a file that did not
 * open at all, which is exactly how it was reported.
 *
 * So the opening view is taken from where the drawing's *mass* is, not from
 * its extremes. The test for "a long way out" is made against the drawing's own
 * spread rather than any fixed distance, because a site plan and a door detail
 * are three orders of magnitude apart and both are ordinary.
 *
 * Nothing is hidden or discarded: every entity is still drawn, and zooming out
 * reaches all of them. This decides one thing only — where the view starts.
 */
public fun DxfDrawing.openingBounds(): Aabb = entities.openingBounds()

internal fun List<DxfEntity>.openingBounds(): Aabb {
    val whole = boundsOf(this)
    // Too few to tell an outlier from the drawing.
    if (size < MINIMUM_TO_JUDGE) return whole

    val centres = map { it.centre() }
    // The box holding all but the outermost few per cent on each side. A
    // middle-of-the-drawing measure was tried first and was wrong: a plan with
    // a hundred points in one cupboard and the building around it has a tiny
    // typical spread, and the building itself then looks like the outlier.
    // Where the drawing *ends* is the question, so the ends are what is
    // measured.
    val xs = centres.map { it.x }.filter { it.isFinite() }.sorted()
    val ys = centres.map { it.y }.filter { it.isFinite() }.sorted()
    if (xs.isEmpty() || ys.isEmpty()) return whole
    val lowX = xs.at(EDGE)
    val highX = xs.at(1.0 - EDGE)
    val lowY = ys.at(EDGE)
    val highY = ys.at(1.0 - EDGE)

    val core = maxOf(highX - lowX, highY - lowY)
    if (!core.isFinite() || core <= 0.0) return whole

    val middleX = (lowX + highX) / 2.0
    val middleY = (lowY + highY) / 2.0
    val reach = core * ROOM

    val near = filterIndexed { index, _ ->
        val c = centres[index]
        abs(c.x - middleX) <= reach && abs(c.y - middleY) <= reach
    }

    // If trimming would drop a real part of the drawing, the drawing is simply
    // spread out and the whole of it is the right view.
    if (near.size < size * KEEP_AT_LEAST) return whole
    val trimmed = boundsOf(near)
    return if (trimmed.isEmpty) whole else trimmed
}

/** The value this far along a sorted list. */
private fun List<Double>.at(fraction: Double): Double {
    if (isEmpty()) return 0.0
    val index = ((size - 1) * fraction).toInt().coerceIn(0, size - 1)
    return this[index]
}

private fun boundsOf(entities: List<DxfEntity>): Aabb = entities
    .asSequence()
    .flatMap { it.outline().asSequence() }
    .filter { it.x.isFinite() && it.y.isFinite() }
    .fold(Aabb.EMPTY) { box, p -> box.encompass(Vec3(p.x, p.y, 0.0)) }

/** The middle of an entity, cheaply: the centre of the box around it. */
private fun DxfEntity.centre(): Vec3 {
    var minX = Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    var maxY = -Double.MAX_VALUE
    for (p in outline(arcSegments = 4)) {
        if (!p.x.isFinite() || !p.y.isFinite()) continue
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
    }
    if (minX > maxX) return Vec3(0.0, 0.0, 0.0)
    return Vec3((minX + maxX) / 2.0, (minY + maxY) / 2.0, 0.0)
}

private fun List<Double>.median(): Double {
    val sorted = filter { it.isFinite() }.sorted()
    if (sorted.isEmpty()) return 0.0
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[middle]
    } else {
        (sorted[middle - 1] + sorted[middle]) / 2.0
    }
}

/** Below this, there is no "most of the drawing" to speak of. */
private const val MINIMUM_TO_JUDGE = 12

/** How much of each edge is treated as possibly stray while measuring. */
private const val EDGE = 0.05

/**
 * How much further than the drawing itself still counts as part of it.
 *
 * Generous on purpose: a site plan with the building in one corner is normal
 * and must not be cropped. Four times the drawing's own width is not a corner
 * of the sheet, it is another postcode.
 */
private const val ROOM = 4.0

/** Trimming that loses this much of the drawing is not trimming. */
private const val KEEP_AT_LEAST = 0.85
