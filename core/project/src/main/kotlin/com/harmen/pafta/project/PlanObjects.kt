package com.harmen.pafta.project

import com.harmen.pafta.dxf.DxfEntity
import com.harmen.pafta.dxf.DxfTextAlign
import com.harmen.pafta.geometry.Segment2
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import com.harmen.pafta.geometry.area
import com.harmen.pafta.geometry.centroid
import com.harmen.pafta.geometry.containsPoint
import com.harmen.pafta.geometry.faceContaining
import com.harmen.pafta.geometry.insetPolygon
import com.harmen.pafta.geometry.offset
import com.harmen.pafta.geometry.rotated
import com.harmen.pafta.geometry.scaled
import kotlin.math.atan2

/**
 * Shapes that cannot draw themselves.
 *
 * A door is a hole in a wall and a room is the space between several, so
 * neither knows its own geometry until it is put next to the walls it belongs
 * to. Everything in this file takes the whole drawing and works them out, which
 * is also what makes a door follow its wall and a room follow its walls.
 */

/** An arc of a circle, in drawing millimetres and degrees anticlockwise from east. */
public data class PlanArc(
    val centre: Vec2,
    val radiusMm: Double,
    val startDegrees: Double,
    val endDegrees: Double,
)

/** A door or window worked out against the wall it sits in. */
public data class OpeningPlan(
    val id: String,
    val layer: String,
    val kind: OpeningKind,
    /** The piece of wall the opening takes out, as four corners. */
    val cut: List<Vec2>,
    /** The two jambs: the wall's cut ends, which are drawn back in. */
    val jambs: List<Segment2>,
    /** A window's glass, or a door's leaf. */
    val leaf: List<Segment2>,
    /** The quarter circle a door swings through. Null for a window. */
    val swing: PlanArc?,
)

/** A room worked out from the walls around the point that made it. */
public data class ZonePlan(
    val id: String,
    val layer: String,
    val name: String,
    /** The floor, to the faces of the walls. Empty when the walls no longer close. */
    val outline: List<Vec2>,
    /** Square millimetres of floor. */
    val areaMm2: Double,
    /** Where the name and the area are written. */
    val anchor: Vec2,
) {
    /** True when the walls around the room no longer enclose it. */
    public val isOpen: Boolean get() = outline.size < 3
}

/**
 * How far past the wall's faces an opening is cut.
 *
 * A cut exactly as deep as the wall leaves a hairline of wall behind at both
 * faces, because the two edges land on each other. A millimetre over and the
 * hole is a hole.
 */
private const val CUT_OVERSHOOT_MM = 1.0

/** The openings among these shapes, each against the wall it is in. */
public fun List<DrawnShape>.openingPlans(): List<OpeningPlan> {
    val walls = filterIsInstance<DrawnShape.Wall>().associateBy { it.id }
    return filterIsInstance<DrawnShape.Opening>().mapNotNull { opening ->
        walls[opening.wallId]?.let { opening.planIn(it) }
    }
}

/**
 * Everything that walls a room in, as straight pieces with a thickness each.
 *
 * Walls, and the rounded corners between them. A fillet parts the two walls'
 * centre lines by a radius, so a room whose corner has been rounded would
 * otherwise report that its walls no longer close — the corner was tidied and
 * the room fell open, which is the opposite of what tidying it should do. The
 * curve is cut into short straight pieces so the same face-finding works on it.
 */
private fun List<DrawnShape>.wallPieces(): List<Pair<Segment2, Double>> {
    val pieces = mutableListOf<Pair<Segment2, Double>>()
    for (shape in this) {
        when {
            shape is DrawnShape.Wall -> pieces += shape.centreLine() to shape.thicknessMm

            shape is DrawnShape.Arc && shape.thicknessMm >= EPSILON_MM -> {
                val points = shape.centreLinePoints()
                for (i in 0 until points.size - 1) {
                    pieces += Segment2(points[i], points[i + 1]) to shape.thicknessMm
                }
            }
        }
    }
    return pieces
}

