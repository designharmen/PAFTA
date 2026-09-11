package com.harmen.pafta.ui.viewport

import com.harmen.pafta.dxf.DxfDrawing
import com.harmen.pafta.dxf.DxfEntity
import com.harmen.pafta.dxf.DxfInsUnits
import com.harmen.pafta.dxf.DxfLayer
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import com.harmen.pafta.measure.Measurement
import com.harmen.pafta.R
import com.harmen.pafta.project.LayerState
import com.harmen.pafta.ui.state.MaterialSwatch
import com.harmen.pafta.ui.state.PropertyRow

/**
 * A worked apartment plan, in millimetres, used as the first-run drawing.
 *
 * It exists so that a fresh install opens onto something real rather than an
 * empty canvas: every panel, dimension and label in the design has something to
 * show, and it doubles as the fixture the viewport is eyeballed against.
 */
public object SamplePlan {

    private const val W = 9_600.0
    private const val H = 7_400.0

    /** Outer shell, internal partitions, door swings and window openings. */
    public val drawing: DxfDrawing = DxfDrawing(
        layers = listOf(
            DxfLayer("DUVAR", colour = 7),
            DxfLayer("BOLME", colour = 8),
            DxfLayer("ACIKLIK", colour = 4),
            DxfLayer("MOBILYA", colour = 3),
            DxfLayer("IZGARA", colour = 8),
        ),
        insUnits = DxfInsUnits.MILLIMETRES,
        entities = buildList {
            // --- Outer shell, 200mm thick, drawn as two offset rings ---------
            addAll(rectangle("DUVAR", 0.0, 0.0, W, H))
            addAll(rectangle("DUVAR", 200.0, 200.0, W - 200.0, H - 200.0))

            // --- Internal partitions, 120mm -----------------------------------
            // Kitchen / living divider.
            add(wall("BOLME", 3_400.0, 200.0, 3_400.0, 4_300.0))
            // Corridor wall.
            add(wall("BOLME", 200.0, 4_300.0, 6_900.0, 4_300.0))
            // Bath / bedroom divider.
            add(wall("BOLME", 6_900.0, 4_300.0, 6_900.0, H - 200.0))
            add(wall("BOLME", 3_400.0, 4_300.0, 3_400.0, H - 200.0))
            // Terrace threshold.
            add(wall("BOLME", 6_900.0, 200.0, 6_900.0, 4_300.0))

            // --- Door swings ---------------------------------------------------
            add(DxfEntity.Arc("ACIKLIK", Vec3(3_400.0, 1_100.0, 0.0), 800.0, 0.0, 90.0))
            add(DxfEntity.Arc("ACIKLIK", Vec3(4_600.0, 4_300.0, 0.0), 800.0, 180.0, 270.0))
            add(DxfEntity.Arc("ACIKLIK", Vec3(6_900.0, 5_400.0, 0.0), 750.0, 90.0, 180.0))

            // --- Window openings: a double line in the wall thickness ---------
            addAll(window("ACIKLIK", 900.0, 0.0, 2_100.0, 0.0))
            addAll(window("ACIKLIK", 4_300.0, 0.0, 6_100.0, 0.0))
            addAll(window("ACIKLIK", W, 1_200.0, W, 3_100.0))

            // --- A few furniture blocks, so the Furniture layer is not empty ---
            addAll(rectangle("MOBILYA", 600.0, 600.0, 2_800.0, 1_400.0)) // counter
            addAll(rectangle("MOBILYA", 4_000.0, 700.0, 6_200.0, 2_000.0)) // sofa
            addAll(rectangle("MOBILYA", 4_000.0, 4_900.0, 6_100.0, 6_900.0)) // bed
            add(DxfEntity.Circle("MOBILYA", Vec3(2_000.0, 5_600.0, 0.0), 450.0)) // basin
        },
    )

    /** Dimension strings along the outer walls, in the accent colour. */
    public val measurements: List<Measurement> = listOf(
        Measurement.Distance("d-bottom", Vec3(0.0, -700.0, 0.0), Vec3(5_500.0, -700.0, 0.0)),
        Measurement.Distance("d-bottom-2", Vec3(5_500.0, -700.0, 0.0), Vec3(W, -700.0, 0.0)),
        Measurement.Distance("d-left", Vec3(-700.0, 0.0, 0.0), Vec3(-700.0, 4_300.0, 0.0)),
        Measurement.Distance("d-left-2", Vec3(-700.0, 4_300.0, 0.0), Vec3(-700.0, H, 0.0)),
        Measurement.Distance("d-kitchen", Vec3(200.0, 3_900.0, 0.0), Vec3(3_400.0, 3_900.0, 0.0)),
    )

