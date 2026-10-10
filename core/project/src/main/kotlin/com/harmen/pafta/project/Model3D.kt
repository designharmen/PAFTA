package com.harmen.pafta.project

import com.harmen.pafta.geometry.Aabb
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import com.harmen.pafta.geometry.area

/**
 * The building the plan describes, as flat faces in space.
 *
 * PAFTA has been storing the third dimension since the very first wall: a wall
 * has a height, a door has a height and a sill, a column has a height, a slab
 * has a thickness and a level. None of it appeared anywhere, because a plan is
 * a horizontal cut and a height does not show in one. This is where it all
 * comes out.
 *
 * Deliberately faces rather than a mesh with shared vertices: a wall is twenty
 * quads, a house is a few hundred, and a few hundred quads sorted back to front
 * draw perfectly well on a canvas. Shared vertices and a depth buffer would buy
 * nothing here and cost the thing this project values most — being able to test
 * the answer on a machine with no screen.
 */

/** What part of the building a face belongs to, which is what shades it. */
public enum class FaceKind { WALL, WALL_TOP, OPENING_REVEAL, COLUMN, COLUMN_TOP, SLAB, FURNITURE }

/**
 * One flat face: its corners in order, and what it is.
 *
 * [points] go round the face; three or more, and all on one plane — everything
 * built here is either upright or level, so that is true by construction rather
 * than by hope.
 */
public data class Face3(
    val points: List<Vec3>,
    val kind: FaceKind,
    /** The layer it came from, so hiding a layer hides it here too. */
    val layer: String,
    /** Which shape it belongs to, so the selected one can be picked out. */
    val id: String,
) {
    /** The middle of the face, which is what it is sorted by. */
    public val centre: Vec3
        get() = points.fold(Vec3.ZERO) { sum, p -> sum + p } / points.size.toDouble()

    /**
     * Which way the face looks.
     *
     * Newell's method rather than the cross product of the first two edges: a
     * face with three points in a line would give zero from the simple version,
     * and a wall 1mm long is allowed to exist.
     */
    public val normal: Vec3
        get() {
            var x = 0.0
            var y = 0.0
            var z = 0.0
            for (i in points.indices) {
                val a = points[i]
                val b = points[(i + 1) % points.size]
                x += (a.y - b.y) * (a.z + b.z)
                y += (a.z - b.z) * (a.x + b.x)
                z += (a.x - b.x) * (a.y + b.y)
            }
            return Vec3(x, y, z).normalized()
        }
}

/**
 * Everything these shapes stand up as, ready to be drawn.
 *
 * The one place the plan becomes a building. Everything downstream — the
 * camera, the sorting, the shading — sees flat faces and knows nothing about
 * walls or doors.
 */
public fun List<DrawnShape>.toFaces(): List<Face3> {
    // Named rather than used through the receiver, and not by preference: the
    // first version did all of this inside `buildList`, where `this` is the
    // list being built. `filterIsInstance` takes an `Iterable<*>`, so it bound
    // to that happily and filtered the *output* for walls — which compiled,
    // ran, and stood up an empty building. Every test caught it at once; no
    // compiler ever would have.
    val shapes = this
    val walls = shapes.filterIsInstance<DrawnShape.Wall>()
    val openingsByWall = shapes.filterIsInstance<DrawnShape.Opening>().groupBy { it.wallId }
    val slabs = shapes.filterIsInstance<DrawnShape.Slab>()

    return buildList {
        for (wall in walls) addAll(wall.faces(openingsByWall[wall.id].orEmpty()))
        for (column in shapes.filterIsInstance<DrawnShape.Column>()) addAll(column.faces())
        for (plan in shapes.slabPlans()) {
            if (plan.outline.size < 3) continue
            val slab = slabs.firstOrNull { it.id == plan.id } ?: continue
            addAll(slab.faces(plan.outline))
        }
        for (piece in shapes.filterIsInstance<DrawnShape.Block>()) addAll(piece.faces())
    }
}

/** How far the building reaches, in all three directions. */
public fun List<Face3>.bounds(): Aabb =
    flatMap { it.points }.fold(Aabb.EMPTY) { box, p -> box.encompass(p) }

// --- walls -------------------------------------------------------------------

/**
 * A wall as six-ish faces: the two long sides, the two ends, and the top.
 *
 * The long sides are the interesting part. A wall with a door in it is not one
 * rectangle, it is the pieces of wall either side of the door plus the lintel
 * over it — and for a window, the wall under the sill as well. Getting that
 * right is the difference between a model you can see through a doorway and a
 * model with a door painted on a solid block.
 */
