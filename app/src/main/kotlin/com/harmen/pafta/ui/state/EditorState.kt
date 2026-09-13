package com.harmen.pafta.ui.state

import androidx.annotation.StringRes
import com.harmen.pafta.R
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.measure.Measurement
import com.harmen.pafta.measure.MeasurementDisplay
import com.harmen.pafta.measure.MeasurementKind
import com.harmen.pafta.project.Annotation
import com.harmen.pafta.project.DrawnShape
import com.harmen.pafta.project.AnnotationKind
import com.harmen.pafta.project.LayerState
import com.harmen.pafta.project.MaterialOverride
import com.harmen.pafta.project.StoreFailure
import com.harmen.pafta.project.WallMaterial

/**
 * The tools on the left rail, in the order they appear.
 *
 * Labels are string resource ids, not literals: PAFTA's interface is Turkish and
 * the wording lives in `strings.xml`, so a label can never be an English word
 * that slipped into code.
 */
public enum class Tool(
    @StringRes public val label: Int,
    /**
     * Whether this tool actually does something yet.
     *
     * A tool that is drawn like every other one but answers a tap with nothing
     * is the worst thing an interface can do, and PAFTA had nine of them. The
     * unfinished ones are shown faint and cannot be tapped until the phase that
     * builds them lands, so the rail always tells the truth about itself.
     */
    public val ready: Boolean = false,
) {
    SELECT(R.string.tool_select, ready = true),
    WALL(R.string.tool_wall, ready = true),
    LINE(R.string.tool_line, ready = true),
    RECTANGLE(R.string.tool_rectangle, ready = true),
    CIRCLE(R.string.tool_circle, ready = true),
    MEASURE(R.string.tool_measure, ready = true),
    GRID(R.string.tool_grid, ready = true),
    ARC(R.string.tool_arc),
    HATCH(R.string.tool_hatch),
    TEXT(R.string.tool_text),
    PALETTE(R.string.tool_palette),
    LAYERS(R.string.tool_layers),
}

/**
 * Tools that make a shape rather than inspect one.
 *
 * Public because the viewport needs it too: while one of these is active a
 * single finger draws instead of panning, and the viewport is the only place
 * that sees the finger.
 */
public val DRAWING_TOOLS: Set<Tool> = setOf(Tool.WALL, Tool.LINE, Tool.RECTANGLE, Tool.CIRCLE)

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
    /** Which kind of measurement the Ölç tool is taking. */
    val measureMode: MeasurementKind = MeasurementKind.DISTANCE,
    /**
     * Points picked for a measurement that is not finished yet.
     *
     * Session state, not document state: it is deliberately kept out of the
     * undo history, because undoing a half-finished tap is not a step anyone
     * means to take back.
     */
    val pendingPicks: List<Vec2> = emptyList(),
    /** What the user has drawn, on top of the imported file. */
    val shapes: List<DrawnShape> = emptyList(),
    /** The drawn shape currently selected, if any. */
    val selectedShapeId: String? = null,
    /**
     * The shape being drawn right now, following the finger.
     *
     * It is not in [shapes] and not in the undo history: until the finger
     * lifts, nothing has been drawn.
     */
    val preview: DrawnShape? = null,
    /**
     * The point the finger has been pulled onto, while it is being pulled.
     *
     * Shown as a small square on the plan. Without it the user has no way to
     * tell whether a corner was caught or missed until the wall is finished and
     * it is too late — and neither had I.
     */
    val snapAt: Vec2? = null,
    /** Thickness used by the wall tool, in drawing millimetres. */
    val wallThicknessMm: Double = DrawnShape.DEFAULT_WALL_THICKNESS_MM,
    /** Material used by the wall tool; it decides which layer the wall lands on. */
    val wallMaterial: WallMaterial = WallMaterial.BRICK,
    /** Preview of the last text the user typed, shown under the text tool. */
    val lastText: String = "",
    val gridVisible: Boolean = true,
    /**
     * Grid spacing in model millimetres: 10cm.
     *
     * Fine enough to place a wall face on, and the brighter every-tenth line
     * then falls on the metre, so the two readings an architect wants are both
     * on the sheet. It is also what a point without anything to catch snaps to,
     * so a wall drawn in open space still lands on a round number.
     */
    val gridSpacingMm: Double = 100.0,
    val annotationTool: AnnotationKind? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val dirty: Boolean = false,
)
