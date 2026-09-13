package com.harmen.pafta.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harmen.pafta.R
import com.harmen.pafta.data.ProjectRepository
import com.harmen.pafta.dxf.DxfDrawing
import com.harmen.pafta.dxf.near
import com.harmen.pafta.dxf.snapSegments
import com.harmen.pafta.geometry.Segment2
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import com.harmen.pafta.measure.MeasurementEngine
import com.harmen.pafta.measure.MeasurementKind
import com.harmen.pafta.measure.snap
import com.harmen.pafta.project.AnnotationKind
import com.harmen.pafta.project.AutoSavePolicy
import com.harmen.pafta.project.DrawingDocument
import com.harmen.pafta.project.DrawnShape
import com.harmen.pafta.project.PaftaProject
import com.harmen.pafta.project.StoreResult
import com.harmen.pafta.project.StoredMeasurement
import com.harmen.pafta.project.UndoStack
import com.harmen.pafta.project.pick
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
                        unsupportedEntityTypes = doc.unsupportedEntityTypes,
                    )
                    _state.value = loaded.toEditorState(doc)
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
        val next = transform(current)
        if (next == current) return

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
            Tool.WALL, Tool.LINE, Tool.RECTANGLE, Tool.CIRCLE -> drawPick(point, toleranceMm)
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

        val shape = shapeBetween(first, landed)
        _state.update { it.copy(pendingPicks = emptyList()) }
        if (shape != null) {
            edit { s -> s.copy(shapes = s.shapes + shape, selectedShapeId = shape.id) }
            rebuildSnapCandidates()
        }
    }

    private fun shapeBetween(from: Vec2, to: Vec2): DrawnShape? {
        // Unique within the project: the clock separates sessions, the counter
        // separates shapes drawn inside one. A repeated id would mean selecting
        // one shape and deleting another.
        val id = "s" + clock() + "-" + (nextShape++)
        val a = Vec3(from.x, from.y, 0.0)
        val b = Vec3(to.x, to.y, 0.0)

        val shape = when (_state.value.activeTool) {
            Tool.WALL -> DrawnShape.Wall(id, a, b, _state.value.wallThicknessMm)
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
        val hit = _state.value.shapes.pick(point, toleranceMm)
        _state.update { it.copy(selectedShapeId = hit?.id) }
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

    private fun snapped(point: Vec2, toleranceMm: Double): Vec2 {
        val nearby = snapCandidates.near(point, toleranceMm)
        val result = snap(
            pick = point,
            segments = nearby,
            tolerance = toleranceMm,
            gridSpacing = if (_state.value.gridVisible) _state.value.gridSpacingMm else null,
        )
        return result.point.toVec2()
    }

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

    private fun rebuildSnapCandidates() {
        val drawing = _document.value?.drawing
        snapCandidates = if (drawing == null) {
            emptyList()
        } else {
            val visible = _state.value.layers.filter { it.visible }.map { it.name }.toSet()
            // The drawing here is the file's; what the user drew since opening
            // it is snapped to as well, so a second wall meets the first.
            val drawn = _state.value.shapes.flatMap { it.toEntities() }
            val all =
                if (drawn.isEmpty()) drawing else drawing.copy(entities = drawing.entities + drawn)
            all.snapSegments(visibleLayers = visible.ifEmpty { null })
        }
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

/** Builds the editor snapshot for a freshly opened project. */
private fun PaftaProject.toEditorState(doc: DrawingDocument): EditorState = EditorState(
    projectName = manifest.projectName,
    unitLabel = manifest.source.fileName,
    layers = doc.layers,
    shapes = shapes,
    materials = emptyList(),
    properties = drawingProperties(doc),
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
private fun drawingProperties(doc: DrawingDocument): List<PropertyRow> {
    val size = doc.bounds.size
    return buildList {
        add(PropertyRow(R.string.property_entities, doc.entityCount.toString()))
        add(PropertyRow(R.string.property_layer_count, doc.layers.size.toString()))
        if (!doc.bounds.isEmpty) {
            add(PropertyRow(R.string.property_width, formatLength(size.x)))
            add(PropertyRow(R.string.property_height, formatLength(size.y)))
        }
        if (doc.unsupportedEntityTypes.isNotEmpty()) {
            // The values are entity names out of the user's own file: data, not
            // interface text.
            add(
                PropertyRow(
                    R.string.property_not_shown,
                    doc.unsupportedEntityTypes.sorted().joinToString(", "),
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
