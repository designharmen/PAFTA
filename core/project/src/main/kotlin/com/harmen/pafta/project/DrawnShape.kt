package com.harmen.pafta.project

import com.harmen.pafta.dxf.DxfEntity
import com.harmen.pafta.geometry.Segment2
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Something the user drew, rather than something that came out of a file.
 *
 * PAFTA keeps these separate from the imported payload on purpose: the file the
 * architect brought in is never rewritten, so nothing they draw can damage the
 * original. The two are merged only when the drawing is displayed or exported.
 *
 * Every shape carries its own geometry in drawing millimetres and turns itself
 * into ordinary DXF entities, so the renderer, the snapping and the exporter all
 * keep working on one kind of thing.
 */
/**
 * What a wall is built of.
 *
 * The code is ASCII because it becomes part of a layer name, and layer names
 * travel into exported DXF files whose encoding cannot carry `Ç`, `İ` or `Ş`.
 * The Turkish name the user reads is composed at the interface, never here.
 */
@Serializable
public enum class WallMaterial(public val code: String) {
    BRICK("TUGLA"),
    CONCRETE("BETON"),
    AERATED("GAZBETON"),
    TIMBER("AHSAP"),
}

/** What an opening in a wall is. */
@Serializable
public enum class OpeningKind { DOOR, WINDOW }

/**
 * Which jamb a door is hinged on, and which side of the wall it opens to.
 *
 * Four choices rather than two settings, because this is the one property of a
 * door everybody draws by pointing at it, and a screen with two dropdowns for
 * something you could point at is the kind of thing this project is against.
 */
@Serializable
public enum class DoorSwing { LEFT_IN, LEFT_OUT, RIGHT_IN, RIGHT_OUT }

@Serializable
public sealed interface DrawnShape {
    public val id: String
    public val layer: String

    /** The DXF entities this shape is drawn and exported as. */
    public fun toEntities(): List<DxfEntity>

    /** The same shape moved by a drawing-millimetre offset. */
    public fun translated(dx: Double, dy: Double): DrawnShape

    /**
     * A wall: a centre line with a real thickness.
     *
     * Drawn as the closed outline of its two faces. Where two walls meet, their
     * outlines simply overlap — tidying that corner is the `join` tool of a
     * later phase, and pretending to do it now would produce a wrong corner
     * rather than an honest overlap.
     */
    @Serializable
    @SerialName("duvar")
    public data class Wall(
        override val id: String,
        @Serializable(with = Vec3Serializer::class) val a: Vec3,
        @Serializable(with = Vec3Serializer::class) val b: Vec3,
        val thicknessMm: Double = DEFAULT_WALL_THICKNESS_MM,
        /**
         * Floor to ceiling, in millimetres.
         *
         * Nothing on the plan shows it yet — a plan is a horizontal cut and a
         * wall's height does not appear in one. It is carried now because the
         * wall is the same object in the 3D view and in the schedules, and a
         * height typed in today must still be there when those arrive.
         */
        val heightMm: Double = DEFAULT_WALL_HEIGHT_MM,
        val material: WallMaterial = WallMaterial.BRICK,
        override val layer: String = wallLayer(DEFAULT_WALL_THICKNESS_MM, WallMaterial.BRICK),
    ) : DrawnShape {

        /** The centre line, which is what another wall should meet end to end. */
        public fun centreLine(): Segment2 = Segment2(a.toVec2(), b.toVec2())

        /** The four corners of the wall's outline, anticlockwise from `a`. */
        public fun outline(): List<Vec2> {
            val start = a.toVec2()
            val end = b.toVec2()
            val along = end - start
            val length = hypot(along.x, along.y)
            if (length < EPSILON_MM) return emptyList()

            // The perpendicular, half a thickness long, on each side.
            val half = thicknessMm / 2.0
            val n = Vec2(-along.y / length * half, along.x / length * half)
            return listOf(start + n, end + n, end - n, start - n)
        }

        override fun toEntities(): List<DxfEntity> {
            val corners = outline()
            if (corners.size < 4) return emptyList()
            return listOf(DxfEntity.Polyline(layer, corners, closed = true))
        }

        override fun translated(dx: Double, dy: Double): DrawnShape = copy(
            a = Vec3(a.x + dx, a.y + dy, a.z),
            b = Vec3(b.x + dx, b.y + dy, b.z),
        )
    }

