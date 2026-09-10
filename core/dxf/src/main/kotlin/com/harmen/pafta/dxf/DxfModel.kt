package com.harmen.pafta.dxf

import com.harmen.pafta.geometry.Aabb
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import com.harmen.pafta.geometry.tessellateArc
import com.harmen.pafta.geometry.tessellateCircle

/**
 * AutoCAD Colour Index. 256 means "by layer", 0 means "by block"; 1..255 are
 * the fixed palette slots. PAFTA renders its own dark-theme palette, so the
 * index is carried through for fidelity on export rather than used directly.
 */
public const val COLOUR_BY_LAYER: Int = 256

/** A layer as declared in the DXF `TABLES` section. */
public data class DxfLayer(
    val name: String,
    val colour: Int = 7,
    /** DXF flag bit 1 means frozen; PAFTA maps this to the layer's visibility. */
    val frozen: Boolean = false,
    val lineType: String = "CONTINUOUS",
) {
    public val visible: Boolean get() = !frozen && colour >= 0
}

/** Horizontal justification of a DXF `TEXT` entity (group code 72). */
public enum class DxfTextAlign { LEFT, CENTRE, RIGHT }

/**
 * A drawing entity.
 *
 * Coordinates are kept exactly as they appear in the file — DXF is unit-less,
 * and the drawing's unit is resolved separately from the `$INSUNITS` header.
 */
public sealed interface DxfEntity {
    public val layer: String
    public val colour: Int

    /** Points approximating the entity, used for bounds and for 2D drawing. */
    public fun outline(arcSegments: Int = 32): List<Vec2>

    public data class Line(
        override val layer: String,
        val start: Vec3,
        val end: Vec3,
        override val colour: Int = COLOUR_BY_LAYER,
    ) : DxfEntity {
        override fun outline(arcSegments: Int): List<Vec2> = listOf(start.toVec2(), end.toVec2())
    }

    public data class Point(
        override val layer: String,
        val position: Vec3,
        override val colour: Int = COLOUR_BY_LAYER,
    ) : DxfEntity {
        override fun outline(arcSegments: Int): List<Vec2> = listOf(position.toVec2())
    }

    /** `LWPOLYLINE`, and also the vertex-collected classic `POLYLINE`. */
    public data class Polyline(
        override val layer: String,
        val vertices: List<Vec2>,
        val closed: Boolean = false,
        override val colour: Int = COLOUR_BY_LAYER,
    ) : DxfEntity {
        override fun outline(arcSegments: Int): List<Vec2> =
            if (closed && vertices.size > 2) vertices + vertices.first() else vertices
    }

    public data class Circle(
        override val layer: String,
        val centre: Vec3,
        val radius: Double,
        override val colour: Int = COLOUR_BY_LAYER,
    ) : DxfEntity {
        override fun outline(arcSegments: Int): List<Vec2> {
            val ring = tessellateCircle(centre.toVec2(), radius, maxOf(3, arcSegments * 2))
            return ring + ring.first()
        }
    }

    /** Angles are degrees counter-clockwise from +X, as stored in DXF. */
    public data class Arc(
        override val layer: String,
        val centre: Vec3,
        val radius: Double,
        val startAngleDegrees: Double,
        val endAngleDegrees: Double,
        override val colour: Int = COLOUR_BY_LAYER,
    ) : DxfEntity {
        override fun outline(arcSegments: Int): List<Vec2> = tessellateArc(
            centre.toVec2(),
            radius,
            Math.toRadians(startAngleDegrees),
            Math.toRadians(endAngleDegrees),
            maxOf(1, arcSegments),
        )
    }

    public data class Text(
        override val layer: String,
        val position: Vec3,
        val height: Double,
        val value: String,
        val rotationDegrees: Double = 0.0,
        val align: DxfTextAlign = DxfTextAlign.LEFT,
        override val colour: Int = COLOUR_BY_LAYER,
    ) : DxfEntity {
        override fun outline(arcSegments: Int): List<Vec2> = listOf(position.toVec2())
    }

    /** A block reference. The referenced block's contents are not expanded. */
    public data class Insert(
        override val layer: String,
        val blockName: String,
        val position: Vec3,
        val scale: Vec3 = Vec3(1.0, 1.0, 1.0),
        val rotationDegrees: Double = 0.0,
        override val colour: Int = COLOUR_BY_LAYER,
    ) : DxfEntity {
        override fun outline(arcSegments: Int): List<Vec2> = listOf(position.toVec2())
    }
}

/** Unit codes from the DXF `$INSUNITS` header variable. */
public enum class DxfInsUnits(public val code: Int) {
    UNITLESS(0), INCHES(1), FEET(2), MILLIMETRES(4), CENTIMETRES(5), METRES(6),
    ;

    public companion object {
        public fun fromCode(code: Int): DxfInsUnits =
            entries.firstOrNull { it.code == code } ?: UNITLESS
    }
}

/** A parsed DXF drawing. */
public data class DxfDrawing(
    val layers: List<DxfLayer> = emptyList(),
    val entities: List<DxfEntity> = emptyList(),
    val insUnits: DxfInsUnits = DxfInsUnits.UNITLESS,
    /** Entity types seen in the file that this reader does not model yet. */
    val unsupportedEntityTypes: Set<String> = emptySet(),
) {
    /** Layer lookup, falling back to a synthetic default for unknown names. */
    public fun layer(name: String): DxfLayer =
        layers.firstOrNull { it.name == name } ?: DxfLayer(name)

    /** Bounds of every entity outline, in drawing units. */
    public val bounds: Aabb
        get() = entities
            .asSequence()
            .flatMap { it.outline().asSequence() }
            .fold(Aabb.EMPTY) { box, p -> box.encompass(Vec3(p.x, p.y, 0.0)) }

    public fun entitiesOnLayer(name: String): List<DxfEntity> = entities.filter { it.layer == name }
}