/** The rooms among these shapes, each measured from the walls around it. */
public fun List<DrawnShape>.zonePlans(): List<ZonePlan> {
    val zones = filterIsInstance<DrawnShape.Zone>()
    if (zones.isEmpty()) return emptyList()

    val pieces = wallPieces()
    val centreLines = pieces.map { it.first }

    return zones.map { zone ->
        val seed = zone.seed.toVec2()
        val face = faceContaining(centreLines, seed, tolerance = JOIN_TOLERANCE_MM)

        // A room is measured to the plaster, so each side comes in by half the
        // thickness of the wall that side is.
        val outline = face?.let {
            insetPolygon(it.map { edge -> edge.a }, it.map { edge -> pieces[edge.source].second / 2.0 })
        }.orEmpty()

        ZonePlan(
            id = zone.id,
            layer = zone.layer,
            name = zone.name,
            outline = outline,
            areaMm2 = if (outline.size >= 3) area(outline) else 0.0,
            anchor = if (outline.size >= 3) centroid(outline) else seed,
        )
    }
}

/**
 * The floor slabs among these shapes, each measured from the walls around it.
 *
 * Exactly the same question a room asks — which walls enclose the point that was
 * tapped — and deliberately the same answer, because a floor and the room above
 * it are the same outline. What differs is what is done with it: a room reports
 * its name and its area, a slab reports what it is made of and how thick it is.
 *
 * Measured to the middle of the walls rather than to their faces, unlike a room:
 * a slab is poured under the walls, not between them, so its area is the
 * structural area and a room's is the usable one. They are different numbers
 * and they are both right.
 */
public fun List<DrawnShape>.slabPlans(): List<ZonePlan> {
    val slabs = filterIsInstance<DrawnShape.Slab>()
    if (slabs.isEmpty()) return emptyList()

    val centreLines = wallPieces().map { it.first }

    return slabs.map { slab ->
        val seed = slab.seed.toVec2()
        val face = faceContaining(centreLines, seed, tolerance = JOIN_TOLERANCE_MM)
        val outline = face?.map { it.a }.orEmpty()

        ZonePlan(
            id = slab.id,
            layer = slab.layer,
            name = "",
            outline = outline,
            areaMm2 = if (outline.size >= 3) area(outline) else 0.0,
            // Below the middle, not in it. The room above the slab covers the
            // same floor and writes its name at the centroid, so two labels
            // would land on each other and neither would be readable. A quarter
            // of the room's height down keeps them apart at every zoom, and
            // noting the floor finish low on the room is what a drawing does
            // anyway.
            anchor = if (outline.size >= 3) {
                val middle = centroid(outline)
                val height = outline.maxOf { it.y } - outline.minOf { it.y }
                Vec2(middle.x, middle.y - height * 0.25)
            } else {
                seed
            },
        )
    }
}

/**
 * A parallel copy of this shape, [millimetres] to one side of it.
 *
 * The commonest edit on a plan: the second skin of a cavity wall, the far side
 * of a corridor, a setback line. A positive distance goes to the left of the
 * way the shape was drawn and a negative one to the right, so which side you
 * get is predictable rather than something you find out.
 *
 * Only a wall or a line has a side to be offset to; anything else answers null,
 * and the interface then does not offer it.
 */
public fun DrawnShape.offsetBy(millimetres: Double, id: String): DrawnShape? {
    if (millimetres == 0.0) return null
    return when (this) {
        is DrawnShape.Wall -> offset(centreLine(), millimetres)?.let {
            copy(id = id, a = Vec3(it.a.x, it.a.y, a.z), b = Vec3(it.b.x, it.b.y, b.z))
        }

        is DrawnShape.Line -> offset(Segment2(a.toVec2(), b.toVec2()), millimetres)?.let {
            copy(id = id, a = Vec3(it.a.x, it.a.y, a.z), b = Vec3(it.b.x, it.b.y, b.z))
        }

        else -> null
    }
}

