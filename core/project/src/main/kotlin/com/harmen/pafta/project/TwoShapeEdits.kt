package com.harmen.pafta.project

import com.harmen.pafta.geometry.Segment2
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import com.harmen.pafta.geometry.chamfer
import com.harmen.pafta.geometry.extend
import com.harmen.pafta.geometry.fillet
import com.harmen.pafta.geometry.mirrored
import com.harmen.pafta.geometry.trim

/**
 * The edits that take two shapes.
 *
 * Round this corner, cut that corner off, cut this back to that, stretch this
 * until it reaches that. Every one is the same shape of problem: read two
 * shapes as plain segments, ask `core:geometry` for the answer, and write the
 * answer back as shapes.
 *
 * None of them drags joined walls along, which is deliberate and is the
 * opposite of what changing a wall's length does. Tidying a junction is exactly
 * the moment you want one wall to move and the rest of the room to stay put.
 */

/** Why a two-shape edit could not be done. Data, not a sentence. */
public enum class EditRefusal {
    /** One of the two is not something with two ends — a room, a door, an arc. */
    NOT_A_LINE,

    /** They are parallel, so there is no corner between them to work on. */
    NO_CORNER,

    /** The radius or the cut is longer than the shapes have to give. */
    DOES_NOT_FIT,

    /** They do not cross, so there is nothing to cut back to. */
    DOES_NOT_CROSS,

    /** It already reaches, so there is nothing to stretch. */
    ALREADY_REACHES,

    /** The two are not on the same straight line, so they cannot become one. */
    NOT_IN_LINE,

    /** The two do not touch, so joining them would invent a piece of wall. */
    DO_NOT_TOUCH,

    /** This is not something that can be reflected. */
    CANNOT_MIRROR,
}

/** What a two-shape edit did, or why it did nothing. */
public sealed interface EditOutcome {
    /** The whole drawing, as it is after the edit. */
    public data class Done(val shapes: List<DrawnShape>) : EditOutcome

    public data class Refused(val reason: EditRefusal) : EditOutcome
}

/**
 * The corner between two shapes, rounded off by [radiusMm].
 *
 * Both are cut back to where the arc touches them and a new arc is added
 * between. It works on two that already meet, two that fall short of each
 * other, and two that cross — the corner is where their lines meet, not where
 * the drawn pieces happen to stop.
 */
public fun List<DrawnShape>.filleted(
    firstId: String,
    secondId: String,
    radiusMm: Double,
): EditOutcome {
    val first = shapeAndLine(firstId) ?: return refuse(EditRefusal.NOT_A_LINE)
    val second = shapeAndLine(secondId) ?: return refuse(EditRefusal.NOT_A_LINE)
    if (firstId == secondId) return refuse(EditRefusal.NO_CORNER)

    val rounded = fillet(first.second, second.second, radiusMm)
        ?: return refuse(
            // Parallel lines have no corner; everything else that fails here
            // failed because the radius was too big for the arms.
            if (areParallel(first.second, second.second)) {
                EditRefusal.NO_CORNER
            } else {
                EditRefusal.DOES_NOT_FIT
            },
        )

    // A rounded corner between two walls is a piece of curved wall, as thick as
    // the thinner of the two. Drawn as a pencil line it left the corner open
    // with a hairline across the gap, which is what it looked like on the
    // device and is not what "round this corner" means.
    val arc = DrawnShape.Arc(
        id = firstId + "-yay",
        centre = Vec3(rounded.centre.x, rounded.centre.y, 0.0),
        radiusMm = rounded.radiusMm,
        startDegrees = rounded.startDegrees,
        endDegrees = rounded.endDegrees,
        layer = first.first.layer,
        thicknessMm = bandThickness(first.first, second.first),
    )

    return EditOutcome.Done(
        withLines(firstId to rounded.first, secondId to rounded.second) + arc,
    )
}

/**
 * How thick the piece between two filleted or chamfered shapes should be.
 *
 * Zero unless both are walls: a rounded corner on two pencil lines is a pencil
 * line, and guessing a thickness for it would put a wall where the user drew
 * linework. Between two walls of different thicknesses it is the thinner, so
 * the new piece never sticks out past either face.
 */
private fun bandThickness(first: DrawnShape, second: DrawnShape): Double {
    val a = first as? DrawnShape.Wall ?: return 0.0
    val b = second as? DrawnShape.Wall ?: return 0.0
    return kotlin.math.min(a.thicknessMm, b.thicknessMm)
}

