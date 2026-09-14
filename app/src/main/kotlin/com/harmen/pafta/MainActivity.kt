package com.harmen.pafta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmen.pafta.data.ProjectRepository
import com.harmen.pafta.data.UpdateService
import com.harmen.pafta.ui.PaftaScreen
import com.harmen.pafta.ui.library.LibraryScreen
import com.harmen.pafta.ui.state.EditorViewModel
import com.harmen.pafta.ui.state.LibraryViewModel
import com.harmen.pafta.ui.state.UpdateState
import com.harmen.pafta.ui.state.UpdateViewModel
import com.harmen.pafta.ui.theme.PaftaTheme
import java.io.File

/**
 * The single activity: the project library, and the editor for one open project.
 *
 * Navigation is a single nullable path rather than a navigation graph. With two
 * destinations a graph is machinery without a payoff, and the open project's
 * path is the only thing worth restoring.
 */
public class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PaftaTheme {
                PaftaApp(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                )
            }
        }
    }
}

@Composable
private fun PaftaApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { ProjectRepository(context) }

    val libraryViewModel: LibraryViewModel = viewModel { LibraryViewModel(repository) }
    val editorViewModel: EditorViewModel = viewModel { EditorViewModel(repository) }
    val updateService = remember { UpdateService(context) }
    val updateViewModel: UpdateViewModel = viewModel { UpdateViewModel(updateService) }

    var openPath by rememberSaveable { mutableStateOf<String?>(null) }

    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val editorState by editorViewModel.state.collectAsStateWithLifecycle()
    val document by editorViewModel.document.collectAsStateWithLifecycle()
    val editorError by editorViewModel.error.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()

    // The two moments the update has to leave the app: the installer, and the
    // one-time settings screen that lets PAFTA reach it. Both are launched here
    // rather than from the view model, which has no business holding a Context.
    LaunchedEffect(updateState) {
        when (val current = updateState) {
            is UpdateState.Ready -> {
                runCatching { context.startActivity(updateService.installIntent(current.file)) }
                updateViewModel.consumeReady()
            }

            UpdateState.NeedsPermission -> Unit
            else -> Unit
        }
    }

    // CAD formats largely have no registered MIME type, so the picker must accept
    // everything; the extension is what decides whether the import is allowed.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) libraryViewModel.import(uri) }

    // Opening straight after making a project — imported or started empty — is
    // what the user means by both buttons.
    LaunchedEffect(libraryState.justImported) {
        libraryState.justImported?.let { entry ->
            openPath = entry.file.path
            libraryViewModel.consumeJustImported()
        }
    }

    // Load whenever the open project changes.
    LaunchedEffect(openPath) {
        openPath?.let { editorViewModel.open(File(it)) }
    }

    // Unsaved work must not depend on a timer the system may not let run, so
    // flush on every stop — task switch, or the process going away.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { editorViewModel.requestFlush() }

    val opened = document
    if (openPath == null || opened == null) {
        // An editor error with no document means the open failed; the library is
        // where the user can act on that.
        LibraryScreen(
            state = libraryState.copy(error = libraryState.error ?: editorError),
            onImport = { picker.launch(arrayOf("*/*")) },
            onOpen = { openPath = it.file.path },
            onDelete = { libraryViewModel.delete(it) },
            onDismissError = {
                libraryViewModel.dismissError()
                editorViewModel.dismissError()
            },
            modifier = modifier,
            updateState = updateState,
            onUpdate = {
                if (updateState == UpdateState.NeedsPermission) {
                    // The banner told the user what to do; this takes them there.
                    runCatching { context.startActivity(updateService.permissionIntent()) }
                    updateViewModel.consumePermissionRequest()
                } else {
                    updateViewModel.start()
                }
            },
            onDismissUpdate = { updateViewModel.dismiss() },
            onBeginNewProject = { libraryViewModel.beginNewProject() },
            onCancelNewProject = { libraryViewModel.cancelNewProject() },
            onCreateProject = { libraryViewModel.createProject(it) },
        )
        // A failed open must not leave the app stuck pointing at a dead project.
        LaunchedEffect(editorError) {
            if (editorError != null) openPath = null
        }
    } else {
        PaftaScreen(
            state = editorState,
            viewModel = editorViewModel,
            drawing = opened.drawing,
            fitBounds = opened.bounds,
            // While a project is open the editor says what went wrong, over the
            // drawing. Sending it to the library would mean closing the project
            // to read why a door could not be placed.
            error = editorError,
            onDismissError = editorViewModel::dismissError,
            onBack = {
                editorViewModel.close { openPath = null }
                libraryViewModel.refresh()
            },
            modifier = modifier,
        )
    }
}
