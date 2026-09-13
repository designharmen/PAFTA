package com.harmen.pafta.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harmen.pafta.R
import com.harmen.pafta.data.ProjectRepository
import com.harmen.pafta.dxf.DxfDrawing
import com.harmen.pafta.dxf.near
import com.harmen.pafta.dxf.snapSegments
import com.harmen.pafta.geometry.Segment2
import com.harmen.pafta.geometry.Aabb
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import com.harmen.pafta.measure.MeasurementEngine
import com.harmen.pafta.measure.MeasurementKind
import com.harmen.pafta.measure.SnapKind
import com.harmen.pafta.measure.snap
import com.harmen.pafta.project.AnnotationKind
import com.harmen.pafta.project.AutoSavePolicy
import com.harmen.pafta.project.DoorSwing
import com.harmen.pafta.project.DrawingDocument
import com.harmen.pafta.project.DrawnShape
import com.harmen.pafta.project.LayerState
import com.harmen.pafta.project.OpeningKind
import com.harmen.pafta.project.PaftaProject
import com.harmen.pafta.project.ShapeDimension
import com.harmen.pafta.project.StoreResult
import com.harmen.pafta.project.StoredMeasurement
import com.harmen.pafta.project.UndoStack
import com.harmen.pafta.project.WallMaterial
import com.harmen.pafta.project.pickResolved
import com.harmen.pafta.project.snapSegments
import com.harmen.pafta.project.toEntities
import com.harmen.pafta.project.withDimension
import com.harmen.pafta.project.withId
import com.harmen.pafta.project.zonePlans
import com.harmen.pafta.units.formatLength
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the editor screen needs beyond [EditorState]: the open document. */
public data class EditorDocument(
    val file: File,
    val drawing: DxfDrawing,
    /**
     * What the opening view is framed around: the file's extent together with
     * anything already drawn by hand, which may reach past it.
     */
    val bounds: Aabb,
    val unsupportedEntityTypes: Set<String> = emptySet(),
)

/**
 * Drives the editor: the open project, the undo history, and auto-save.
 *
 * The undo stack and the save timing policy both come from `core:project`, where
 * they are unit-tested; this class is the wiring between them, the UI state, and
 * the repository.
 */
