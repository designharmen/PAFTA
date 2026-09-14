package com.harmen.pafta.project

import com.harmen.pafta.geometry.Segment2
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import com.harmen.pafta.geometry.chamfer
import com.harmen.pafta.geometry.extend
import com.harmen.pafta.geometry.fillet
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

    val arc = DrawnShape.Arc(
        id = firstId + "-yay",
        centre = Vec3(rounded.centre.x, rounded.centre.y, 0.0),
        radiusMm = rounded.radiusMm,
        startDegrees = rounded.startDegrees,
        endDegrees = rounded.endDegrees,
        layer = first.first.layer,
    )

    return EditOutcome.Done(
        map {
            when (it.id) {
                firstId -> it.withLine(rounded.first)
                secondId -> it.withLine(rounded.second)
                else -> it
            }
        } + arc,
    )
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

    val bridge = DrawnShape.Line(
        id = firstId + "-pah",
        a = Vec3(cut.bridge.a.x, cut.bridge.a.y, 0.0),
        b = Vec3(cut.bridge.b.x, cut.bridge.b.y, 0.0),
        layer = first.first.layer,
    )

    return EditOutcome.Done(
        map {
            when (it.id) {
                firstId -> it.withLine(cut.first)
                secondId -> it.withLine(cut.second)
                else -> it
            }
        } + bridge,
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
    return EditOutcome.Done(map { if (it.id == targetId) it.withLine(cut) else it })
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
    return EditOutcome.Done(map { if (it.id == targetId) it.withLine(stretched) else it })
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