/**
 * The corner between two shapes cut off straight, [distanceMm] back along each.
 *
 * The flat that joins the two cuts is drawn as a line on the first shape's
 * layer — a chamfered wall corner is a wall face, not a new wall, and it is
 * left as linework rather than guessing at a thickness for it.
 */
public fun List<DrawnShape>.chamfered(
    firstId: String,
    secondId: String,
    distanceMm: Double,
): EditOutcome {
    val first = shapeAndLine(firstId) ?: return refuse(EditRefusal.NOT_A_LINE)
    val second = shapeAndLine(secondId) ?: return refuse(EditRefusal.NOT_A_LINE)
    if (firstId == secondId) return refuse(EditRefusal.NO_CORNER)

    val cut = chamfer(first.second, second.second, distanceMm, distanceMm)
        ?: return refuse(
            if (areParallel(first.second, second.second)) {
                EditRefusal.NO_CORNER
            } else {
                EditRefusal.DOES_NOT_FIT
            },
        )

    // Between two walls the flat is a short wall, not a line across a gap: it is
    // built, it has two faces, and a plan that draws it as a pencil mark is
    // drawing a hole in the building.
    val thickness = bandThickness(first.first, second.first)
    val corner = if (thickness > 0.0) {
        val wall = first.first as DrawnShape.Wall
        DrawnShape.Wall(
            id = firstId + "-pah",
            a = Vec3(cut.bridge.a.x, cut.bridge.a.y, 0.0),
            b = Vec3(cut.bridge.b.x, cut.bridge.b.y, 0.0),
            thicknessMm = thickness,
            heightMm = wall.heightMm,
            material = wall.material,
            layer = wall.layer,
        )
    } else {
        DrawnShape.Line(
            id = firstId + "-pah",
            a = Vec3(cut.bridge.a.x, cut.bridge.a.y, 0.0),
            b = Vec3(cut.bridge.b.x, cut.bridge.b.y, 0.0),
            layer = first.first.layer,
        )
    }

    return EditOutcome.Done(
        withLines(firstId to cut.first, secondId to cut.second) + corner,
    )
}

/**
 * [targetId] cut back at [boundaryId], losing the piece [at] is on.
 *
 * Point at the part you want gone, which is how everybody thinks about trim and
 * how AutoCAD has always done it.
 */
public fun List<DrawnShape>.trimmed(
    targetId: String,
    boundaryId: String,
    at: Vec2,
): EditOutcome {
    val target = shapeAndLine(targetId) ?: return refuse(EditRefusal.NOT_A_LINE)
    val boundary = shapeAndLine(boundaryId) ?: return refuse(EditRefusal.NOT_A_LINE)
    if (targetId == boundaryId) return refuse(EditRefusal.DOES_NOT_CROSS)

    val line = target.second
    val along = line.b - line.a
    if (along.lengthSquared < 1e-9) return refuse(EditRefusal.NOT_A_LINE)

    // The far end from whatever was pointed at is the end that survives, so the
    // piece under the finger is the piece that goes.
    val atPointed = ((at - line.a) dot along) / along.lengthSquared
    val keep = if (atPointed < 0.5) line.b else line.a

    val cut = trim(line, boundary.second, keep) ?: return refuse(EditRefusal.DOES_NOT_CROSS)
    return EditOutcome.Done(withLines(targetId to cut))
}

/** [targetId] stretched until it reaches [boundaryId]. */
public fun List<DrawnShape>.extended(targetId: String, boundaryId: String): EditOutcome {
    val target = shapeAndLine(targetId) ?: return refuse(EditRefusal.NOT_A_LINE)
    val boundary = shapeAndLine(boundaryId) ?: return refuse(EditRefusal.NOT_A_LINE)
    if (targetId == boundaryId) return refuse(EditRefusal.ALREADY_REACHES)

    val stretched = extend(target.second, boundary.second)
        ?: return refuse(
            if (areParallel(target.second, boundary.second)) {
                EditRefusal.NO_CORNER
            } else {
                EditRefusal.ALREADY_REACHES
            },
        )
    return EditOutcome.Done(withLines(targetId to stretched))
}


/**
 * A reflected copy of [targetId], across [axisId].
 *
 * The original stays. That is the point of mirroring on a plan: half a flat is
 * drawn, a centre wall is pointed at, and the other half appears — which is
 * only useful if the half already drawn is still there.
 *
 * The axis is a wall or a line, because an axis you pick is easier to be sure
 * of than an axis you type, and on a plan the thing you want to reflect about
 * is nearly always already drawn.
 */