public class EditorViewModel(
    private val repository: ProjectRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    public val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _document = MutableStateFlow<EditorDocument?>(null)
    public val document: StateFlow<EditorDocument?> = _document.asStateFlow()

    private val _error = MutableStateFlow<UiError?>(null)
    public val error: StateFlow<UiError?> = _error.asStateFlow()

    private val history = UndoStack<EditorState>(limit = 64)
    private val autoSave = AutoSavePolicy()

    /** The pick state machine from `core:measure`, unit-tested there. */
    private val engine = MeasurementEngine()

    /**
     * What a tap can snap to, rebuilt when the drawing opens and whenever a
     * layer is hidden — a measurement must never lock onto a line the user
     * cannot see.
     */
    private var snapCandidates: List<Segment2> = emptyList()

    /** Makes each drawn shape's id unique within a session. */
    private var nextShape: Int = 0

    /** Where the finger went down, and where it is now, while drawing. */
    private var dragFrom: Vec2? = null
    private var dragTo: Vec2? = null

    /** The project as last loaded or saved; the overlay is rebuilt from state. */
    private var project: PaftaProject? = null
    private var autoSaveJob: Job? = null

    /** Loads a project and its drawing. */
    public fun open(file: File) {
        viewModelScope.launch {
            when (val result = repository.openAsDrawing(file)) {
                is StoreResult.Failure -> {
                    _error.value = UiError.Store(result.failure)
                    _document.value = null
                }

                is StoreResult.Success -> {
                    val (loaded, doc) = result.value
                    project = loaded
                    history.clear()
                    autoSave.onSaved()
                    _error.value = null
                    _document.value = EditorDocument(
                        file = file,
                        drawing = doc.drawing,
                        bounds = doc.bounds,
                        unsupportedEntityTypes = doc.unsupportedEntityTypes,
                    )
                    _state.value = loaded.toEditorState(doc).let {
                        it.copy(properties = drawingProperties(_document.value, it))
                    }
                    rebuildSnapCandidates()
                }
            }
        }
    }

    /** Closes the project, flushing any unsaved edits first. */
    public fun close(onClosed: () -> Unit = {}) {
        viewModelScope.launch {
            flush()
            project = null
            history.clear()
            _document.value = null
            _state.value = EditorState()
            onClosed()
        }
    }

    // --- Selection: no document change, so nothing is recorded or saved ------
    public fun selectTool(tool: Tool) {
        // Leaving a tool abandons whatever it had half-finished, rather than
        // leaving stray points waiting on the drawing.
        if (tool != Tool.MEASURE) engine.cancel()
        _state.update { it.copy(pendingPicks = emptyList()) }
        if (tool != Tool.SELECT) _state.update { it.copy(selectedShapeId = null) }
        _state.update {
            it.copy(
                activeTool = tool,
                annotationTool = null,
                // Grid is a toggle, not a mode: tapping it has to change
                // something visible or the icon is a lie.
                gridVisible = if (tool == Tool.GRID) !it.gridVisible else it.gridVisible,
            )
        }
    }

    public fun selectTab(tab: ViewTab) {
        _state.update { it.copy(activeTab = tab) }
    }

    public fun selectEditMode(mode: EditMode) {
        _state.update { it.copy(editMode = mode) }
    }

    public fun selectAnnotationTool(kind: AnnotationKind?) {
        _state.update { it.copy(annotationTool = kind, activeTool = Tool.TEXT) }
    }

    public fun toggleGrid() {
        _state.update { it.copy(gridVisible = !it.gridVisible) }
    }

    // --- Document edits: recorded and auto-saved -----------------------------
    public fun setLayerVisible(layerId: String, visible: Boolean) {
        edit { s ->
            s.copy(layers = s.layers.map { if (it.id == layerId) it.copy(visible = visible) else it })
        }
        rebuildSnapCandidates()
    }

    public fun setLayerOpacity(layerId: String, opacity: Double) {
        val clamped = opacity.coerceIn(0.0, 1.0)
        edit { s ->
            s.copy(layers = s.layers.map { if (it.id == layerId) it.copy(opacity = clamped) else it })
        }
    }

    public fun selectMaterial(materialId: String) {
        edit { s -> s.copy(materials = s.materials.map { it.copy(selected = it.id == materialId) }) }
    }

    public fun edit(transform: (EditorState) -> EditorState) {
        val current = _state.value
        val edited = transform(current)
        if (edited == current) return

        // The facts table counts what is on the plan, so it has to be counted
        // again whenever the plan changes. It was worked out once, when the file
        // was opened, and then stood still: drawing ten walls left it saying
        // exactly what it had said before anything was drawn.
        val next =
            if (edited.shapes != current.shapes || edited.layers != current.layers) {
                edited.copy(properties = drawingProperties(_document.value, edited))
            } else {
                edited
            }

        history.record(current)
        _state.value = next.copy(canUndo = true, canRedo = false, dirty = true)
        markEdited()
    }

    public fun undo() {
        val current = _state.value
        val previous = history.undo(current) ?: return
        _state.value = previous.copy(
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            dirty = true,
            // Tool and tab belong to the live session, not to document history.
            activeTool = current.activeTool,
            activeTab = current.activeTab,
            annotationTool = current.annotationTool,
            // Session state, not document history.
            measureMode = current.measureMode,
            pendingPicks = current.pendingPicks,
        )
        markEdited()
    }

    public fun redo() {
        val current = _state.value
        val next = history.redo(current) ?: return
        _state.value = next.copy(
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            dirty = true,
            activeTool = current.activeTool,
            activeTab = current.activeTab,
            annotationTool = current.annotationTool,
            measureMode = current.measureMode,
            pendingPicks = current.pendingPicks,
        )
        markEdited()
    }

    // --- Measuring ----------------------------------------------------------

    /** Switches what the Ölç tool takes: a distance, an area, or an angle. */
    public fun selectMeasureMode(mode: MeasurementKind) {
        // Setting the engine's mode discards any half-taken picks, which is
        // right: three points meant for an angle are not the start of an area.
        engine.mode = mode
        _state.update {
            it.copy(measureMode = mode, pendingPicks = emptyList(), activeTool = Tool.MEASURE)
        }
    }

    /**
     * A tap on the drawing.
     *
     * @param point where the finger landed, in drawing millimetres.
     * @param toleranceMm how far from that point a snap may reach, also in
     *   millimetres — the viewport converts a finger-sized distance on screen
     *   into model units, so the tolerance stays the same size to the eye at
     *   every zoom level.
     */
    public fun onCanvasPick(point: Vec2, toleranceMm: Double) {
        when (_state.value.activeTool) {
            Tool.MEASURE -> measurePick(point, toleranceMm)
            Tool.SELECT -> selectAt(point, toleranceMm)
            Tool.DOOR -> placeOpening(point, toleranceMm, OpeningKind.DOOR)
            Tool.WINDOW -> placeOpening(point, toleranceMm, OpeningKind.WINDOW)
            Tool.ZONE -> placeZone(point)
            in DRAWING_TOOLS -> drawPick(point, toleranceMm)
            else -> Unit
        }
    }

    private fun measurePick(point: Vec2, toleranceMm: Double) {
        val nearby = snapCandidates.near(point, toleranceMm)
        val landed = snap(
            pick = point,
            segments = nearby,
            tolerance = toleranceMm,
            gridSpacing = if (_state.value.gridVisible) _state.value.gridSpacingMm else null,
        )

        val finished = engine.addPick(landed.point)
        if (finished == null) {
            _state.update { it.copy(pendingPicks = engine.pendingPoints.map(Vec3::toVec2)) }
        } else {
            _state.update { it.copy(pendingPicks = emptyList()) }
            edit { s -> s.copy(measurements = s.measurements + finished) }
        }
    }

    /**
     * A tap while a drawing tool is active.
     *
     * Every shape here takes exactly two taps — a wall its two ends, a rectangle
     * two opposite corners, a circle its centre and a point on its edge. Two
     * taps is the most a shape is allowed to cost: one is not enough to say
     * where and how big, and three is a tool that needs explaining.
     */
    private fun drawPick(point: Vec2, toleranceMm: Double) {
        val landed = snapped(point, toleranceMm)
        val first = _state.value.pendingPicks.firstOrNull()

        if (first == null) {
            _state.update { it.copy(pendingPicks = listOf(landed)) }
            return
        }

        val shape = shapeBetween(first, landed, id = newShapeId())
        if (shape == null) {
            _state.update { it.copy(pendingPicks = emptyList()) }
            return
        }

        // A wall is rarely drawn alone: a room is a run of them. The next wall
        // therefore starts where this one ended, so the whole outline is drawn
        // in one movement instead of restarting at every corner — and the
        // junction is exact, because it is the same point rather than a second
        // attempt to hit it.
        val chained = _state.value.activeTool in CHAINED_TOOLS
        _state.update { it.copy(pendingPicks = if (chained) listOf(landed) else emptyList()) }

        // Deliberately not selected: a shape that selects itself the moment it
        // is drawn makes everything look permanently picked, and the next shape
        // appears to delete the last one as the highlight moves.
        edit { s -> s.copy(shapes = s.shapes + shape, layers = s.layers.including(shape.layer)) }
        rebuildSnapCandidates()
    }

    /**
     * Unique within the project: the clock separates sessions, the counter
     * separates shapes drawn inside one. A repeated id would mean selecting one
     * shape and deleting another.
     */
    private fun newShapeId(): String = "s" + clock() + "-" + (nextShape++)

    private fun shapeBetween(from: Vec2, to: Vec2, id: String): DrawnShape? {
        val a = Vec3(from.x, from.y, 0.0)
        val b = Vec3(to.x, to.y, 0.0)

        val shape = when (_state.value.activeTool) {
            Tool.WALL -> {
                val thickness = _state.value.wallThicknessMm
                val material = _state.value.wallMaterial
                DrawnShape.Wall(
                    id = id,
                    a = a,
                    b = b,
                    thicknessMm = thickness,
                    material = material,
                    layer = DrawnShape.wallLayer(thickness, material),
                )
            }
            Tool.LINE -> DrawnShape.Line(id, a, b)
            Tool.RECTANGLE -> DrawnShape.Rectangle(id, a, b)
            Tool.CIRCLE -> DrawnShape.Circle(id, a, from.distanceTo(to))
            else -> return null
        }
        // A shape with no size is a mis-tap, not a drawing: two taps in the same
        // place should leave the plan exactly as it was.
        return if (shape.toEntities().isEmpty()) null else shape
    }

    private fun selectAt(point: Vec2, toleranceMm: Double) {
        // Resolved, so a tap in a doorway picks the door rather than the wall
        // it is cut into, and a tap on the floor picks the room.
        val hit = _state.value.shapes.pickResolved(point, toleranceMm)
        _state.update { it.copy(selectedShapeId = hit?.id) }
    }

    /**
     * Puts a door or a window in the wall under the tap.
     *
     * Nothing happens on a tap that misses every wall, and the screen says why:
     * an opening is a hole in something, so there is nowhere for one to go on
     * open paper. That is a truthful no, not a silent one.
     */
    private fun placeOpening(point: Vec2, toleranceMm: Double, kind: OpeningKind) {
        val wall = _state.value.shapes
            .filterIsInstance<DrawnShape.Wall>()
            .lastOrNull { shape ->
                shape.centreLine().closestPointTo(point).distanceTo(point) <=
                    shape.thicknessMm / 2.0 + toleranceMm
            }

        if (wall == null) {
            _error.value = UiError.NothingToPlaceOn(kind)
            return
        }

        val along = wall.b.toVec2() - wall.a.toVec2()
        val length = along.length
        if (length < 1.0) return
        // How far down the wall the finger landed, measured along its centre
        // line — which is the one number an opening keeps.
        val distance = ((point - wall.a.toVec2()) dot along) / length

        val width = when (kind) {
            OpeningKind.DOOR -> _state.value.doorWidthMm
            OpeningKind.WINDOW -> _state.value.windowWidthMm
        }
        if (width > length) {
            _error.value = UiError.OpeningTooWide(kind)
            return
        }

        val opening = DrawnShape.Opening(
            id = newShapeId(),
            wallId = wall.id,
            kind = kind,
            alongMm = distance,
            widthMm = width,
            heightMm = when (kind) {
                OpeningKind.DOOR -> DrawnShape.DEFAULT_DOOR_HEIGHT_MM
                OpeningKind.WINDOW -> DrawnShape.DEFAULT_WINDOW_HEIGHT_MM
            },
            sillMm = when (kind) {
                OpeningKind.DOOR -> 0.0
                OpeningKind.WINDOW -> DrawnShape.DEFAULT_WINDOW_SILL_MM
            },
            swing = _state.value.doorSwing,
            layer = when (kind) {
                OpeningKind.DOOR -> DrawnShape.LAYER_DOOR
                OpeningKind.WINDOW -> DrawnShape.LAYER_WINDOW
            },
        )

        edit { s ->
            s.copy(shapes = s.shapes + opening, layers = s.layers.including(opening.layer))
        }
    }

    /**
     * Names and measures the room the tap landed in.
     *
     * One tap: the walls around the point are found, the floor between them is
     * measured, and the room is there. It is given the plain word for a room
     * until the user types over it, because a room with no name at all cannot
     * be told from the floor it sits on.
     */
    private fun placeZone(point: Vec2) {
        val zone = DrawnShape.Zone(
            id = newShapeId(),
            name = defaultZoneName,
            seed = Vec3(point.x, point.y, 0.0),
        )

        // Refused rather than placed empty: a room the walls do not enclose is
        // a mis-tap, and one that sits on the plan measuring nothing is worse
        // than being told the walls do not close.
        val measured = (_state.value.shapes + zone).zonePlans().first { it.id == zone.id }
        if (measured.isOpen) {
            _error.value = UiError.NotEnclosed
            return
        }

        edit { s ->
            s.copy(
                shapes = s.shapes + zone,
                layers = s.layers.including(zone.layer),
                selectedShapeId = zone.id,
            )
        }
    }

    /**
     * The word a new room is given until the user types over it.
     *
     * Set by the screen from `strings.xml`, never written here: a Turkish word
     * in Kotlin is how English ends up on a Turkish screen. Empty until the
     * screen sets it, which only means a new room starts unnamed.
     */
    public var defaultZoneName: String = ""

    /** Sets the name of the selected room. */
    public fun setSelectedName(name: String) {
        val id = _state.value.selectedShapeId ?: return
        edit { s ->
            s.copy(
                shapes = s.shapes.map {
                    if (it.id == id && it is DrawnShape.Zone) it.copy(name = name) else it
                },
            )
        }
    }

    /** Sets which way the selected door opens, and which way the next one will. */
    public fun setDoorSwing(swing: DoorSwing) {
        val id = _state.value.selectedShapeId
        edit { s ->
            s.copy(
                doorSwing = swing,
                shapes = s.shapes.map {
                    if (it.id == id && it is DrawnShape.Opening) it.copy(swing = swing) else it
                },
            )
        }
    }

    /** Sets the width the opening tools place with. */
    public fun setOpeningWidth(kind: OpeningKind, millimetres: Double) {
        _state.update {
            when (kind) {
                OpeningKind.DOOR -> it.copy(doorWidthMm = millimetres, activeTool = Tool.DOOR)
                OpeningKind.WINDOW -> it.copy(windowWidthMm = millimetres, activeTool = Tool.WINDOW)
            }
        }
    }

    /** Moves the selected shape by a drawing-millimetre offset. */
    public fun moveSelected(dx: Double, dy: Double) {
        val id = _state.value.selectedShapeId ?: return
        edit { s ->
            s.copy(shapes = s.shapes.map { if (it.id == id) it.translated(dx, dy) else it })
        }
        rebuildSnapCandidates()
    }

    /** Deletes the selected shape. Recorded, so it can be undone. */
    public fun deleteSelected() {
        val id = _state.value.selectedShapeId ?: return
        edit { s -> s.copy(shapes = s.shapes.filterNot { it.id == id }, selectedShapeId = null) }
        rebuildSnapCandidates()
    }

    /** Sets the thickness the wall tool draws with, in millimetres. */
    public fun selectWallThickness(thicknessMm: Double) {
        _state.update { it.copy(wallThicknessMm = thicknessMm, activeTool = Tool.WALL) }
    }

    // --- Drawing by dragging -------------------------------------------------

    /**
     * The finger has gone down with a drawing tool active.
     *
     * Dragging is how a wall should be drawn — the shape follows the path the
     * hand takes, the way a line is drawn with a pencil — so this is the main
     * route. Two separate taps still work for anyone who prefers to place two
     * points exactly, and for a hand that cannot hold a steady drag.
     */
    public fun beginDrag(point: Vec2, toleranceMm: Double) {
        if (_state.value.activeTool !in DRAWING_TOOLS) return
        dragFrom = snapped(point, toleranceMm)
        dragTo = dragFrom
        // Deliberately does not clear the points picked so far: a tap is a
        // press and a release like any other, so clearing here would wipe the
        // first tap of a two-tap shape the instant the second tap began.
        _state.update { it.copy(preview = null, snapAt = lastSnap) }
    }

    /** The finger has moved; the shape being drawn follows it. */
    public fun updateDrag(point: Vec2, toleranceMm: Double) {
        val from = dragFrom ?: return
        val to = snapped(point, toleranceMm)
        dragTo = to
        _state.update {
            it.copy(preview = shapeBetween(from, to, id = PREVIEW_ID), snapAt = lastSnap)
        }
    }

    /** The finger has lifted: the shape is committed at the size it was drawn. */
    public fun endDrag() {
        val from = dragFrom
        val to = dragTo
        dragFrom = null
        dragTo = null
        _state.update { it.copy(preview = null, pendingPicks = emptyList(), snapAt = null) }
        if (from == null || to == null) return

        val shape = shapeBetween(from, to, id = newShapeId()) ?: return
        edit { s -> s.copy(shapes = s.shapes + shape, layers = s.layers.including(shape.layer)) }
        rebuildSnapCandidates()
    }

    /** The drag turned into a pinch, or was otherwise abandoned. */
    public fun cancelDrag() {
        dragFrom = null
        dragTo = null
        _state.update { it.copy(preview = null, snapAt = null) }
    }

    /** Ends a run of walls without switching tool. */
    public fun finishChain() {
        _state.update { it.copy(pendingPicks = emptyList()) }
    }

    /**
     * Sets one of the selected shape's measurements exactly.
     *
     * The point of the whole drawing tool: a finger cannot land on 3600mm, so
     * the shape is drawn roughly and then told what it is — its length, its
     * thickness, its height, whichever of those it has.
     */
    public fun setSelectedDimension(which: ShapeDimension, millimetres: Double) {
        val id = _state.value.selectedShapeId ?: return
        edit { s ->
            val shapes = s.shapes.map {
                if (it.id == id) it.withDimension(which, millimetres) else it
            }
            // A thickness change moves the wall to another layer, which that
            // layer has to exist for.
            s.copy(
                shapes = shapes,
                layers = shapes.firstOrNull { it.id == id }
                    ?.let { s.layers.including(it.layer) }
                    ?: s.layers,
            )
        }
        rebuildSnapCandidates()
    }

    /**
     * Copies the selected shape and selects the copy.
     *
     * This is how a second component with different numbers is made: duplicate
     * the one that is nearly right, then retype what differs. The copy is
     * nudged clear of the original, because a copy sitting exactly on top of
     * what it came from looks like nothing happened.
     */
    public fun duplicateSelected() {
        val id = _state.value.selectedShapeId ?: return
        val original = _state.value.shapes.firstOrNull { it.id == id } ?: return
        val copy = original.withId(newShapeId())
            .translated(DUPLICATE_OFFSET_MM, -DUPLICATE_OFFSET_MM)

        edit { s ->
            s.copy(
                shapes = s.shapes + copy,
                layers = s.layers.including(copy.layer),
                selectedShapeId = copy.id,
            )
        }
        rebuildSnapCandidates()
    }

    /** Sets what the wall tool builds with; this also decides its layer. */
    public fun selectWallMaterial(material: WallMaterial) {
        _state.update { it.copy(wallMaterial = material, activeTool = Tool.WALL) }
    }

    /**
     * The point a raw finger position is pulled onto.
     *
     * It also records what was caught in [lastSnap], so the screen can show it.
     * A grid line is not recorded: the grid is already drawn, and marking every
     * point on it would put a square under the finger at all times and say
     * nothing.
     */
    private fun snapped(point: Vec2, toleranceMm: Double): Vec2 {
        val nearby = snapCandidates.near(point, toleranceMm)
        val result = snap(
            pick = point,
            segments = nearby,
            tolerance = toleranceMm,
            gridSpacing = if (_state.value.gridVisible) _state.value.gridSpacingMm else null,
        )
        val landed = result.point.toVec2()
        lastSnap = when (result.kind) {
            SnapKind.NONE, SnapKind.GRID -> null
            else -> landed
        }
        return landed
    }

    /** What [snapped] last caught, or null when it caught nothing worth showing. */
    private var lastSnap: Vec2? = null

    /** Closes an area or a polyline, which have no fixed number of points. */
    public fun finishMeasurement() {
        val finished = engine.finish()
        _state.update { it.copy(pendingPicks = emptyList()) }
        if (finished != null) edit { s -> s.copy(measurements = s.measurements + finished) }
    }

    /** Takes back the last tap of a measurement in progress. */
    public fun undoPick() {
        engine.undoPick()
        _state.update { it.copy(pendingPicks = engine.pendingPoints.map(Vec3::toVec2)) }
    }

    /** Abandons the measurement in progress. */
    public fun cancelMeasurement() {
        engine.cancel()
        _state.update { it.copy(pendingPicks = emptyList()) }
    }

    /** Removes every finished measurement. Recorded, so it can be undone. */
    public fun clearMeasurements() {
        cancelMeasurement()
        edit { s -> s.copy(measurements = emptyList()) }
    }

    private companion object {
        /** Tools that keep going from where the last shape ended. */
        private val CHAINED_TOOLS = setOf(Tool.WALL, Tool.LINE)

        /** The id a shape being dragged carries; it never reaches the project. */
        private const val PREVIEW_ID = "onizleme"

        /** How far a duplicate is nudged off the shape it came from. */
        private const val DUPLICATE_OFFSET_MM = 500.0
    }

    private fun rebuildSnapCandidates() {
        val visible = _state.value.layers.filter { it.visible }.map { it.name }.toSet()
        // The file's own geometry, plus what the user has drawn since. A wall
        // contributes its centre line rather than its outline, which is what
        // lets the next wall meet it end to end instead of half a thickness off
        // to the side. Drawn shapes count even when no file is open: a plan
        // started from nothing still has to snap to itself.
        val fromFile = _document.value?.drawing
            ?.snapSegments(visibleLayers = visible.ifEmpty { null })
            .orEmpty()
        snapCandidates = fromFile + _state.value.shapes.flatMap { it.snapSegments() }
    }

    public fun dismissError() {
        _error.value = null
    }

    /**
     * Saves now if anything is pending. Called when the app is backgrounded and
     * before the project is closed, so unsaved work never depends on a timer
     * that the system may not let run.
     */
    public suspend fun flush() {
        if (autoSave.shouldSaveOnExit()) saveNow()
    }

    /** [flush] for callers that are not coroutines, such as lifecycle callbacks. */
    public fun requestFlush() {
        viewModelScope.launch { flush() }
    }

    /** Records an edit and schedules the next auto-save. */
    private fun markEdited() {
        autoSave.onEdit(clock())
        scheduleAutoSave()
    }

    /**
     * Keeps exactly one pending save timer. Re-scheduling on each edit is what
     * makes a drag of the opacity track write the container once, not forty
     * times.
     *
     * The waiting is a loop rather than a re-scheduling call, so the job is only
     * ever cancelled from outside itself: an edit that lands mid-wait simply
     * moves the deadline, and the same job waits again.
     */
    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (true) {
                val wait = autoSave.delayUntilSave(clock()) ?: return@launch
                if (wait > 0) {
                    delay(wait)
                    continue
                }
                saveNow()
                return@launch
            }
        }
    }

    private suspend fun saveNow() {
        val current = project ?: return
        val file = _document.value?.file ?: return

        when (val result = repository.save(current.withOverlayFrom(_state.value), file)) {
            is StoreResult.Success -> {
                project = result.value
                autoSave.onSaved()
                _state.update { it.copy(dirty = false) }
            }

            is StoreResult.Failure -> {
                // Stay dirty: a failed save must not look like a successful one.
                _error.value = UiError.SaveFailed(result.failure)
            }
        }
    }
}