private fun DrawnShape.Wall.faces(openings: List<DrawnShape.Opening>): List<Face3> {
    val start = a.toVec2()
    val end = b.toVec2()
    val along = end - start
    val length = along.length
    if (length < EPSILON_MM || heightMm < EPSILON_MM) return emptyList()

    val unit = along / length
    val sideways = Vec2(-unit.y, unit.x) * (thicknessMm / 2.0)
    val base = a.z

    /** A point on the wall: [d] along it, [side] across it, [h] up it. */
    fun at(d: Double, side: Double, h: Double): Vec3 {
        val p = start + unit * d + sideways * side
        return Vec3(p.x, p.y, base + h)
    }

    // Where the holes are, as spans along the wall, in order and clipped to it.
    val holes = openings
        .mapNotNull { opening ->
            val half = opening.widthMm / 2.0
            val from = (opening.alongMm - half).coerceIn(0.0, length)
            val to = (opening.alongMm + half).coerceIn(0.0, length)
            if (to - from < EPSILON_MM) null else Hole(from, to, opening)
        }
        .sortedBy { it.from }

    return buildList {
        for (side in listOf(1.0, -1.0)) {
            var at = 0.0
            for (hole in holes) {
                if (hole.from > at + EPSILON_MM) {
                    add(sideFace(::at, side, at, hole.from, 0.0, heightMm))
                }
                val head = (hole.opening.sillMm + hole.opening.heightMm).coerceAtMost(heightMm)
                // The lintel over the hole, and the wall under a window's sill.
                if (heightMm - head > EPSILON_MM) {
                    add(sideFace(::at, side, hole.from, hole.to, head, heightMm))
                }
                if (hole.opening.sillMm > EPSILON_MM) {
                    add(sideFace(::at, side, hole.from, hole.to, 0.0, hole.opening.sillMm))
                }
                at = maxOf(at, hole.to)
            }
            if (length - at > EPSILON_MM) {
                add(sideFace(::at, side, at, length, 0.0, heightMm))
            }
        }

        // The two ends, square across the wall.
        for (d in listOf(0.0, length)) {
            add(
                Face3(
                    points = listOf(
                        at(d, 1.0, 0.0),
                        at(d, -1.0, 0.0),
                        at(d, -1.0, heightMm),
                        at(d, 1.0, heightMm),
                    ),
                    kind = FaceKind.WALL,
                    layer = layer,
                    id = id,
                ),
            )
        }

        // The top, in one piece: a doorway does not go through the top of a
        // wall, it goes through the side of one.
        add(
            Face3(
                points = listOf(
                    at(0.0, 1.0, heightMm),
                    at(length, 1.0, heightMm),
                    at(length, -1.0, heightMm),
                    at(0.0, -1.0, heightMm),
                ),
                kind = FaceKind.WALL_TOP,
                layer = layer,
                id = id,
            ),
        )

        // The reveals: the faces you see looking into a doorway, which are what
        // give an opening its depth instead of leaving a hole with no sides.
        for (hole in holes) {
            val head = (hole.opening.sillMm + hole.opening.heightMm).coerceAtMost(heightMm)
            for (d in listOf(hole.from, hole.to)) {
                add(
                    Face3(
                        points = listOf(
                            at(d, 1.0, hole.opening.sillMm),
                            at(d, -1.0, hole.opening.sillMm),
                            at(d, -1.0, head),
                            at(d, 1.0, head),
                        ),
                        kind = FaceKind.OPENING_REVEAL,
                        layer = hole.opening.layer,
                        id = hole.opening.id,
                    ),
                )
            }
            if (head < heightMm - EPSILON_MM) {
                add(
                    Face3(
                        points = listOf(
                            at(hole.from, 1.0, head),
                            at(hole.to, 1.0, head),
                            at(hole.to, -1.0, head),
                            at(hole.from, -1.0, head),
                        ),
                        kind = FaceKind.OPENING_REVEAL,
                        layer = hole.opening.layer,
                        id = hole.opening.id,
                    ),
                )
            }
        }
    }
}

/** One span of one side of a wall, from [from] to [to] along it and [low] to [high] up it. */
private fun DrawnShape.Wall.sideFace(
    at: (Double, Double, Double) -> Vec3,
    side: Double,
    from: Double,
    to: Double,
    low: Double,
    high: Double,
): Face3 = Face3(
    points = listOf(
        at(from, side, low),
        at(to, side, low),
        at(to, side, high),
        at(from, side, high),
    ),
    kind = FaceKind.WALL,
    layer = layer,
    id = id,
)

