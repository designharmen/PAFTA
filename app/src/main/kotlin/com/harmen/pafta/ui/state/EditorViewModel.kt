package com.harmen.pafta.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harmen.pafta.R
import com.harmen.pafta.data.ProjectRepository
import com.harmen.pafta.dxf.DxfDrawing
import com.harmen.pafta.project.AnnotationKind
import com.harmen.pafta.project.AutoSavePolicy
import com.harmen.pafta.project.DrawingDocument
import com.harmen.pafta.project.PaftaProject
import com.harmen.pafta.project.StoredMeasurement
import com.harmen.pafta.project.StoreResult
import com.harmen.pafta.project.UndoStack
import com.harmen.pafta.units.formatLength
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

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

    public fun selectDimensionPreset(preset: Double?) {
        _state.update { it.copy(selectedPreset = preset, activeTool = Tool.DIMENSIONS) }
    }

    public fun toggleGrid() {
        _state.update { it.copy(gridVisible = !it.gridVisible) }
    }

    // --- Document edits: recorded and auto-saved -----------------------------
    public fun setLayerVisible(layerId: String, visible: Boolean) {
        edit { s ->
            s.copy(layers = s.layers.map { if (it.id == layerId) it.copy(visible = visible) else it })
        }
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
        )
        markEdited()
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
)
