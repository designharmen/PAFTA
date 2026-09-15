package com.harmen.pafta.project

import com.harmen.pafta.dxf.DxfEntity
import com.harmen.pafta.geometry.Vec2
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin

/**
 * The library: the things that are drawn the same way every time.
 *
 * A bed is 1600 x 2000 with a headboard, a WC is a shape everybody recognises,
 * a sink is a rectangle with a circle in it. None of them is worth drawing by
 * hand twice, and every one of them is worth drawing the *same* twice — which
 * is what a library is for.
 *
 * Each piece is held as plain geometry in millimetres about its own middle, so
 * placing one is a move and a turn and nothing else. The geometry lives in code
 * rather than in the project file: a project stores *which* piece it used, not
 * a copy of it, so improving how a WC is drawn improves it in every project
 * ever saved rather than only in the ones drawn afterwards.
 */

/** Which part of the library a piece belongs to. */
@Serializable
public enum class BlockGroup { SEATING, DINING, BEDROOM, KITCHEN, BATHROOM }

/**
 * One piece of the library.
 *
 * [widthMm] and [depthMm] are the size it is drawn at by default; an instance
 * may be placed at another size, which is what makes one "masa" cover a table
 * for four and a table for eight.
 */
@Serializable
public enum class CatalogueBlock(
    public val group: BlockGroup,
    public val widthMm: Double,
    public val depthMm: Double,
) {
    // --- oturma ---
    ARMCHAIR(BlockGroup.SEATING, 800.0, 800.0),
    SOFA_TWO(BlockGroup.SEATING, 1600.0, 850.0),
    SOFA_THREE(BlockGroup.SEATING, 2100.0, 850.0),
    COFFEE_TABLE(BlockGroup.SEATING, 1100.0, 600.0),
    TV_UNIT(BlockGroup.SEATING, 1800.0, 400.0),

    // --- yemek ---
    DINING_TABLE_FOUR(BlockGroup.DINING, 1200.0, 800.0),
    DINING_TABLE_SIX(BlockGroup.DINING, 1800.0, 900.0),
    DINING_TABLE_ROUND(BlockGroup.DINING, 1200.0, 1200.0),
    CHAIR(BlockGroup.DINING, 450.0, 450.0),

    // --- yatak odası ---
    BED_SINGLE(BlockGroup.BEDROOM, 1000.0, 2000.0),
    BED_DOUBLE(BlockGroup.BEDROOM, 1600.0, 2000.0),
    WARDROBE(BlockGroup.BEDROOM, 1800.0, 600.0),
    BEDSIDE(BlockGroup.BEDROOM, 450.0, 400.0),
    DESK(BlockGroup.BEDROOM, 1200.0, 600.0),

    // --- mutfak ---
    COUNTER(BlockGroup.KITCHEN, 1200.0, 600.0),
    SINK(BlockGroup.KITCHEN, 800.0, 600.0),
    HOB(BlockGroup.KITCHEN, 600.0, 600.0),
    FRIDGE(BlockGroup.KITCHEN, 700.0, 700.0),

    // --- banyo ---
    WC(BlockGroup.BATHROOM, 400.0, 700.0),
    BASIN(BlockGroup.BATHROOM, 600.0, 450.0),
    SHOWER(BlockGroup.BATHROOM, 900.0, 900.0),
    BATH(BlockGroup.BATHROOM, 1700.0, 750.0),
    ;

    /**
     * How the piece is drawn, about its own middle, at its own size.
     *
     * Plain rectangles and circles: a plan is read at 1:50 and a sofa with its
     * cushions drawn in is a grey smudge. What has to be right is the size it
     * takes up and whether you can tell at a glance what it is.
     */
    public fun outlines(): List<List<Vec2>> {
        val w = widthMm / 2.0
        val d = depthMm / 2.0
        fun box(left: Double, bottom: Double, right: Double, top: Double) =
            listOf(Vec2(left, bottom), Vec2(right, bottom), Vec2(right, top), Vec2(left, top))

        val body = box(-w, -d, w, d)

        return when (this) {
            // A seat is a box with its back drawn thicker along one side and
            // its arms down the two others.
            ARMCHAIR, SOFA_TWO, SOFA_THREE -> listOf(
                body,
                box(-w, d - 200.0, w, d),
                box(-w, -d, -w + 150.0, d - 200.0),
                box(w - 150.0, -d, w, d - 200.0),
            )

            COFFEE_TABLE, TV_UNIT, WARDROBE, BEDSIDE, DESK, COUNTER -> listOf(body)

            DINING_TABLE_FOUR, DINING_TABLE_SIX -> listOf(body)

            DINING_TABLE_ROUND -> listOf(circle(Vec2.ZERO, w))

            CHAIR -> listOf(body, box(-w, d - 80.0, w, d))

            // A bed: the mattress, and the pillows across the head of it.
            BED_SINGLE, BED_DOUBLE -> listOf(
                body,
                box(-w + 60.0, d - 450.0, w - 60.0, d - 100.0),
                listOf(Vec2(-w, d - 450.0), Vec2(w, d - 450.0)),
            )

            // A sink: the bowl inside the counter, and the tap behind it.
            SINK -> listOf(
                body,
                box(-w + 100.0, -d + 100.0, w - 100.0, d - 180.0),
                listOf(Vec2(0.0, d - 150.0), Vec2(0.0, d - 60.0)),
            )

            // A hob: four rings.
            HOB -> listOf(
                body,
                circle(Vec2(-w / 2.0, d / 2.0), 110.0),
                circle(Vec2(w / 2.0, d / 2.0), 110.0),
                circle(Vec2(-w / 2.0, -d / 2.0), 90.0),
                circle(Vec2(w / 2.0, -d / 2.0), 90.0),
            )

            FRIDGE -> listOf(body, listOf(Vec2(-w, -d), Vec2(w, d)))

            // A WC: the cistern across the back, the pan in front of it. The
            // pan reaches the front of the 700mm the fitting actually takes up,
            // which is what the plan has to leave room for.
            WC -> {
                val pan = w * 0.85
                listOf(
                    box(-w, d - 200.0, w, d),
                    circle(Vec2(0.0, -d + pan), pan),
                    box(-w * 0.5, -d + pan, w * 0.5, d - 200.0),
                )
            }

            BASIN -> listOf(body, circle(Vec2(0.0, -30.0), w * 0.62))

            // A shower tray: the drain, and the diagonals that say "tray".
            SHOWER -> listOf(
                body,
                circle(Vec2.ZERO, 60.0),
                listOf(Vec2(-w, -d), Vec2(w, d)),
                listOf(Vec2(-w, d), Vec2(w, -d)),
            )

            BATH -> listOf(
                body,
                box(-w + 80.0, -d + 80.0, w - 250.0, d - 80.0),
                circle(Vec2(w - 150.0, 0.0), 45.0),
            )
        }
    }
}

