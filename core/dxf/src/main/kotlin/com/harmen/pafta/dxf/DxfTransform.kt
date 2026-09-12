package com.harmen.pafta.dxf

import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where a block's geometry lands when the block is referenced.
 *
 * A DXF block is defined in its own coordinate space around a base point; an
 * `INSERT` places it by subtracting that base point, then scaling, rotating and
 * translating. Keeping the whole placement in one value means an entity inside
 * a block inside a block is just two applications of the same thing.
 */
public data class BlockTransform(
    val base: Vec2 = Vec2.ZERO,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val rotationDegrees: Double = 0.0,
    val translate: Vec2 = Vec2.ZERO,
) {
    private val cosR: Double = cos(Math.toRadians(rotationDegrees))
    private val sinR: Double = sin(Math.toRadians(rotationDegrees))

    /** Block space -> the space the reference sits in. */
    public fun apply(p: Vec2): Vec2 {
        val dx = (p.x - base.x) * scaleX
        val dy = (p.y - base.y) * scaleY
        return Vec2(
            translate.x + dx * cosR - dy * sinR,
            translate.y + dx * sinR + dy * cosR,
        )
    }

    /** Z is carried through untouched: the placement here is a plan transform. */
    public fun apply(p: Vec3): Vec3 {
        val q = apply(p.toVec2())
        return Vec3(q.x, q.y, p.z)
    }

    /** A negative scale on exactly one axis mirrors the geometry. */
    public val mirrored: Boolean get() = scaleX * scaleY < 0.0

    /**
     * The single factor to scale a length by.
     *
     * A non-uniform scale turns a circle into an ellipse, which this reader does
     * not model; the geometric mean is used instead, because it keeps the area
     * right and degrades to the exact factor whenever the scale is uniform.
     */
    public val lengthFactor: Double get() = sqrt(abs(scaleX * scaleY))
}

/**
 * The same entity, placed by [t].
 *
 * [layerOverride] and [colourOverride] implement the DXF inheritance rules:
 * geometry drawn on layer `0` inside a block takes the layer of the reference,
 * and colour `BYBLOCK` takes the reference's colour. Without this, every
 * expanded door and window would land on layer `0` and the layer palette would
 * stop matching the drawing.
 */
internal fun DxfEntity.placedIn(
    t: BlockTransform,
    layerOverride: String?,
    colourOverride: Int?,
): DxfEntity {
    val newLayer = if (layer == BLOCK_INHERITED_LAYER && layerOverride != null) layerOverride else layer
    val newColour = if (colour == COLOUR_BY_BLOCK && colourOverride != null) colourOverride else colour

    return when (this) {
        is DxfEntity.Line -> copy(
            layer = newLayer,
            start = t.apply(start),
            end = t.apply(end),
            colour = newColour,
        )

        is DxfEntity.Point -> copy(
            layer = newLayer,
            position = t.apply(position),
            colour = newColour,
        )

        is DxfEntity.Polyline -> copy(
            layer = newLayer,
            vertices = vertices.map { t.apply(it) },
            colour = newColour,
        )

        is DxfEntity.Circle -> copy(
            layer = newLayer,
            centre = t.apply(centre),
            radius = radius * t.lengthFactor,
            colour = newColour,
        )

        is DxfEntity.Arc -> {
            // The arc is re-derived from its transformed end points rather than
            // by adding the rotation to the stored angles: that is the only way
            // a mirrored block (a door flipped to open the other way — the most
            // common mirrored thing in a plan) comes out the right way round.
            val centreT = t.apply(centre)
            val startPoint = t.apply(pointOnCircle(centre.toVec2(), radius, startAngleDegrees))
            val endPoint = t.apply(pointOnCircle(centre.toVec2(), radius, endAngleDegrees))
            val newStart = angleOf(centreT.toVec2(), startPoint)
            val newEnd = angleOf(centreT.toVec2(), endPoint)
            copy(
                layer = newLayer,
                centre = centreT,
                radius = radius * t.lengthFactor,
                // Mirroring reverses the sweep, so the ends swap to keep DXF's
                // counter-clockwise convention.
                startAngleDegrees = if (t.mirrored) newEnd else newStart,
                endAngleDegrees = if (t.mirrored) newStart else newEnd,
                colour = newColour,
            )
        }

        is DxfEntity.Text -> copy(
            layer = newLayer,
            position = t.apply(position),
            height = height * t.lengthFactor,
            rotationDegrees = rotationDegrees + t.rotationDegrees * signOrOne(t.scaleX * t.scaleY),
            colour = newColour,
        )

        is DxfEntity.Insert -> copy(
            layer = newLayer,
            position = t.apply(position),
            scale = Vec3(scale.x * t.scaleX, scale.y * t.scaleY, scale.z),
            rotationDegrees = rotationDegrees + t.rotationDegrees,
            colour = newColour,
        )
    }
}