    /** A plain line, with no thickness. */
    @Serializable
    @SerialName("cizgi")
    public data class Line(
        override val id: String,
        @Serializable(with = Vec3Serializer::class) val a: Vec3,
        @Serializable(with = Vec3Serializer::class) val b: Vec3,
        override val layer: String = LAYER_DRAWING,
    ) : DrawnShape {
        override fun toEntities(): List<DxfEntity> = listOf(DxfEntity.Line(layer, a, b))

        override fun translated(dx: Double, dy: Double): DrawnShape = copy(
            a = Vec3(a.x + dx, a.y + dy, a.z),
            b = Vec3(b.x + dx, b.y + dy, b.z),
        )
    }

    /** A rectangle, given by two opposite corners. */
    @Serializable
    @SerialName("dikdortgen")
    public data class Rectangle(
        override val id: String,
        @Serializable(with = Vec3Serializer::class) val corner: Vec3,
        @Serializable(with = Vec3Serializer::class) val opposite: Vec3,
        override val layer: String = LAYER_DRAWING,
    ) : DrawnShape {

        public fun outline(): List<Vec2> = listOf(
            Vec2(corner.x, corner.y),
            Vec2(opposite.x, corner.y),
            Vec2(opposite.x, opposite.y),
            Vec2(corner.x, opposite.y),
        )

        override fun toEntities(): List<DxfEntity> {
            if (abs(opposite.x - corner.x) < EPSILON_MM || abs(opposite.y - corner.y) < EPSILON_MM) {
                return emptyList()
            }
            return listOf(DxfEntity.Polyline(layer, outline(), closed = true))
        }

        override fun translated(dx: Double, dy: Double): DrawnShape = copy(
            corner = Vec3(corner.x + dx, corner.y + dy, corner.z),
            opposite = Vec3(opposite.x + dx, opposite.y + dy, opposite.z),
        )
    }

    /** A circle, given by its centre and a radius in millimetres. */
    @Serializable
    @SerialName("daire")
    public data class Circle(
        override val id: String,
        @Serializable(with = Vec3Serializer::class) val centre: Vec3,
        val radiusMm: Double,
        override val layer: String = LAYER_DRAWING,
    ) : DrawnShape {
        override fun toEntities(): List<DxfEntity> =
            if (radiusMm < EPSILON_MM) emptyList()
            else listOf(DxfEntity.Circle(layer, centre, radiusMm))

        override fun translated(dx: Double, dy: Double): DrawnShape =
            copy(centre = Vec3(centre.x + dx, centre.y + dy, centre.z))
    }

    /**
     * A door or a window, in a wall.
     *
     * It does not carry its own position on the sheet: it carries which wall it
     * is in and how far along that wall it sits. That is what keeps a door in
     * its doorway when the wall is moved or made longer, which is the whole
     * reason an opening is an object rather than a hole drawn by hand.
     */
    @Serializable
    @SerialName("aciklik")
    public data class Opening(
        override val id: String,
        /** The [Wall] this is cut into. */
        val wallId: String,
        val kind: OpeningKind,
        /** From the wall's start, along its centre line, to the middle of the opening. */
        val alongMm: Double,
        val widthMm: Double = DEFAULT_DOOR_WIDTH_MM,
        val heightMm: Double = DEFAULT_DOOR_HEIGHT_MM,
        /** Height of the sill above the floor. A door sits on the floor: zero. */
        val sillMm: Double = 0.0,
        val swing: DoorSwing = DoorSwing.LEFT_IN,
        override val layer: String = LAYER_DOOR,
    ) : DrawnShape {

        /**
         * Nothing on its own.
         *
         * An opening is a shape in a wall, and it cannot draw itself without
         * knowing which wall: where its jambs are and which way its door swings
         * both come from the wall's line and thickness. `List<DrawnShape>`
         * resolves it — see `openingPlans`.
         */
        override fun toEntities(): List<DxfEntity> = emptyList()

        /** Moves with its wall, so moving it on its own would tear it out of one. */
        override fun translated(dx: Double, dy: Double): DrawnShape = this
    }

