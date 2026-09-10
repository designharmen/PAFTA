package com.harmen.pafta.ui.state

import com.harmen.pafta.measure.Measurement
import com.harmen.pafta.measure.MeasurementDisplay
import com.harmen.pafta.project.Annotation
import com.harmen.pafta.project.AnnotationKind
import com.harmen.pafta.project.LayerState
import com.harmen.pafta.project.MaterialOverride

/** The tools on the left rail, in the order they appear. */
public enum class Tool(public val label: String) {
    SELECT("Select"),
    PENCIL("Pencil"),
    LINE("Line"),
    ARC("Arc"),
    DIM("Dim"),
    DIMENSIONS("Dimensions"),
    HATCH("Hatch"),
    TEXT("Text"),
    GRID("Grid"),
    MEASURE("Measure"),
    PALETTE("Palette"),
    LAYERS("Layers"),
}

/** The tab group in the second row of the top bar. */
public enum class ViewTab(public val label: String) {
    ACTIVE("Active"),
    ANNOTATIONS("Annotations"),
    FURNITURE("Furniture"),
    WALLS("Walls"),
    GRID("Grid"),
}

/** The left-hand menu group in the second row. */
public enum class EditMode(public val label: String) {
    EDIT("EDIT"),
    VIEW("VIEW"),
}

/** A material swatch in `[Material Selector]`. */
public data class MaterialSwatch(
    val id: String,
    val name: String,
    /** `#RRGGBB`. */
    val colour: String,
    val roughness: Double = 0.6,
    val selected: Boolean = false,
)

/** One row of the `[Properties]` table. */
public data class PropertyRow(
    val key: String,
    val value: String,
    /** Numeric values are set in the mono face so columns align. */
    val numeric: Boolean = true,
)

/**
 * Everything the editor screen draws.
 *
 * One immutable snapshot per frame: the screen is a pure function of this, which
 * keeps the chrome testable and makes undo a matter of swapping the snapshot.
 */
public data class EditorState(
    val projectName: String = "Untitled",
    val unitLabel: String = "Unit 101 – Lvl 2",
    val activeTool: Tool = Tool.SELECT,
    val activeTab: ViewTab = ViewTab.ACTIVE,
    val editMode: EditMode = EditMode.EDIT,
    val layers: List<LayerState> = emptyList(),
    val materials: List<MaterialSwatch> = emptyList(),
    val properties: List<PropertyRow> = emptyList(),
    val selectionTitle: String? = null,
    val annotations: List<Annotation> = emptyList(),
    val measurements: List<Measurement> = emptyList(),
    val materialOverrides: List<MaterialOverride> = emptyList(),
    val display: MeasurementDisplay = MeasurementDisplay(),
    /** Dimension presets offered under the `Dimensions` tool. */
    val dimensionPresets: List<Double> = listOf(3100.0, 4500.0, 4800.0),
    val selectedPreset: Double? = null,
    /** Preview of the last text the user typed, shown under the Text tool. */
    val lastText: String = "",
    val gridVisible: Boolean = true,
    /** Grid spacing in model millimetres. */
    val gridSpacingMm: Double = 1000.0,
    val annotationTool: AnnotationKind? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val dirty: Boolean = false,
)