public fun List<DrawnShape>.mirroredAcross(
    targetId: String,
    axisId: String,
    newId: String,
): EditOutcome {
    if (targetId == axisId) return refuse(EditRefusal.CANNOT_MIRROR)
    val target = firstOrNull { it.id == targetId } ?: return refuse(EditRefusal.CANNOT_MIRROR)
    val axis = shapeAndLine(axisId) ?: return refuse(EditRefusal.NOT_A_LINE)

    val through = axis.second.a
    val direction = axis.second.b - axis.second.a
    if (direction.length < EPSILON_MM) return refuse(EditRefusal.NOT_A_LINE)

    fun flip(point: Vec3): Vec3 {
        val reflected = mirrored(listOf(Vec2(point.x, point.y)), through, direction).first()
        return Vec3(reflected.x, reflected.y, point.z)
    }

    val copy: DrawnShape = when (target) {
        is DrawnShape.Wall -> target.copy(id = newId, a = flip(target.a), b = flip(target.b))
        is DrawnShape.Line -> target.copy(id = newId, a = flip(target.a), b = flip(target.b))
        is DrawnShape.Circle -> target.copy(id = newId, centre = flip(target.centre))

        // Reflecting a curve turns it round: what ran anticlockwise from one
        // angle to another now runs anticlockwise from the reflection of the
        // second to the reflection of the first.
        is DrawnShape.Arc -> {
            val ends = target.centreLinePoints()
            val start = mirrored(listOf(ends.first()), through, direction).first()
            val end = mirrored(listOf(ends.last()), through, direction).first()
            val centre = flip(target.centre)
            target.copy(
                id = newId,
                centre = centre,
                startDegrees = angleAt(Vec2(centre.x, centre.y), end),
                endDegrees = angleAt(Vec2(centre.x, centre.y), start),
            )
        }

        // A rectangle is held as two opposite corners with its sides square to
        // the sheet, so it cannot be written down at an angle — reflecting it
        // about a sloping wall would quietly straighten it, which is a wrong
        // answer rather than a refusal.
        else -> return refuse(EditRefusal.CANNOT_MIRROR)
    }

    return EditOutcome.Done(this + copy)
}

/** The angle from [centre] to [point], in degrees anticlockwise from east. */
private fun angleAt(centre: Vec2, point: Vec2): Double {
    val degrees = Math.toDegrees(kotlin.math.atan2(point.y - centre.y, point.x - centre.x))
    return if (degrees < 0.0) degrees + 360.0 else degrees
}

/**
 * Two shapes on the same straight line, made into one.
 *
 * A wall cut in half by an earlier trim, or two runs drawn in two goes, are one
 * wall as far as the building is concerned and should be one wall on the plan
 * too: one length to type into, one thing to select, one thing to hide.
 *
 * The first one is the one that survives — it keeps its id, its thickness and
 * its material — and the second one's doors and windows come across onto it,
 * because a door does not stop existing when the wall it is in is renamed.
 */
public fun List<DrawnShape>.joined(firstId: String, secondId: String): EditOutcome {
    if (firstId == secondId) return refuse(EditRefusal.DO_NOT_TOUCH)
    val first = shapeAndLine(firstId) ?: return refuse(EditRefusal.NOT_A_LINE)
    val second = shapeAndLine(secondId) ?: return refuse(EditRefusal.NOT_A_LINE)

    val alongFirst = first.second.b - first.second.a
    val alongSecond = second.second.b - second.second.a
    if (alongFirst.length < EPSILON_MM || alongSecond.length < EPSILON_MM) {
        return refuse(EditRefusal.NOT_IN_LINE)
    }

    // Parallel is not enough: both ends of the second have to lie on the first
    // one's line, or the two are two walls of a corridor rather than one wall.
    val unit = alongFirst / alongFirst.length
    val sideways = Vec2(-unit.y, unit.x)
    val off = listOf(second.second.a, second.second.b)
        .maxOf { kotlin.math.abs((it - first.second.a) dot sideways) }
    if (off > JOIN_TOLERANCE_MM) return refuse(EditRefusal.NOT_IN_LINE)

    // Measured along the shared line, the two must overlap or touch; a gap
    // between them would be filled in with wall that was never drawn.
    val ends = listOf(first.second.a, first.second.b, second.second.a, second.second.b)
        .map { (it - first.second.a) dot unit }
    val firstSpan = minOf(ends[0], ends[1])..maxOf(ends[0], ends[1])
    val secondSpan = minOf(ends[2], ends[3])..maxOf(ends[2], ends[3])
    val gap = maxOf(firstSpan.start, secondSpan.start) -
        minOf(firstSpan.endInclusive, secondSpan.endInclusive)
    if (gap > JOIN_TOLERANCE_MM) return refuse(EditRefusal.DO_NOT_TOUCH)

    val from = minOf(firstSpan.start, secondSpan.start)
    val to = maxOf(firstSpan.endInclusive, secondSpan.endInclusive)
    val whole = Segment2(first.second.a + unit * from, first.second.a + unit * to)

    // The second one's openings are re-measured onto the survivor before it is
    // dropped, since they were counted from an end that is about to go.
    val carried = filterIsInstance<DrawnShape.Opening>()
        .filter { it.wallId == secondId }
        .map { opening ->
            val on = second.second.a + (second.second.b - second.second.a).let {
                it / it.length * opening.alongMm
            }
            opening.copy(wallId = firstId, alongMm = (on - whole.a) dot unit)
        }

    val joinedList = withLines(firstId to whole)
        .filterNot { it.id == secondId }
        .map { shape -> carried.firstOrNull { it.id == shape.id } ?: shape }

    return EditOutcome.Done(joinedList)
}