    /**
     * A room: named, and measured from the walls around it.
     *
     * It stores the point the user tapped, not the outline. The outline is
     * worked out from the walls every time it is needed, so moving a wall
     * changes the room and its area with it — which is what an architect
     * expects and what a stored outline could never do.
     */
    @Serializable
    @SerialName("mahal")
    public data class Zone(
        override val id: String,
        val name: String = "",
        /** Where the user tapped inside the room. */
        @Serializable(with = Vec3Serializer::class) val seed: Vec3,
        override val layer: String = LAYER_ZONE,
    ) : DrawnShape {

        /** Derived from the walls, like the outline. See `zonePlans`. */
        override fun toEntities(): List<DxfEntity> = emptyList()

        override fun translated(dx: Double, dy: Double): DrawnShape =
            copy(seed = Vec3(seed.x + dx, seed.y + dy, seed.z))
    }

    public companion object {
        /** A 20cm interior wall: the thickness most plans start from. */
        public const val DEFAULT_WALL_THICKNESS_MM: Double = 200.0

        /** A storey height, which is what a wall is unless it is told otherwise. */
        public const val DEFAULT_WALL_HEIGHT_MM: Double = 2800.0

        /** A room door. */
        public const val DEFAULT_DOOR_WIDTH_MM: Double = 900.0
        public const val DEFAULT_DOOR_HEIGHT_MM: Double = 2100.0

        /** A room window, and how high off the floor it starts. */
        public const val DEFAULT_WINDOW_WIDTH_MM: Double = 1200.0
        public const val DEFAULT_WINDOW_HEIGHT_MM: Double = 1400.0
        public const val DEFAULT_WINDOW_SILL_MM: Double = 900.0

        /**
         * Layer names for what PAFTA draws.
         *
         * ASCII on purpose: these names are written into exported DXF files,
         * whose text encoding cannot carry `Ç`, `İ` or `Ş` reliably, and a
         * mangled layer name in someone else's CAD program is worse than an
         * unaccented one.
         */
        public const val LAYER_WALL: String = "DUVAR"
        public const val LAYER_DRAWING: String = "CIZIM"
        public const val LAYER_DOOR: String = "KAPI"
        public const val LAYER_WINDOW: String = "PENCERE"
        public const val LAYER_ZONE: String = "MAHAL"

        /**
         * The layer a wall belongs on, e.g. `DUVAR-TUGLA-200`.
         *
         * Walls are separated by thickness and material rather than piled onto
         * one `DUVAR` layer, so the layer palette can show — and hide — every
         * 200mm brick wall as a group. That is what the palette is for, and it
         * costs nothing to do it at the moment the wall is drawn.
         */
        public fun wallLayer(thicknessMm: Double, material: WallMaterial): String =
            "$LAYER_WALL-${material.code}-${Math.round(thicknessMm)}"

        private const val EPSILON_MM = 1e-6
    }
}

/**
 * How long the shape is, in drawing millimetres, or null when length is not
 * what describes it.
 */
public val DrawnShape.lengthMm: Double?
    get() = when (this) {
        is DrawnShape.Wall -> a.toVec2().distanceTo(b.toVec2())
        is DrawnShape.Line -> a.toVec2().distanceTo(b.toVec2())
        is DrawnShape.Circle -> radiusMm * 2.0
        is DrawnShape.Opening -> widthMm
        is DrawnShape.Rectangle, is DrawnShape.Zone -> null
    }

/**
 * The same shape at an exact length, keeping its start and its direction.
 *
 * This is what makes a drawn wall usable: a finger cannot land on 3600mm, so
 * the wall is drawn roughly and then told what it is. The start point stays put
 * because it is usually already snapped to something that matters — a corner,
 * another wall — and moving it would break that join to fix the length.
 */