/**
 * The palette, with [layer] present.
 *
 * A shape drawn onto a layer the palette has never heard of is invisible to
 * every control that works by layer: hiding `DUVAR-TUGLA-200` would leave the
 * walls on screen, because nothing knew that layer existed. Registering it at
 * the moment it is first drawn on is what makes the grouping real.
 */
private fun List<LayerState>.including(layer: String): List<LayerState> =
    if (any { it.name == layer }) this else this + LayerState(id = layer, name = layer)

/** The palette, with every layer these shapes sit on present. */
private fun List<LayerState>.including(shapes: List<DrawnShape>): List<LayerState> =
    shapes.fold(this) { palette, shape -> palette.including(shape.layer) }

/** Builds the editor snapshot for a freshly opened project. */
private fun PaftaProject.toEditorState(doc: DrawingDocument): EditorState = EditorState(
    projectName = manifest.projectName,
    unitLabel = manifest.source.fileName,
    // A project saved with walls on `DUVAR-TUGLA-200` has to come back with
    // that layer in the palette, or reopening it loses the ability to hide it.
    layers = doc.layers.including(shapes),
    shapes = shapes,
    materials = emptyList(),
    // Filled in by the caller, which is the only place that has both the file
    // and the finished snapshot to count.
    properties = emptyList(),
    selectionTitle = null,
    measurements = measurements.mapNotNull { it.toMeasurement() },
    dirty = false,
    canUndo = false,
    canRedo = false,
)