/** True when the two shapes the tool needs are both things it can work on. */
public fun DrawnShape.isLine(): Boolean =
    this is DrawnShape.Wall || this is DrawnShape.Line

private fun refuse(reason: EditRefusal): EditOutcome = EditOutcome.Refused(reason)

private fun List<DrawnShape>.shapeAndLine(id: String): Pair<DrawnShape, Segment2>? {
    val shape = firstOrNull { it.id == id } ?: return null
    return when (shape) {
        is DrawnShape.Wall -> shape to shape.centreLine()
        is DrawnShape.Line -> shape to Segment2(shape.a.toVec2(), shape.b.toVec2())
        else -> null
    }
}

/**
 * This list with some shapes' ends moved, and their doors and windows left
 * where they are on the sheet.
 *
 * An opening does not hold a place on the sheet: it holds how far it is from
 * its wall's start. That is what keeps a door in its doorway when the wall is
 * dragged, and it is exactly what goes wrong when the start itself moves —
 * cutting 300mm off the back of a wall slid every door in it 300mm along,
 * which is what the device showed. So whenever a wall's start moves, every
 * opening in it is re-measured from the new start.
 */
private fun List<DrawnShape>.withLines(vararg moved: Pair<String, Segment2>): List<DrawnShape> {
    val lines = moved.toMap()
    val slid = mutableMapOf<String, Double>()
    val lengths = mutableMapOf<String, Double>()

    for ((id, line) in lines) {
        val wall = firstOrNull { it.id == id } as? DrawnShape.Wall ?: continue
        val before = wall.centreLine()
        val along = before.b - before.a
        val length = along.length
        if (length < EPSILON_MM) continue
        val unit = along / length
        // How far the start end travelled along the wall's own direction. The
        // two-shape edits only ever slide an end along the line it is on, so
        // this is the whole of the change an opening can feel.
        slid[id] = (line.a - before.a) dot unit
        lengths[id] = line.length
    }

    return map { shape ->
        when {
            shape.id in lines -> shape.withLine(lines.getValue(shape.id))

            shape is DrawnShape.Opening && shape.wallId in slid -> {
                val half = shape.widthMm / 2.0
                val length = lengths.getValue(shape.wallId)
                val limit = (length - half).coerceAtLeast(half)
                shape.copy(
                    alongMm = (shape.alongMm - slid.getValue(shape.wallId))
                        .coerceIn(half.coerceAtMost(limit), limit),
                )
            }

            else -> shape
        }
    }
}

/** This shape with its two ends moved, keeping everything else about it. */
private fun DrawnShape.withLine(line: Segment2): DrawnShape = when (this) {
    is DrawnShape.Wall -> copy(
        a = Vec3(line.a.x, line.a.y, a.z),
        b = Vec3(line.b.x, line.b.y, b.z),
    )

    is DrawnShape.Line -> copy(
        a = Vec3(line.a.x, line.a.y, a.z),
        b = Vec3(line.b.x, line.b.y, b.z),
    )

    else -> this
}

private fun areParallel(first: Segment2, second: Segment2): Boolean {
    val a = first.b - first.a
    val b = second.b - second.a
    if (a.length < 1e-9 || b.length < 1e-9) return true
    return kotlin.math.abs(a.normalized() cross b.normalized()) < 1e-9
}
