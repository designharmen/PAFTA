package com.harmen.pafta.ui.state

import androidx.annotation.StringRes
import com.harmen.pafta.R
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.measure.Measurement
import com.harmen.pafta.measure.MeasurementDisplay
import com.harmen.pafta.measure.MeasurementKind
import com.harmen.pafta.project.Annotation
import com.harmen.pafta.project.DoorSwing
import com.harmen.pafta.project.DrawnShape
import com.harmen.pafta.project.EditRefusal
import com.harmen.pafta.project.AnnotationKind
import com.harmen.pafta.project.LayerState
import com.harmen.pafta.project.MaterialOverride
import com.harmen.pafta.project.OpeningKind
import com.harmen.pafta.project.StoreFailure
import com.harmen.pafta.project.WallMaterial

/**
 * Everything the user can pick up and work with.
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
    DOOR(R.string.tool_door, ready = true),
    WINDOW(R.string.tool_window, ready = true),
    ZONE(R.string.tool_zone, ready = true),
    FILLET(R.string.tool_fillet, ready = true),
    CHAMFER(R.string.tool_chamfer, ready = true),
    TRIM(R.string.tool_trim, ready = true),
    EXTEND(R.string.tool_extend, ready = true),
    MIRROR(R.string.tool_mirror, ready = true),
    JOIN(R.string.tool_join, ready = true),
    MEASURE(R.string.tool_measure, ready = true),
    GRID(R.string.tool_grid, ready = true),
    ARC(R.string.tool_arc),
    HATCH(R.string.tool_hatch),
    TEXT(R.string.tool_text),
    SLAB(R.string.tool_slab),
    FURNITURE(R.string.tool_furniture),
}

/**
 * The drawing and editing tools, in the order they sit on the top row.
 *
 * These are the commands of a drawing office: pick something, draw a line,
 * round a corner, cut one thing back to another, measure. They are on top
 * because they apply to whatever is already on the sheet.
 */
public val TOOLBAR_TOOLS: List<Tool> = listOf(
    Tool.SELECT,
    Tool.LINE,
    Tool.RECTANGLE,
    Tool.CIRCLE,
    Tool.ARC,
    Tool.FILLET,
    Tool.CHAMFER,
    Tool.TRIM,
    Tool.EXTEND,
    Tool.MIRROR,
    Tool.JOIN,
    Tool.MEASURE,
    Tool.HATCH,
    Tool.TEXT,
    Tool.GRID,
)

/**
 * The building elements, in the order they sit in the right-hand column.
 *
 * A wall is not a line and a door is not a rectangle: these make the building
 * itself, they carry thickness, height and material, and they are counted as
 * elements. Keeping them in their own column is what stops "çizgi" and "duvar"
 * from looking like two names for the same job.
 */
public val ELEMENT_TOOLS: List<Tool> = listOf(
    Tool.WALL,
    Tool.DOOR,
    Tool.WINDOW,
    Tool.ZONE,
    Tool.SLAB,
    Tool.FURNITURE,
)

/**
 * Tools that make a shape rather than inspect one.
 *
 * Public because the viewport needs it too: while one of these is active a
 * single finger draws instead of panning, and the viewport is the only place
 * that sees the finger.
 */
public val DRAWING_TOOLS: Set<Tool> = setOf(Tool.WALL, Tool.LINE, Tool.RECTANGLE, Tool.CIRCLE)

/**
 * Tools that put an object somewhere rather than draw a shape.
 *
 * A door is not dragged out to a size: it is a component, so it is placed with
 * one tap on the wall it belongs in and then given its numbers. The same is
 * true of a room, which is placed by tapping the floor it covers.
 */
public val PLACING_TOOLS: Set<Tool> = setOf(Tool.DOOR, Tool.WINDOW, Tool.ZONE)

/**
 * Tools that need two shapes before they can do anything.
 *
 * Round this corner, cut that corner off, cut this back to that, stretch this
 * until it reaches that — each one is a sentence with two nouns in it. They are
 * worked the way AutoCAD has always worked them: pick the tool, tap one, tap
 * the other.
 */
public val PAIRED_TOOLS: Set<Tool> = setOf(
    Tool.FILLET,
    Tool.CHAMFER,
    Tool.TRIM,
    Tool.EXTEND,
    Tool.MIRROR,
    Tool.JOIN,
)

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

    /**
     * A door or a window was put down where there is no wall.
     *
     * Data, not a sentence: which kind it was, so `UiText` can say "kapı" or
     * "pencere" without an English word ever being written in Kotlin.
     */
    public data class NothingToPlaceOn(val kind: OpeningKind) : UiError

    /** The opening does not fit in the wall it was put in. */
    public data class OpeningTooWide(val kind: OpeningKind) : UiError

    /** The tap for a room did not land inside walls that close. */
    public data object NotEnclosed : UiError

    /** A two-shape edit could not be done. Carries why, not a sentence. */
    public data class EditRefused(val reason: EditRefusal) : UiError
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
    /** Width the door tool places with, in drawing millimetres. */
    val doorWidthMm: Double = DrawnShape.DEFAULT_DOOR_WIDTH_MM,
    /** Width the window tool places with. */
    val windowWidthMm: Double = DrawnShape.DEFAULT_WINDOW_WIDTH_MM,
    /** Which jamb a placed door hangs on, and which way it opens. */
    val doorSwing: DoorSwing = DoorSwing.LEFT_IN,
    /**
     * The first shape a two-shape tool has been given, while it waits for the
     * second.
     *
     * Kept apart from the selection so that picking a corner to round does not
     * also change what the panel on the right is showing.
     */
    val firstPickId: String? = null,
    /** The radius the round-off tool works with. */
    val filletRadiusMm: Double = 300.0,
    /** How far back along each side the chamfer tool cuts. */
    val chamferMm: Double = 300.0,
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
