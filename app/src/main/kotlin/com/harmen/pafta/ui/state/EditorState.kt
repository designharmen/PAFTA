package com.harmen.pafta.ui.state

import androidx.annotation.StringRes
import com.harmen.pafta.R
import com.harmen.pafta.measure.Measurement
import com.harmen.pafta.measure.MeasurementDisplay
import com.harmen.pafta.project.Annotation
import com.harmen.pafta.project.AnnotationKind
import com.harmen.pafta.project.LayerState
import com.harmen.pafta.project.MaterialOverride
import com.harmen.pafta.project.StoreFailure

/**
 * The tools on the left rail, in the order they appear.
 *
 * Labels are string resource ids, not literals: PAFTA's interface is Turkish and
 * the wording lives in `strings.xml`, so a label can never be an English word
 * that slipped into code.
 */
public enum class Tool(@StringRes public val label: Int) {
    SELECT(R.string.tool_select),
    PENCIL(R.string.tool_pencil),
    LINE(R.string.tool_line),
    ARC(R.string.tool_arc),
    DIM(R.string.tool_dim),
    DIMENSIONS(R.string.tool_dimensions),
    HATCH(R.string.tool_hatch),
    TEXT(R.string.tool_text),
    GRID(R.string.tool_grid),
    MEASURE(R.string.tool_measure),
    PALETTE(R.string.tool_palette),
    LAYERS(R.string.tool_layers),
}

/** The tab group in the second row of the top bar. */
public enum class ViewTab(@StringRes public val label: Int) {
    ACTIVE(R.string.tab_active),
    ANNOTATIONS(R.string.tab_annotations),
    FURNITURE(R.string.tab_furniture),
    WALLS(R.string.tab_walls),
    GRID(R.string.tab_grid),
}

/** The top-bar menus. Identifiers, not labels — the wording is in resources. */
public enum class TopMenu { FILE, EDIT }

/** The left-hand menu group in the second row. */
public enum class EditMode(@StringRes public val label: Int) {
    EDIT(R.string.mode_edit),
    VIEW(R.string.mode_view),
}

/** A material swatch in the material selector. */
public data class MaterialSwatch(
    val id: String,
    @StringRes val name: Int,
    /** `#RRGGBB`. */
    val colour: String,
    val roughness: Double = 0.6,
    val selected: Boolean = false,
)

/**
 * One row of the properties table.
 *
 * The key is always a resource id. The value may be free text, because it is
 * either a number PAFTA formatted or data read out of the user's own file.
 */
public data class PropertyRow(
    @StringRes val key: Int,
    val value: String,
    /** Numeric values are set in the mono face so columns align. */
    val numeric: Boolean = true,
)

/**
 * Something that went wrong, in a form the screen can render in Turkish.
 *
 * The view models never build sentences: they pass the failure along and the
 * composable resolves it against `strings.xml`.
 */
public sealed interface UiError {
    /** An import, open or listing failure. */
    public data class Store(val failure: StoreFailure) : UiError

    /** A save failed, which needs saying differently: work is still unsaved. */
    public data class SaveFailed(val failure: StoreFailure) : UiError
}

/**
 * Everything the editor screen draws.
 *
 * One immutable snapshot per frame: the screen is a pure function of this, which
 * keeps the chrome testable and makes undo a matter of swapping the snapshot.
 */
public data class EditorState(
    val projectName: String = "",
    val unitLabel: String = "",
    val activeTool: Tool = Tool.SELECT,
    val activeTab: ViewTab = ViewTab.ACTIVE,
    val editMode: EditMode = EditMode.EDIT,
    val layers: List<LayerState> = emptyList(),
    val materials: List<MaterialSwatch> = emptyList(),
    val properties: List<PropertyRow> = emptyList(),
    /** Resource id naming what is selected, or null when nothing is. */
    @StringRes val selectionTitle: Int? = null,
    val annotations: List<Annotation> = emptyList(),
    val measurements: List<Measurement> = emptyList(),
    val materialOverrides: List<MaterialOverride> = emptyList(),
    val display: MeasurementDisplay = MeasurementDisplay(),
    /** Dimension presets offered under the dimensions tool, in millimetres. */
    val dimensionPresets: List<Double> = listOf(3100.0, 4500.0, 4800.0),
    val selectedPreset: Double? = null,
    /** Preview of the last text the user typed, shown under the text tool. */
    val lastText: String = "",
    val gridVisible: Boolean = true,
    /** Grid spacing in model millimetres. */
    val gridSpacingMm: Double = 1000.0,
    val annotationTool: AnnotationKind? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val dirty: Boolean = false,
)