/**
 * This shape turned about its own middle.
 *
 * Turning a wall where it stands is what "this one should run the other way"
 * means, and doing it about the middle keeps it in the room it was in. A
 * circle looks the same afterwards and a rectangle has no way to be drawn at an
 * angle yet, so neither is offered it.
 */
public fun DrawnShape.turnedBy(degrees: Double): DrawnShape? = when (this) {
    is DrawnShape.Wall -> {
        val turned = rotated(listOf(a.toVec2(), b.toVec2()), centreLine().midpoint, degrees)
        copy(a = Vec3(turned[0].x, turned[0].y, a.z), b = Vec3(turned[1].x, turned[1].y, b.z))
    }

    is DrawnShape.Line -> {
        val middle = (a.toVec2() + b.toVec2()) * 0.5
        val turned = rotated(listOf(a.toVec2(), b.toVec2()), middle, degrees)
        copy(a = Vec3(turned[0].x, turned[0].y, a.z), b = Vec3(turned[1].x, turned[1].y, b.z))
    }

    // A piece of furniture and a column turn where they stand, by changing the
    // angle they are drawn at rather than by moving any points: a sofa against
    // the far wall is the same sofa at 180 degrees, and turning it must not
    // drift it across the room.
    is DrawnShape.Block -> copy(rotationDegrees = rotationDegrees + degrees)
    is DrawnShape.Column -> copy(rotationDegrees = rotationDegrees + degrees)

    else -> null
}

/**
 * This shape made bigger or smaller about its own middle.
 *
 * About its own middle, because "make this half the size" said while pointing at
 * one thing means that thing, where it is — scaling about the origin would send
 * it across the sheet, which is the behaviour that makes the command frightening
 * in the programs that do it.
 *
 * An opening is not offered it: a door is 900mm because that is the door that
 * was bought, and its width is typed, not stretched.
 */
public fun DrawnShape.scaledBy(factor: Double): DrawnShape? {
    if (factor <= 0.0 || kotlin.math.abs(factor - 1.0) < EPSILON_MM) return null
    return when (this) {
        is DrawnShape.Wall -> {
            val ends = scaled(listOf(a.toVec2(), b.toVec2()), centreLine().midpoint, factor)
            copy(a = Vec3(ends[0].x, ends[0].y, a.z), b = Vec3(ends[1].x, ends[1].y, b.z))
        }

        is DrawnShape.Line -> {
            val middle = (a.toVec2() + b.toVec2()) * 0.5
            val ends = scaled(listOf(a.toVec2(), b.toVec2()), middle, factor)
            copy(a = Vec3(ends[0].x, ends[0].y, a.z), b = Vec3(ends[1].x, ends[1].y, b.z))
        }

        is DrawnShape.Rectangle -> {
            val middle = (corner.toVec2() + opposite.toVec2()) * 0.5
            val ends = scaled(listOf(corner.toVec2(), opposite.toVec2()), middle, factor)
            copy(
                corner = Vec3(ends[0].x, ends[0].y, corner.z),
                opposite = Vec3(ends[1].x, ends[1].y, opposite.z),
            )
        }

        is DrawnShape.Circle -> copy(radiusMm = radiusMm * factor)
        is DrawnShape.Arc -> copy(radiusMm = radiusMm * factor)
        is DrawnShape.Block -> copy(
            widthMm = drawnWidthMm * factor,
            depthMm = drawnDepthMm * factor,
        )
        else -> null
    }
}