/**
 * With nothing selected yet, the properties table shows the drawing's own facts
 * — which is more useful than an empty panel and confirms the import worked.
 */
private fun drawingProperties(doc: EditorDocument?, state: EditorState): List<PropertyRow> {
    val bounds = doc?.bounds ?: Aabb.EMPTY
    val size = bounds.size
    // The file's own entities plus everything drawn on top: what the user means
    // by "how many things are on this plan" is all of them, not just the ones
    // that arrived in the file.
    val entities = (doc?.drawing?.entities?.size ?: 0) + state.shapes.toEntities().size

    return buildList {
        add(PropertyRow(R.string.property_entities, entities.toString()))
        add(PropertyRow(R.string.property_layer_count, state.layers.size.toString()))
        if (!bounds.isEmpty) {
            add(PropertyRow(R.string.property_width, formatLength(size.x)))
            add(PropertyRow(R.string.property_height, formatLength(size.y)))
        }
        val unsupported = doc?.unsupportedEntityTypes.orEmpty()
        if (unsupported.isNotEmpty()) {
            // The values are entity names out of the user's own file: data, not
            // interface text.
            add(
                PropertyRow(
                    R.string.property_not_shown,
                    unsupported.sorted().joinToString(", "),
                    numeric = false,
                ),
            )
        }
    }
}

/** Folds the editor's overlay back into the project for saving. */
private fun PaftaProject.withOverlayFrom(state: EditorState): PaftaProject = copy(
    manifest = manifest.copy(projectName = state.projectName),
    layers = state.layers,
    annotations = state.annotations,
    measurements = state.measurements.map { StoredMeasurement.from(it) },
    materials = state.materialOverrides,
    shapes = state.shapes,
)
