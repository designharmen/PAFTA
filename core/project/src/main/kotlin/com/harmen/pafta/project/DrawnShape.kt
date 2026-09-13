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

    public companion object {
        /** A 20cm interior wall: the thickness most plans start from. */
        public const val DEFAULT_WALL_THICKNESS_MM: Double = 200.0

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
        is DrawnShape.Rectangle -> null
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
        is DrawnShape.Rectangle -> this
    }
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
}

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
        }
        if (hit) return shape
    }
    return null
}