public fun DrawnShape.withLength(millimetres: Double): DrawnShape {
    if (millimetres <= 0.0) return this

    fun stretched(from: Vec3, to: Vec3): Vec3 {
        val direction = to.toVec2() - from.toVec2()
        val current = hypot(direction.x, direction.y)
        // A shape with no length has no direction to stretch along; leave it.
        if (current < 1e-6) return to
        val factor = millimetres / current
        return Vec3(
            from.x + direction.x * factor,
            from.y + direction.y * factor,
            to.z,
        )
    }

    return when (this) {
        is DrawnShape.Wall -> copy(b = stretched(a, b))
        is DrawnShape.Line -> copy(b = stretched(a, b))
        is DrawnShape.Circle -> copy(radiusMm = millimetres / 2.0)
        is DrawnShape.Opening -> copy(widthMm = millimetres)
        is DrawnShape.Rectangle, is DrawnShape.Zone -> this
    }
}

/**
 * A number on a shape that the user is allowed to retype.
 *
 * Drawing by hand gets the shape roughly right; these are how it is made exact.
 * Every shape says which of them it has, so the panel can offer those and only
 * those — a thickness field on a circle would be a control that does nothing.
 */
public enum class ShapeDimension {
    /** End to end, along the shape. */
    LENGTH,

    /** Across the shape: a wall's two faces. */
    THICKNESS,

    /** Floor to ceiling. */
    HEIGHT,

    /** A rectangle, left to right. */
    WIDTH,

    /** A rectangle, front to back. */
    DEPTH,

    /** A circle, edge to edge through the middle. */
    DIAMETER,

    /** How high above the floor a window starts. */
    SILL,
}

/**
 * The measurements of this shape the user may set, in drawing millimetres.
 *
 * Ordered, because the panel shows them in this order and the order is the one
 * an architect says them in: how long, how thick, how tall.
 */
public fun DrawnShape.dimensions(): Map<ShapeDimension, Double> = when (this) {
    is DrawnShape.Wall -> linkedMapOf(
        ShapeDimension.LENGTH to a.toVec2().distanceTo(b.toVec2()),
        ShapeDimension.THICKNESS to thicknessMm,
        ShapeDimension.HEIGHT to heightMm,
    )

    is DrawnShape.Line -> linkedMapOf(
        ShapeDimension.LENGTH to a.toVec2().distanceTo(b.toVec2()),
    )

    is DrawnShape.Rectangle -> linkedMapOf(
        ShapeDimension.WIDTH to abs(opposite.x - corner.x),
        ShapeDimension.DEPTH to abs(opposite.y - corner.y),
    )

    is DrawnShape.Circle -> linkedMapOf(ShapeDimension.DIAMETER to radiusMm * 2.0)

    // A door has no sill, so it is not offered one: floor level is not a
    // setting, it is where doors are.
    is DrawnShape.Opening -> when (kind) {
        OpeningKind.DOOR -> linkedMapOf(
            ShapeDimension.WIDTH to widthMm,
            ShapeDimension.HEIGHT to heightMm,
        )
        OpeningKind.WINDOW -> linkedMapOf(
            ShapeDimension.WIDTH to widthMm,
            ShapeDimension.HEIGHT to heightMm,
            ShapeDimension.SILL to sillMm,
        )
    }

    // A room is measured, not set: its size comes from the walls around it.
    is DrawnShape.Zone -> emptyMap()
}

/**
 * The same shape with one measurement set exactly.
 *
 * A measurement the shape does not have is ignored rather than refused: the
 * panel only offers the ones [dimensions] reports, so asking for another is a
 * mistake in the code, not something the user can do.
 *
 * Changing a wall's thickness moves it to the layer for that thickness, exactly
 * as drawing it at that thickness would have. Otherwise a 100mm wall would sit
 * on the 200mm layer for the rest of the project's life.
 */