/** Layer `0` inside a block means "whatever layer the reference is on". */
internal const val BLOCK_INHERITED_LAYER: String = "0"

/** AutoCAD colour index 0 means "whatever colour the reference is". */
public const val COLOUR_BY_BLOCK: Int = 0

private fun pointOnCircle(centre: Vec2, radius: Double, angleDegrees: Double): Vec2 {
    val r = Math.toRadians(angleDegrees)
    return Vec2(centre.x + cos(r) * radius, centre.y + sin(r) * radius)
}

private fun angleOf(centre: Vec2, point: Vec2): Double {
    val d = point - centre
    if (hypot(d.x, d.y) == 0.0) return 0.0
    val degrees = Math.toDegrees(atan2(d.y, d.x))
    return if (degrees < 0.0) degrees + 360.0 else degrees
}

private fun signOrOne(v: Double): Double = if (v == 0.0) 1.0 else sign(v)

/** A block definition: the base point it is drawn around, and its geometry. */
internal data class BlockDefinition(
    val base: Vec2,
    val entities: List<DxfEntity>,
)

/**
 * Replaces block references with the geometry they stand for.
 *
 * Three things bound the work, because a malformed or merely enormous file must
 * not be able to hang the app: a depth limit, a guard against a block that
 * refers to itself, and a total entity budget. Whenever one of them stops an
 * expansion the reference is kept as-is — a visible marker in the drawing — and
 * [truncated] records that the drawing is not complete, so the screen can say so
 * rather than quietly showing less than the file holds.
 */
internal class BlockExpansion(private val blocks: Map<String, BlockDefinition>) {

    val out: MutableList<DxfEntity> = ArrayList()

    var truncated: Boolean = false
        private set

    private val active = HashSet<String>()
    private var budget = MAX_EXPANDED_ENTITIES

    fun place(
        entities: List<DxfEntity>,
        transform: BlockTransform?,
        layerOverride: String?,
        colourOverride: Int?,
        depth: Int,
    ) {
        for (entity in entities) {
            val placed =
                if (transform == null) entity
                else entity.placedIn(transform, layerOverride, colourOverride)

            val block = (placed as? DxfEntity.Insert)?.let { blocks[it.blockName] }
            if (placed is DxfEntity.Insert && block != null) {
                val canExpand = budget > 0 &&
                    depth < MAX_BLOCK_DEPTH &&
                    active.add(placed.blockName)
                if (canExpand) {
                    place(
                        entities = block.entities,
                        transform = BlockTransform(
                            base = block.base,
                            scaleX = placed.scale.x,
                            scaleY = placed.scale.y,
                            rotationDegrees = placed.rotationDegrees,
                            translate = placed.position.toVec2(),
                        ),
                        layerOverride = placed.layer,
                        colourOverride = placed.colour,
                        depth = depth + 1,
                    )
                    active.remove(placed.blockName)
                    continue
                }
                truncated = true
            }

            out += placed
            budget--
        }
    }
}

/** A block nested deeper than this is a loop in all but name. */
private const val MAX_BLOCK_DEPTH = 16

/**
 * The ceiling on expanded entities.
 *
 * Expansion multiplies: one block of 500 entities placed 400 times is 200,000.
 * The limit is high enough for a real building plan and low enough that a
 * pathological file stops rather than taking the app down with it.
 */
private const val MAX_EXPANDED_ENTITIES = 200_000
