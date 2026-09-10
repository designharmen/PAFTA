package com.harmen.pafta.ui.state

import androidx.lifecycle.ViewModel
import com.harmen.pafta.project.AnnotationKind
import com.harmen.pafta.project.LayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the editor snapshot and the undo/redo stacks.
 *
 * Undo is snapshot-based rather than command-based: the states PAFTA holds are
 * small (layer flags, annotations, measurements — never the model payload), and
 * a snapshot stack cannot drift out of sync with the document the way an
 * inverse-command stack can.
 */
public class EditorViewModel(initial: EditorState = EditorState()) : ViewModel() {

    private val _state = MutableStateFlow(initial)
    public val state: StateFlow<EditorState> = _state.asStateFlow()

    private val undoStack = ArrayDeque<EditorState>()
    private val redoStack = ArrayDeque<EditorState>()

    /** Deepest undo history kept; beyond this the oldest entry is dropped. */
    private val historyLimit = 64

    // --- Tool and tab selection (not undoable: they change no document state) -
    public fun selectTool(tool: Tool) {
        _state.update { it.copy(activeTool = tool, annotationTool = null) }
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

    // --- Document edits (undoable) ------------------------------------------
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
        edit { s ->
            s.copy(materials = s.materials.map { it.copy(selected = it.id == materialId) })
        }
    }

    public fun replaceLayers(layers: List<LayerState>) {
        edit { it.copy(layers = layers) }
    }

    /** Applies an undoable change and records the previous snapshot. */
    public fun edit(transform: (EditorState) -> EditorState) {
        _state.update { current ->
            val next = transform(current)
            if (next == current) return@update current
            undoStack.addLast(current)
            if (undoStack.size > historyLimit) undoStack.removeFirst()
            redoStack.clear()
            next.copy(canUndo = true, canRedo = false, dirty = true)
        }
    }

    public fun undo() {
        _state.update { current ->
            val previous = undoStack.removeLastOrNull() ?: return@update current
            redoStack.addLast(current)
            previous.copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = true,
                dirty = true,
                // Transient selections belong to the live session, not to history.
                activeTool = current.activeTool,
                activeTab = current.activeTab,
            )
        }
    }

    public fun redo() {
        _state.update { current ->
            val next = redoStack.removeLastOrNull() ?: return@update current
            undoStack.addLast(current)
            next.copy(
                canUndo = true,
                canRedo = redoStack.isNotEmpty(),
                dirty = true,
                activeTool = current.activeTool,
                activeTab = current.activeTab,
            )
        }
    }

    /** Called after a successful save. */
    public fun markSaved() {
        _state.update { it.copy(dirty = false) }
    }
}
