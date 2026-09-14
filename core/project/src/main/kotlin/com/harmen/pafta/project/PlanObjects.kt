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

/** The rooms among these shapes, each measured from the walls around it. */
public fun List<DrawnShape>.zonePlans(): List<ZonePlan> {
    val zones = filterIsInstance<DrawnShape.Zone>()
    if (zones.isEmpty()) return emptyList()

    val walls = filterIsInstance<DrawnShape.Wall>()
    val centreLines = walls.map { it.centreLine() }

    return zones.map { zone ->
        val seed = zone.seed.toVec2()
        val face = faceContaining(centreLines, seed, tolerance = JOIN_TOLERANCE_MM)

        // A room is measured to the plaster, so each side comes in by half the
        // thickness of the wall that side is.
        val outline = face?.let {
            insetPolygon(it.map { edge -> edge.a }, it.map { edge -> walls[edge.source].thicknessMm / 2.0 })
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

    val rooms = zonePlans().associateBy { it.id }
    for (shape in asReversed()) {
        if (shape is DrawnShape.Zone) {
            val plan = rooms[shape.id] ?: continue
            if (plan.outline.size >= 3 && containsPoint(plan.outline, at)) return shape
        }
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