public fun DrawnShape.withDimension(which: ShapeDimension, millimetres: Double): DrawnShape {
    if (millimetres <= 0.0) return this

    return when (this) {
        is DrawnShape.Wall -> when (which) {
            ShapeDimension.LENGTH -> withLength(millimetres)
            ShapeDimension.THICKNESS -> copy(
                thicknessMm = millimetres,
                layer = DrawnShape.wallLayer(millimetres, material),
            )
            ShapeDimension.HEIGHT -> copy(heightMm = millimetres)
            else -> this
        }

        is DrawnShape.Line ->
            if (which == ShapeDimension.LENGTH) withLength(millimetres) else this

        is DrawnShape.Rectangle -> {
            // The corner the user drew from stays put and the opposite one
            // moves, on the side it was already on — a rectangle made wider
            // must not turn itself inside out.
            val towardsX = if (opposite.x >= corner.x) 1.0 else -1.0
            val towardsY = if (opposite.y >= corner.y) 1.0 else -1.0
            when (which) {
                ShapeDimension.WIDTH ->
                    copy(opposite = Vec3(corner.x + towardsX * millimetres, opposite.y, opposite.z))
                ShapeDimension.DEPTH ->
                    copy(opposite = Vec3(opposite.x, corner.y + towardsY * millimetres, opposite.z))
                else -> this
            }
        }

        is DrawnShape.Circle ->
            if (which == ShapeDimension.DIAMETER) copy(radiusMm = millimetres / 2.0) else this

        is DrawnShape.Opening -> when (which) {
            ShapeDimension.WIDTH -> copy(widthMm = millimetres)
            ShapeDimension.HEIGHT -> copy(heightMm = millimetres)
            ShapeDimension.SILL -> copy(sillMm = millimetres)
            else -> this
        }

        is DrawnShape.Zone -> this
    }
}

/**
 * The same shape under a new id.
 *
 * What makes a copy a copy: everything about it is the same except the one
 * thing that says which shape it is. Used to duplicate a component so its
 * numbers can be changed without touching the one it came from.
 */
public fun DrawnShape.withId(id: String): DrawnShape = when (this) {
    is DrawnShape.Wall -> copy(id = id)
    is DrawnShape.Line -> copy(id = id)
    is DrawnShape.Rectangle -> copy(id = id)
    is DrawnShape.Circle -> copy(id = id)
    is DrawnShape.Opening -> copy(id = id)
    is DrawnShape.Zone -> copy(id = id)
}

/**
 * What another shape may snap to.
 *
 * A wall offers its **centre line**, not its outline. This is the difference
 * between two walls meeting and two walls almost meeting: the outline's corners
 * sit half a thickness off to the side, so snapping to them leaves every
 * junction 100mm out and no amount of care with the finger fixes it.
 */
public fun DrawnShape.snapSegments(): List<Segment2> = when (this) {
    is DrawnShape.Wall -> if (centreLine().length > 0.0) listOf(centreLine()) else emptyList()
    is DrawnShape.Line -> listOf(Segment2(a.toVec2(), b.toVec2()))
    is DrawnShape.Rectangle -> {
        val c = outline()
        c.indices.map { Segment2(c[it], c[(it + 1) % c.size]) }
    }
    // A circle has no straight edge to meet; its centre is offered instead, as
    // a segment of no length, which the snapper treats as a single point.
    is DrawnShape.Circle -> listOf(Segment2(centre.toVec2(), centre.toVec2()))
    // Neither is a thing to snap to: an opening is a hole in a wall that is
    // already offering its centre line, and a room is the space between them.
    is DrawnShape.Opening, is DrawnShape.Zone -> emptyList()
}

/**
 * A wall as it is actually drawn on the plan: a solid band, not an outline.
 *
 * Two walls whose centre lines meet at a corner still leave a square of nothing
 * between them, because each band stops at its own end. [wallBands] closes that
 * by reaching each end past the meeting point, far enough to cover the other
 * wall's far face — which is exactly half the other wall's thickness. Filled in,
 * the two bands then read as one solid corner, the way a wall is drawn on any
 * plan. Nothing is added where a wall ends free: that end stays square.
 *
 * This is only how a wall is drawn. The wall itself is still its centre line and
 * its thickness, which is what gets measured, saved and exported.
 */
