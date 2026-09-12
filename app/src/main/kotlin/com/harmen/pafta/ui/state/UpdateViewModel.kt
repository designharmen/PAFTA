package com.harmen.pafta.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harmen.pafta.data.UpdateCheck
import com.harmen.pafta.data.UpdateException
import com.harmen.pafta.data.UpdateFailure
import com.harmen.pafta.data.UpdateService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * What the update button is doing, as the screen needs to show it.
 *
 * One button, one line of status: the project owner should never have to know
 * that "check" and "download" are separate steps.
 */
public sealed interface UpdateState {
    /** Nothing has been asked for yet. */
    public data object Idle : UpdateState

    public data object Checking : UpdateState

    /** The running build is the newest published one. */
    public data object UpToDate : UpdateState

    /** [progress] is 0f..1f, or negative while the size is unknown. */
    public data class Downloading(val build: Int, val progress: Float) : UpdateState

    /** Downloaded and handed to Android's installer. */
    public data class Ready(val build: Int, val file: File) : UpdateState

    /** Android has not been told PAFTA may install applications. */
    public data object NeedsPermission : UpdateState

    public data class Failed(val cause: UpdateFailure) : UpdateState
}

/**
 * Drives the update button.
 *
 * One press runs the whole sequence — look, download, hand over — because three
 * buttons for one intention is three chances to stop halfway.
 */
public class UpdateViewModel(private val service: UpdateService) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    public val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Look for a newer build and, if there is one, fetch it. */
    public fun start() {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return

        viewModelScope.launch {
            _state.value = UpdateState.Checking

            when (val result = service.check()) {
                is UpdateCheck.Failed -> _state.value = UpdateState.Failed(result.cause)
                UpdateCheck.UpToDate -> _state.value = UpdateState.UpToDate
                is UpdateCheck.Available -> {
                    val update = result.update
                    // Asked before the download rather than after it, so a
                    // missing permission does not waste 17 MB of someone's data.
                    if (!service.canInstall()) {
                        _state.value = UpdateState.NeedsPermission
                        return@launch
                    }

                    _state.value = UpdateState.Downloading(update.build, 0f)
                    val downloaded = service.download(update) { progress ->
                        _state.value = UpdateState.Downloading(update.build, progress)
                    }

                    downloaded.fold(
                        onSuccess = { file -> _state.value = UpdateState.Ready(update.build, file) },
                        onFailure = { error ->
                            val cause = (error as? UpdateException)?.failure ?: UpdateFailure.SERVER
                            _state.value = UpdateState.Failed(cause)
                        },
                    )
                }
            }
        }
    }

    /** The screen has launched the installer; the button goes quiet again. */
    public fun consumeReady() {
        if (_state.value is UpdateState.Ready) _state.value = UpdateState.Idle
    }

    /** The screen has sent the user to the permission settings. */
    public fun consumePermissionRequest() {
        if (_state.value == UpdateState.NeedsPermission) _state.value = UpdateState.Idle
    }

    public fun dismiss() {
        _state.value = UpdateState.Idle
    }
}
