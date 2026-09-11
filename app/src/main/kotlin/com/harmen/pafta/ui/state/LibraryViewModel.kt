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
    val error: String? = null,
    /** Set after a successful import so the screen can open the new project. */
    val justImported: ProjectEntry? = null,
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
                    it.copy(importing = false, error = result.failure.message)
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
                is StoreResult.Failure -> _state.update { it.copy(error = result.failure.message) }
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
