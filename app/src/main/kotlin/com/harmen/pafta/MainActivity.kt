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
import com.harmen.pafta.ui.PaftaScreen
import com.harmen.pafta.ui.library.LibraryScreen
import com.harmen.pafta.ui.state.EditorViewModel
import com.harmen.pafta.ui.state.LibraryViewModel
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

    var openPath by rememberSaveable { mutableStateOf<String?>(null) }

    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val editorState by editorViewModel.state.collectAsStateWithLifecycle()
    val document by editorViewModel.document.collectAsStateWithLifecycle()
    val editorError by editorViewModel.error.collectAsStateWithLifecycle()

    // CAD formats largely have no registered MIME type, so the picker must accept
    // everything; the extension is what decides whether the import is allowed.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) libraryViewModel.import(uri) }

    // Opening straight after an import is what the user means by "import".
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
            onBack = {
                editorViewModel.close { openPath = null }
                libraryViewModel.refresh()
            },
            modifier = modifier,
        )
    }
}