/**
 * This list with one shape dragged, each shape moving the way it is able to.
 *
 * Most things go wherever the finger takes them. A door does not: it is a hole
 * in a wall, and a hole that left its wall would be a hole in the air. So it
 * slides **along** its wall instead, taking only the part of the movement that
 * runs that way and stopping at each end rather than sliding off. Dragging a
 * door across its wall does nothing, which is the truth about doors.
 *
 * A wall carries its own doors and windows without anything being done here:
 * they hold a distance from its start, not a place on the sheet.
 */
public fun List<DrawnShape>.moving(id: String, dx: Double, dy: Double): List<DrawnShape> {
    val target = firstOrNull { it.id == id } ?: return this
    if (target !is DrawnShape.Opening) {
        return map { if (it.id == id) it.translated(dx, dy) else it }
    }

    val wall = filterIsInstance<DrawnShape.Wall>().firstOrNull { it.id == target.wallId }
        ?: return this
    val along = wall.b.toVec2() - wall.a.toVec2()
    val length = along.length
    if (length < 1.0) return this

    val slid = Vec2(dx, dy) dot (along / length)
    val half = target.widthMm / 2.0
    // Kept inside the wall, and never past the point where the two limits meet
    // — a wall barely wider than its own door has one place the door can be.
    val limit = (length - half).coerceAtLeast(half)
    val moved = (target.alongMm + slid).coerceIn(half.coerceAtMost(limit), limit)

    return map { if (it.id == id) target.copy(alongMm = moved) else it }
}

/**
 * This list with one shape replaced, and the walls joined to it brought along.
 *
 * A corner belongs to both walls that meet there. Moving one wall's end without
 * the other leaves the room hanging open — which is exactly what happened when
 * a wall of a finished room was made shorter: the wall obeyed, its neighbour
 * stayed where it was, and the room reported that its walls no longer closed.
 *
 * Two things can happen to a wall whose corner moves, and which one is right
 * depends on the direction:
 *
 *  - the corner slides **along** that wall, so the wall simply gets longer or
 *    shorter and its far end stays put;
 *  - the corner moves **across** it, so the whole wall travels — otherwise it
 *    would be left leaning over at an angle. Its far end has then moved too,
 *    and whatever is joined there follows in turn.
 *
 * That second rule is what keeps a rectangular room rectangular: shortening one
 * side carries the two beside it and shortens the one opposite. Each wall is
 * carried at most once, so a closed ring of walls settles rather than going
 * round for ever.
 */
public fun List<DrawnShape>.replacing(id: String, changed: DrawnShape): List<DrawnShape> {
    val original = firstOrNull { it.id == id }
    if (original !is DrawnShape.Wall || changed !is DrawnShape.Wall) {
        return map { if (it.id == id) changed else it }
    }

    val walls = filterIsInstance<DrawnShape.Wall>()
    val result = HashMap<String, DrawnShape.Wall>()
    val carried = hashSetOf(id)

    val pending = ArrayDeque<Pair<Vec2, Vec2>>()
    fun offer(from: Vec3, to: Vec3) {
        if (from.toVec2().distanceTo(to.toVec2()) > JOIN_TOLERANCE_MM) {
            pending.addLast(from.toVec2() to to.toVec2())
        }
    }
    offer(original.a, changed.a)
    offer(original.b, changed.b)

    while (pending.isNotEmpty()) {
        val (from, to) = pending.removeFirst()
        val shift = to - from

        for (wall in walls) {
            if (wall.id in carried) continue
            val atStart = wall.a.toVec2().distanceTo(from) <= JOIN_TOLERANCE_MM
            val atEnd = wall.b.toVec2().distanceTo(from) <= JOIN_TOLERANCE_MM
            if (!atStart && !atEnd) continue

            carried += wall.id
            val direction = wall.centreLine().let { it.b - it.a }
            if (direction.length < JOIN_TOLERANCE_MM) continue

            // Along the wall, or across it? Along means stretch; across means
            // carry, because a wall left pinned at one end would lean over.
            val slidesAlong = kotlin.math.abs(shift.normalized() dot direction.normalized()) > 0.5

            if (slidesAlong) {
                result[wall.id] = if (atStart) {
                    wall.copy(a = Vec3(to.x, to.y, wall.a.z))
                } else {
                    wall.copy(b = Vec3(to.x, to.y, wall.b.z))
                }
            } else {
                val moved = wall.copy(
                    a = Vec3(wall.a.x + shift.x, wall.a.y + shift.y, wall.a.z),
                    b = Vec3(wall.b.x + shift.x, wall.b.y + shift.y, wall.b.z),
                )
                result[wall.id] = moved
                // Its far end has moved as well, so whatever meets it there
                // has to come too.
                val far = if (atStart) wall.b else wall.a
                val movedFar = if (atStart) moved.b else moved.a
                offer(far, movedFar)
            }
        }
    }

    return map { shape ->
        when {
            shape.id == id -> changed
            else -> result[shape.id] ?: shape
        }
    }
}

