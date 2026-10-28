package com.harmen.pafta

import android.content.Intent
import android.net.Uri
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
import com.harmen.pafta.project.ProjectEntry
import com.harmen.pafta.project.ViewerKind
import com.harmen.pafta.ui.ModelScreen
import com.harmen.pafta.ui.PaftaScreen
import com.harmen.pafta.ui.library.LibraryScreen
import com.harmen.pafta.ui.state.EditorViewModel
import com.harmen.pafta.ui.state.LibraryViewModel
import com.harmen.pafta.ui.state.ModelViewModel
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

    /**
     * The drawing another app asked PAFTA to open, until it has been taken in.
     *
     * PAFTA tells Android it can open files, so tapping a drawing in a file
     * manager or a downloads list offers PAFTA — and until now, choosing it
     * opened the library and did nothing at all, which from the outside is
     * indistinguishable from the file being unopenable. The file arrives here.
     */
    private val incomingFile = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingFile.value = documentOf(intent)

        setContent {
            PaftaTheme {
                PaftaApp(
                    incoming = incomingFile.value,
                    onIncomingTaken = {
                        incomingFile.value = null
                        // Forget the request as well, so a restart does not
                        // import the same file a second time.
                        setIntent(Intent())
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                )
            }
        }
    }

    /** A second file tapped while PAFTA is already running. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingFile.value = documentOf(intent)
    }

    private fun documentOf(intent: Intent?): Uri? =
        if (intent?.action == Intent.ACTION_VIEW) intent.data else null
}

@Composable
private fun PaftaApp(
    incoming: Uri?,
    onIncomingTaken: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember { ProjectRepository(context) }

    val libraryViewModel: LibraryViewModel = viewModel { LibraryViewModel(repository) }
    val editorViewModel: EditorViewModel = viewModel { EditorViewModel(repository) }
    val modelViewModel: ModelViewModel = viewModel { ModelViewModel(repository) }
    val updateService = remember { UpdateService(context) }
    val updateViewModel: UpdateViewModel = viewModel { UpdateViewModel(updateService) }

    var openPath by rememberSaveable { mutableStateOf<String?>(null) }

    /**
     * Whether the open project holds a model rather than a drawing.
     *
     * Remembered alongside the path and not worked out from it: every project
     * is a `.pafta` file whatever was imported into it, so the path says
     * nothing about what is inside. It comes from the library row the project
     * was opened from, and is saved with the path so that coming back to the
     * app after it was put away opens the right screen.
     */
    var openIsModel by rememberSaveable { mutableStateOf(false) }

    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val editorState by editorViewModel.state.collectAsStateWithLifecycle()
    val modelState by modelViewModel.state.collectAsStateWithLifecycle()
    val modelError by modelViewModel.error.collectAsStateWithLifecycle()
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

    // A file tapped in another app goes in exactly as one chosen from PAFTA's
    // own picker: taken into a project, then opened — which the effect below
    // does for everything the library imports.
    LaunchedEffect(incoming) {
        if (incoming != null) {
            libraryViewModel.import(incoming)
            onIncomingTaken()
        }
    }

    /** Opens a library row, remembering which of the two screens it needs. */
    fun open(entry: ProjectEntry) {
        openIsModel = entry.format?.viewer == ViewerKind.MODEL
        openPath = entry.file.path
    }

    // Opening straight after making a project — imported or started empty — is
    // what the user means by both buttons.
    LaunchedEffect(libraryState.justImported) {
        libraryState.justImported?.let { entry ->
            open(entry)
            libraryViewModel.consumeJustImported()
        }
    }

    // Load whenever the open project changes, into whichever of the two the
    // project turned out to be.
    LaunchedEffect(openPath, openIsModel) {
        val path = openPath ?: return@LaunchedEffect
        if (openIsModel) modelViewModel.open(File(path)) else editorViewModel.open(File(path))
    }

    // Unsaved work must not depend on a timer the system may not let run, so
    // flush on every stop — task switch, or the process going away.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { editorViewModel.requestFlush() }

    val opened = document
    if (openPath != null && openIsModel) {
        ModelScreen(
            state = modelState,
            viewModel = modelViewModel,
            onBack = {
                modelViewModel.close()
                openPath = null
                libraryViewModel.refresh()
            },
            modifier = modifier,
        )
        // A model that will not read must not leave the app looking at nothing.
        LaunchedEffect(modelError) {
            if (modelError != null) openPath = null
        }
    } else if (openPath == null || opened == null) {
        // An editor error with no document means the open failed; the library is
        // where the user can act on that.
        LibraryScreen(
            state = libraryState.copy(
                error = libraryState.error ?: editorError ?: modelError,
            ),
            onImport = { picker.launch(arrayOf("*/*")) },
            onOpen = { open(it) },
            onDelete = { libraryViewModel.delete(it) },
            onDismissError = {
                libraryViewModel.dismissError()
                editorViewModel.dismissError()
                modelViewModel.dismissError()
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
            modelFaces = opened.modelFaces,
            onDismissError = editorViewModel::dismissError,
            onBack = {
                editorViewModel.close { openPath = null }
                libraryViewModel.refresh()
            },
            modifier = modifier,
        )
    }
}
