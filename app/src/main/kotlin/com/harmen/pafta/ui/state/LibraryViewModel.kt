package com.harmen.pafta.ui.state

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harmen.pafta.data.ProjectRepository
import com.harmen.pafta.project.ProjectEntry
import com.harmen.pafta.project.StoreResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** What the library screen draws. */
public data class LibraryState(
    val projects: List<ProjectEntry> = emptyList(),
    val unreadable: List<File> = emptyList(),
    val loading: Boolean = true,
    val importing: Boolean = false,
    /** A failure to show the user; cleared when they dismiss it. */
    val error: UiError? = null,
    /**
     * Set after a project is made — imported or started empty — so the screen
     * can open it straight away.
     */
    val justImported: ProjectEntry? = null,
    /** True while the "new project" sheet is asking for a name. */
    val naming: Boolean = false,
)

/** Drives the project library: list, import, rename, delete. */
public class LibraryViewModel(private val repository: ProjectRepository) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    public val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        refresh()
    }

    public fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val projects = repository.list()
            val unreadable = repository.listUnreadable()
            _state.update {
                it.copy(projects = projects, unreadable = unreadable, loading = false)
            }
        }
    }

    /** Imports the picked document, then reports it so the screen can open it. */
    public fun import(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(importing = true, error = null) }
            when (val result = repository.import(uri)) {
                is StoreResult.Success -> {
                    _state.update {
                        it.copy(importing = false, justImported = result.value)
                    }
                    refresh()
                }

                is StoreResult.Failure -> _state.update {
                    it.copy(importing = false, error = UiError.Store(result.failure))
                }
            }
        }
    }

    /** Opens and closes the little sheet that asks what the project is called. */
    public fun beginNewProject() {
        _state.update { it.copy(naming = true, error = null) }
    }

    public fun cancelNewProject() {
        _state.update { it.copy(naming = false) }
    }

    /**
     * Starts an empty project and reports it, so the screen opens it.
     *
     * The same door as an import: whatever made the project, the user ends up
     * inside it rather than back at a list wondering whether it worked.
     */
    public fun createProject(projectName: String) {
        viewModelScope.launch {
            _state.update { it.copy(naming = false, importing = true, error = null) }
            when (val result = repository.create(projectName)) {
                is StoreResult.Success -> {
                    _state.update { it.copy(importing = false, justImported = result.value) }
                    refresh()
                }

                is StoreResult.Failure -> _state.update {
                    it.copy(importing = false, error = UiError.Store(result.failure))
                }
            }
        }
    }

    public fun delete(entry: ProjectEntry) {
        viewModelScope.launch {
            repository.delete(entry.file)
            refresh()
        }
    }

    public fun rename(entry: ProjectEntry, newName: String) {
        viewModelScope.launch {
            when (val result = repository.rename(entry, newName)) {
                is StoreResult.Success -> refresh()
                is StoreResult.Failure ->
                    _state.update { it.copy(error = UiError.Store(result.failure)) }
            }
        }
    }

    public fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /** Called once the screen has acted on [LibraryState.justImported]. */
    public fun consumeJustImported() {
        _state.update { it.copy(justImported = null) }
    }
}