/**
 * The shape under a tap, openings and rooms included.
 *
 * Later shapes win, as in [pick], but a room is always beaten by anything drawn
 * on top of it: a room covers the whole floor, and if it could be picked from
 * anywhere inside itself nothing standing on that floor could ever be picked.
 */
public fun List<DrawnShape>.pickResolved(at: Vec2, toleranceMm: Double): DrawnShape? {
    val openings = openingPlans().associateBy { it.id }
    for (shape in asReversed()) {
        if (shape is DrawnShape.Opening) {
            val plan = openings[shape.id] ?: continue
            if (containsPoint(plan.cut, at)) return shape
        }
    }

    pick(at, toleranceMm)?.let { return it }

    // A room and the slab under it fill the same floor, so one pass over both,
    // latest first. That keeps the rule the rest of this function follows — the
    // thing put down most recently is the thing a tap means — and it is what
    // makes a slab reachable at all: two separate passes would have made
    // whichever came second permanently untappable under whichever came first.
    val rooms = zonePlans().associateBy { it.id }
    val slabs = slabPlans().associateBy { it.id }
    for (shape in asReversed()) {
        val plan = when (shape) {
            is DrawnShape.Zone -> rooms[shape.id]
            is DrawnShape.Slab -> slabs[shape.id]
            else -> null
        } ?: continue
        if (plan.outline.size >= 3 && containsPoint(plan.outline, at)) return shape
    }
    return null
}

/**
 * Everything these shapes are drawn and exported as.
 *
 * The list, not the shape: an opening and a room have no geometry of their own,
 * so `shapes.flatMap { it.toEntities() }` silently loses both. This is what the
 * exporter and the entity count use.
 */
public fun List<DrawnShape>.toEntities(): List<DxfEntity> = buildList {
    for (shape in this@toEntities) addAll(shape.toEntities())

    for (plan in openingPlans()) {
        for (jamb in plan.jambs) add(DxfEntity.Line(plan.layer, jamb.a.toVec3(), jamb.b.toVec3()))
        for (line in plan.leaf) add(DxfEntity.Line(plan.layer, line.a.toVec3(), line.b.toVec3()))
        plan.swing?.let {
            add(
                DxfEntity.Arc(
                    layer = plan.layer,
                    centre = it.centre.toVec3(),
                    radius = it.radiusMm,
                    startAngleDegrees = it.startDegrees,
                    endAngleDegrees = it.endDegrees,
                ),
            )
        }
    }

    for (plan in zonePlans()) {
        if (plan.outline.size < 3) continue
        add(DxfEntity.Polyline(layer = plan.layer, vertices = plan.outline, closed = true))
        if (plan.name.isNotBlank()) {
            add(
                DxfEntity.Text(
                    layer = plan.layer,
                    position = plan.anchor.toVec3(),
                    value = plan.name,
                    height = ROOM_LABEL_HEIGHT_MM,
                    align = DxfTextAlign.CENTRE,
                ),
            )
        }
    }
}