/** A circle as a polygon, since everything in the library is drawn as outlines. */
private fun circle(centre: Vec2, radius: Double, sides: Int = 32): List<Vec2> =
    (0 until sides).map {
        val radians = 2.0 * Math.PI * it / sides
        Vec2(centre.x + radius * cos(radians), centre.y + radius * sin(radians))
    }

/**
 * The library piece [block], placed, turned and sized, as entities on [layer].
 *
 * The one place a catalogue drawing becomes geometry on the sheet. Everything
 * else — the renderer, the exporter, the entity count, picking — sees ordinary
 * closed polylines and needs to know nothing about libraries.
 */
internal fun placedOutlines(
    block: CatalogueBlock,
    at: Vec2,
    rotationDegrees: Double,
    widthMm: Double,
    depthMm: Double,
): List<List<Vec2>> {
    val scaleX = if (block.widthMm < EPSILON_MM) 1.0 else widthMm / block.widthMm
    val scaleY = if (block.depthMm < EPSILON_MM) 1.0 else depthMm / block.depthMm
    val radians = Math.toRadians(rotationDegrees)
    val c = cos(radians)
    val s = sin(radians)

    return block.outlines().map { outline ->
        outline.map {
            val x = it.x * scaleX
            val y = it.y * scaleY
            Vec2(at.x + x * c - y * s, at.y + x * s + y * c)
        }
    }
}

/** The entities a placed block is drawn and exported as. */
internal fun placedEntities(
    block: CatalogueBlock,
    layer: String,
    at: Vec2,
    rotationDegrees: Double,
    widthMm: Double,
    depthMm: Double,
): List<DxfEntity> = placedOutlines(block, at, rotationDegrees, widthMm, depthMm)
    .filter { it.size >= 2 }
    .map { DxfEntity.Polyline(layer, it, closed = it.size > 2) }
