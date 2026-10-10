package com.harmen.pafta.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harmen.pafta.data.ProjectRepository
import com.harmen.pafta.project.LayerState
import com.harmen.pafta.project.ModelDocument
import com.harmen.pafta.project.StoreResult
import com.harmen.pafta.project.layerStates
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the model screen draws. */
public data class ModelState(
    val projectName: String = "",
    val document: ModelDocument? = null,
    /** The model's parts, each of which can be hidden. */
    val layers: List<LayerState> = emptyList(),
    val loading: Boolean = true,
)

/**
 * Drives a project that holds somebody else's 3D model rather than a drawing.
 *
 * Far smaller than the editor, and deliberately: nothing here edits anything.
 * An imported model is a reference — you look at it, you turn it round, you
 * hide the parts in the way. PAFTA never rewrites the file it was given, and a
 * model is the clearest case of that rule there is.
 */
public class ModelViewModel(private val repository: ProjectRepository) : ViewModel() {

    private val _state = MutableStateFlow(ModelState())
    public val state: StateFlow<ModelState> = _state.asStateFlow()

    private val _error = MutableStateFlow<UiError?>(null)
    public val error: StateFlow<UiError?> = _error.asStateFlow()

    public fun open(file: File) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val result = repository.openAsModel(file)) {
                is StoreResult.Failure -> {
                    _error.value = UiError.Store(result.failure)
                    _state.value = ModelState(loading = false)
                }

                is StoreResult.Success -> {
                    val (project, document) = result.value
                    _error.value = null
                    _state.value = ModelState(
                        projectName = project.manifest.projectName,
                        document = document,
                        layers = document.layerStates(project.layers),
                        loading = false,
                    )
                }
            }
        }
    }

    /** Hides or shows one part of the model. */
    public fun setLayerVisible(layerId: String, visible: Boolean) {
        _state.update { current ->
            current.copy(
                layers = current.layers.map {
                    if (it.id == layerId) it.copy(visible = visible) else it
                },
            )
        }
    }

    public fun close() {
        _state.value = ModelState(loading = false)
        _error.value = null
    }

    public fun dismissError() {
        _error.value = null
    }
}