/** A hole in a wall, as a span along it. */
private data class Hole(val from: Double, val to: Double, val opening: DrawnShape.Opening)

// --- columns, slabs and furniture ---------------------------------------------

private fun DrawnShape.Column.faces(): List<Face3> {
    val footprint = if (round) roundFootprint() else outline()
    if (footprint.size < 3 || heightMm < EPSILON_MM) return emptyList()
    return extruded(footprint, centre.z, heightMm, FaceKind.COLUMN, FaceKind.COLUMN_TOP, layer, id)
}

/** A round column's footprint, as a polygon fine enough not to show its corners. */
private fun DrawnShape.Column.roundFootprint(): List<Vec2> {
    val radius = widthMm / 2.0
    if (radius < EPSILON_MM) return emptyList()
    return (0 until 24).map {
        val radians = Math.toRadians(it * 15.0)
        Vec2(
            centre.x + radius * kotlin.math.cos(radians),
            centre.y + radius * kotlin.math.sin(radians),
        )
    }
}

private fun DrawnShape.Slab.faces(outline: List<Vec2>): List<Face3> =
    extruded(outline, levelMm - thicknessMm, thicknessMm, FaceKind.SLAB, FaceKind.SLAB, layer, id)

/**
 * Furniture, as a block the height of the piece.
 *
 * Only the outer outline is stood up: the cushions and the tap are a plan
 * convention, and a bed with its pillows extruded to 450mm would be a bed with
 * a brick on it.
 */
private fun DrawnShape.Block.faces(): List<Face3> {
    val outline = outlines().firstOrNull() ?: return emptyList()
    if (outline.size < 3) return emptyList()
    return extruded(outline, at.z, block.standingHeightMm(), FaceKind.FURNITURE, FaceKind.FURNITURE, layer, id)
}

/** How tall a piece of furniture stands, which the plan never had to know. */
private fun CatalogueBlock.standingHeightMm(): Double = when (this) {
    CatalogueBlock.WARDROBE, CatalogueBlock.FRIDGE -> 2000.0
    CatalogueBlock.TV_UNIT, CatalogueBlock.DESK, CatalogueBlock.COUNTER,
    CatalogueBlock.SINK, CatalogueBlock.HOB,
    -> 900.0
    CatalogueBlock.DINING_TABLE_FOUR, CatalogueBlock.DINING_TABLE_SIX,
    CatalogueBlock.DINING_TABLE_ROUND, CatalogueBlock.CHAIR,
    -> 750.0
    CatalogueBlock.ARMCHAIR, CatalogueBlock.SOFA_TWO, CatalogueBlock.SOFA_THREE -> 800.0
    CatalogueBlock.BED_SINGLE, CatalogueBlock.BED_DOUBLE -> 500.0
    CatalogueBlock.BEDSIDE, CatalogueBlock.COFFEE_TABLE -> 450.0
    CatalogueBlock.WC, CatalogueBlock.BASIN -> 800.0
    CatalogueBlock.SHOWER -> 100.0
    CatalogueBlock.BATH -> 550.0
}

/**
 * A flat outline stood up into a solid: its sides, its top and its bottom.
 *
 * The outline is wound anticlockwise first, so every side face it builds looks
 * outwards. Without that, half the shapes on a plan — which are drawn whichever
 * way round the hand went — would come out inside-out and be shaded as though
 * lit from within.
 */
private fun extruded(
    outline: List<Vec2>,
    baseZ: Double,
    heightMm: Double,
    sideKind: FaceKind,
    capKind: FaceKind,
    layer: String,
    id: String,
): List<Face3> {
    if (outline.size < 3 || heightMm < EPSILON_MM) return emptyList()
    val round = if (area(outline) < 0.0) outline.asReversed() else outline
    val top = baseZ + heightMm

    return buildList {
        for (i in round.indices) {
            val p = round[i]
            val q = round[(i + 1) % round.size]
            add(
                Face3(
                    points = listOf(
                        Vec3(p.x, p.y, baseZ),
                        Vec3(q.x, q.y, baseZ),
                        Vec3(q.x, q.y, top),
                        Vec3(p.x, p.y, top),
                    ),
                    kind = sideKind,
                    layer = layer,
                    id = id,
                ),
            )
        }
        add(Face3(round.map { Vec3(it.x, it.y, top) }, capKind, layer, id))
        add(Face3(round.asReversed().map { Vec3(it.x, it.y, baseZ) }, capKind, layer, id))
    }
}