/** Room names are written at this size on the plan, in drawing millimetres. */
private const val ROOM_LABEL_HEIGHT_MM: Double = 250.0

private fun Vec2.toVec3(): Vec3 = Vec3(x, y, 0.0)

/**
 * Where this opening falls on [wall], and what is drawn there.
 *
 * Returns null for an opening that cannot fit in its wall — a 2m door in a 1m
 * wall is a mistake, and half a door drawn out into the air is a worse answer
 * than none.
 */
private fun DrawnShape.Opening.planIn(wall: DrawnShape.Wall): OpeningPlan? {
    val start = wall.a.toVec2()
    val finish = wall.b.toVec2()
    val along = finish - start
    val wallLength = along.length
    if (wallLength < 1.0 || widthMm < 1.0 || widthMm > wallLength) return null

    val unit = along / wallLength
    val across = Vec2(-unit.y, unit.x)
    val half = widthMm / 2.0

    // Kept inside the wall it is in, rather than refused: an opening dragged
    // towards a corner should stop at the corner, not vanish.
    val centre = start + unit * alongMm.coerceIn(half, wallLength - half)

    val nearJamb = centre - unit * half
    val farJamb = centre + unit * half
    val reach = across * (wall.thicknessMm / 2.0 + CUT_OVERSHOOT_MM)
    val face = across * (wall.thicknessMm / 2.0)

    val cut = listOf(nearJamb + reach, farJamb + reach, farJamb - reach, nearJamb - reach)
    val jambs = listOf(
        Segment2(nearJamb + face, nearJamb - face),
        Segment2(farJamb + face, farJamb - face),
    )

    return when (kind) {
        // Three lines across the hole: the two faces of the frame and the glass
        // between them. This is how a window is drawn on every plan.
        OpeningKind.WINDOW -> OpeningPlan(
            id = id,
            layer = layer,
            kind = kind,
            cut = cut,
            jambs = jambs,
            leaf = listOf(
                Segment2(nearJamb + face, farJamb + face),
                Segment2(nearJamb, farJamb),
                Segment2(nearJamb - face, farJamb - face),
            ),
            swing = null,
        )

        OpeningKind.DOOR -> {
            val hingeAtStart = swing == DoorSwing.LEFT_IN || swing == DoorSwing.LEFT_OUT
            val hinge = if (hingeAtStart) nearJamb else farJamb
            val opposite = if (hingeAtStart) farJamb else nearJamb
            val outward =
                if (swing == DoorSwing.LEFT_OUT || swing == DoorSwing.RIGHT_OUT) across else -across

            // The door drawn open at a right angle, which is the convention: it
            // shows both where the leaf is and how much floor it needs.
            val leafEnd = hinge + outward * widthMm

            OpeningPlan(
                id = id,
                layer = layer,
                kind = kind,
                cut = cut,
                jambs = jambs,
                leaf = listOf(Segment2(hinge, leafEnd)),
                swing = quarterArc(hinge, widthMm, opposite, leafEnd),
            )
        }
    }
}

/**
 * The short way round from one point to the other, about a centre.
 *
 * DXF arcs are always drawn anticlockwise from start to end, so which of the
 * two angles is the start decides whether the arc drawn is the quarter the door
 * sweeps or the three quarters it does not.
 */
private fun quarterArc(centre: Vec2, radiusMm: Double, from: Vec2, to: Vec2): PlanArc {
    val startAngle = degrees(from - centre)
    val endAngle = degrees(to - centre)
    val anticlockwise = (endAngle - startAngle + 360.0) % 360.0

    return if (anticlockwise <= 180.0) {
        PlanArc(centre, radiusMm, startAngle, endAngle)
    } else {
        PlanArc(centre, radiusMm, endAngle, startAngle)
    }
}

private fun degrees(v: Vec2): Double = (Math.toDegrees(atan2(v.y, v.x)) + 360.0) % 360.0
