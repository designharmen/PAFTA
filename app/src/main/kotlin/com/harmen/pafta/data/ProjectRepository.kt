package com.harmen.pafta.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.harmen.pafta.project.DrawingDocument
import com.harmen.pafta.project.PaftaProject
import com.harmen.pafta.project.ProjectEntry
import com.harmen.pafta.project.ProjectStore
import com.harmen.pafta.project.StoreFailure
import com.harmen.pafta.project.StoreResult
import com.harmen.pafta.project.openAsDrawing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The Android adapter over [ProjectStore].
 *
 * Everything that decides anything — naming, validation, listing order, save
 * semantics — lives in `core:project` and is unit-tested there. This class only
 * supplies the two things the JVM cannot: a directory from `Context`, and bytes
 * from a `content://` URI.
 */
public class ProjectRepository(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Projects live in the app's own files directory rather than in shared
     * storage: no runtime permission, no scoped-storage special cases, and the
     * library cannot be half-readable because a URI grant lapsed.
     */
    private val libraryDir: File = File(appContext.filesDir, "projects")

    private val store = ProjectStore(libraryDir)

    /** Lists the library. Touches the disk, so it is off the main thread. */
    public suspend fun list(): List<ProjectEntry> = withContext(Dispatchers.IO) { store.list() }

    /** `.pafta` files in the library that could not be read. */
    public suspend fun listUnreadable(): List<File> =
        withContext(Dispatchers.IO) { store.listUnreadable() }

    /**
     * Imports the document behind [uri].
     *
     * The bytes are copied into a `.pafta` immediately, so the project keeps
     * working after the URI permission lapses — which it will, as soon as the
     * process restarts without a persisted grant.
     */
    public suspend fun import(uri: Uri): StoreResult<ProjectEntry> = withContext(Dispatchers.IO) {
        val fileName = displayName(uri)
            ?: return@withContext StoreResult.Failure(
                StoreFailure.Io("could not read the name of the selected file"),
            )

        try {
            appContext.contentResolver.openInputStream(uri).use { input ->
                if (input == null) {
                    StoreResult.Failure(StoreFailure.Io("the selected file could not be opened"))
                } else {
                    store.import(fileName, input, importedFrom = uri.toString())
                }
            }
        } catch (e: SecurityException) {
            StoreResult.Failure(
                StoreFailure.Io("PAFTA was not granted permission to read that file"),
            )
        } catch (e: Exception) {
            StoreResult.Failure(StoreFailure.Io(e.message ?: "the file could not be read"))
        }
    }

    public suspend fun open(file: File): StoreResult<PaftaProject> =
        withContext(Dispatchers.IO) { store.open(file) }

    /** Opens a project and parses its payload as a 2D drawing in one step. */
    public suspend fun openAsDrawing(file: File): StoreResult<Pair<PaftaProject, DrawingDocument>> =
        withContext(Dispatchers.IO) {
            when (val opened = store.open(file)) {
                is StoreResult.Failure -> opened
                is StoreResult.Success -> when (val drawing = opened.value.openAsDrawing()) {
                    is StoreResult.Failure -> drawing
                    is StoreResult.Success -> StoreResult.Success(opened.value to drawing.value)
                }
            }
        }

    public suspend fun save(project: PaftaProject, file: File): StoreResult<PaftaProject> =
        withContext(Dispatchers.IO) { store.save(project, file) }

    public suspend fun delete(file: File): Boolean = withContext(Dispatchers.IO) { store.delete(file) }

    public suspend fun rename(entry: ProjectEntry, newName: String): StoreResult<ProjectEntry> =
        withContext(Dispatchers.IO) { store.rename(entry, newName) }

    /**
     * The document's file name, which is the only place the extension comes
     * from. CAD formats mostly have no registered MIME type, so the picker has
     * to accept everything and the extension is what routes the import.
     */
    private fun displayName(uri: Uri): String? {
        appContext.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor: Cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && !cursor.isNull(index)) {
                        return cursor.getString(index)
                    }
                }
            }
        // Fall back to the last path segment for providers that expose no name.
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