public data class WallBand(
    val id: String,
    val layer: String,
    /** The four corners of the band, in order, ready to be filled. */
    val corners: List<Vec2>,
)

/** The walls among these shapes, as the bands they are drawn as. */
public fun List<DrawnShape>.wallBands(): List<WallBand> {
    val walls = filterIsInstance<DrawnShape.Wall>()
    if (walls.isEmpty()) return emptyList()

    /** Half the thickness of the thickest other wall that ends at [point]. */
    fun reachAt(self: DrawnShape.Wall, point: Vec2): Double {
        var reach = 0.0
        for (other in walls) {
            if (other.id == self.id) continue
            val meets = other.a.toVec2().distanceTo(point) <= JOIN_TOLERANCE_MM ||
                other.b.toVec2().distanceTo(point) <= JOIN_TOLERANCE_MM
            if (meets) reach = maxOf(reach, other.thicknessMm / 2.0)
        }
        return reach
    }

    return walls.mapNotNull { wall ->
        val start = wall.a.toVec2()
        val end = wall.b.toVec2()
        val along = end - start
        val length = hypot(along.x, along.y)
        if (length < 1e-6) return@mapNotNull null

        val unit = Vec2(along.x / length, along.y / length)
        val backwards = reachAt(wall, start)
        val forwards = reachAt(wall, end)
        val fromEnd = start - Vec2(unit.x * backwards, unit.y * backwards)
        val toEnd = end + Vec2(unit.x * forwards, unit.y * forwards)

        val half = wall.thicknessMm / 2.0
        val n = Vec2(-unit.y * half, unit.x * half)
        WallBand(
            id = wall.id,
            layer = wall.layer,
            corners = listOf(fromEnd + n, toEnd + n, toEnd - n, fromEnd - n),
        )
    }
}

/**
 * How close two wall ends must be to count as the same corner.
 *
 * A millimetre, because ends that were snapped together are identical and ends
 * that were not are out by far more than this. Loose enough to survive a
 * rounding, tight enough that two walls a finger apart are still two walls.
 */
internal const val JOIN_TOLERANCE_MM = 1.0

/**
 * The shape under a tap, or null.
 *
 * Later shapes win, because the one drawn most recently is the one on top and
 * the one the user is most likely to mean. A wall is picked anywhere on its
 * body rather than only on its outline — hitting a 200mm band is far easier
 * than hitting a 1px edge, and the thickness is what the user sees.
 */
public fun List<DrawnShape>.pick(at: Vec2, toleranceMm: Double): DrawnShape? {
    for (shape in asReversed()) {
        val hit = when (shape) {
            is DrawnShape.Wall -> {
                val centre = Segment2(shape.a.toVec2(), shape.b.toVec2())
                centre.closestPointTo(at).distanceTo(at) <= shape.thicknessMm / 2.0 + toleranceMm
            }

            is DrawnShape.Line ->
                Segment2(shape.a.toVec2(), shape.b.toVec2()).closestPointTo(at).distanceTo(at) <=
                    toleranceMm

            is DrawnShape.Rectangle -> {
                val corners = shape.outline()
                (corners.indices).any { i ->
                    val edge = Segment2(corners[i], corners[(i + 1) % corners.size])
                    edge.closestPointTo(at).distanceTo(at) <= toleranceMm
                }
            }

            is DrawnShape.Circle -> {
                val fromCentre = shape.centre.toVec2().distanceTo(at)
                abs(fromCentre - shape.radiusMm) <= toleranceMm
            }

            // Both are picked through what they are drawn from — an opening
            // through its wall, a room through its outline — which needs the
            // whole drawing, not one shape. `pickResolved` does that.
            is DrawnShape.Opening, is DrawnShape.Zone -> false
        }
        if (hit) return shape
    }
    return null
}