    /**
     * Room names, positioned at the visual centre of each space.
     *
     * Resolved from resources by the caller, so the fixture carries no Turkish
     * text of its own and cannot drift out of step with `strings.xml`.
     */
    public val roomLabelIds: List<Pair<Int, Vec2>> = listOf(
        R.string.sample_room_living to Vec2(5_150.0, 2_300.0),
        R.string.sample_room_kitchen to Vec2(1_800.0, 2_300.0),
        R.string.sample_room_bath to Vec2(1_800.0, 5_800.0),
        R.string.sample_room_bedroom to Vec2(5_150.0, 5_800.0),
        R.string.sample_room_terrace to Vec2(8_250.0, 2_300.0),
    )

    /** Layer palette entries matching the drawing's layers. */
    /**
     * Layer palette rows. The id is the DXF layer name, kept ASCII because DXF
     * layer names travel through files written by other tools; the display name
     * is Turkish and comes from resources.
     */
    public val layerIds: List<Pair<LayerState, Int>> = listOf(
        LayerState("DUVAR", "DUVAR") to R.string.sample_layer_walls,
        LayerState("BOLME", "BOLME") to R.string.sample_layer_partitions,
        LayerState("ACIKLIK", "ACIKLIK", opacity = 0.75, colour = "#C97D5D")
            to R.string.sample_layer_openings,
        LayerState("MOBILYA", "MOBILYA", opacity = 0.5) to R.string.sample_layer_furniture,
        LayerState("IZGARA", "IZGARA", visible = false, opacity = 0.25)
            to R.string.sample_layer_grid,
    )

    public val materials: List<MaterialSwatch> = listOf(
        MaterialSwatch("plaster", R.string.material_plaster, "#D8D2C8", roughness = 0.9, selected = true),
        MaterialSwatch("oak", R.string.material_oak, "#9A7B4F", roughness = 0.55),
        MaterialSwatch("concrete", R.string.material_concrete, "#7E7A76", roughness = 0.8),
        MaterialSwatch("brass", R.string.material_brass, "#C97D5D", roughness = 0.25),
        MaterialSwatch("slate", R.string.material_slate, "#3A3A3A", roughness = 0.7),
        MaterialSwatch("glass", R.string.material_glass, "#AFC4CC", roughness = 0.05),
    )

    /** The properties shown for the default selection. */
    public val properties: List<PropertyRow> = listOf(
        PropertyRow(R.string.property_wall, "120mm"),
        PropertyRow(R.string.property_length, "5500mm"),
        PropertyRow(R.string.property_height, "2900mm"),
        PropertyRow(R.string.property_area, "24.75m²"),
        // The layer value is the DXF layer name out of the drawing, so it is data.
        PropertyRow(R.string.property_layer, "BOLME", numeric = false),
    )

    public const val SELECTION_TITLE: Int = R.string.sample_selection

    // --- helpers ----------------------------------------------------------
    private fun wall(layer: String, x1: Double, y1: Double, x2: Double, y2: Double) =
        DxfEntity.Line(layer, Vec3(x1, y1, 0.0), Vec3(x2, y2, 0.0))

    private fun rectangle(layer: String, x1: Double, y1: Double, x2: Double, y2: Double) =
        listOf(
            DxfEntity.Polyline(
                layer,
                listOf(Vec2(x1, y1), Vec2(x2, y1), Vec2(x2, y2), Vec2(x1, y2)),
                closed = true,
            ),
        )

    /** A window reads as a pair of thin lines inside the wall thickness. */
    private fun window(layer: String, x1: Double, y1: Double, x2: Double, y2: Double): List<DxfEntity> {
        val horizontal = y1 == y2
        return if (horizontal) {
            listOf(
                wall(layer, x1, y1 + 60.0, x2, y1 + 60.0),
                wall(layer, x1, y1 + 140.0, x2, y1 + 140.0),
            )
        } else {
            listOf(
                wall(layer, x1 - 60.0, y1, x1 - 60.0, y2),
                wall(layer, x1 - 140.0, y1, x1 - 140.0, y2),
            )
        }
    }
}
