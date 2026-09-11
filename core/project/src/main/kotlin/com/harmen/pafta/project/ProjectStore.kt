package com.harmen.pafta.project

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/** A project as it appears in the library list. */
public data class ProjectEntry(
    val file: File,
    val manifest: PaftaManifest,
) {
    public val name: String get() = manifest.projectName
    public val format: FileFormat? get() = manifest.format
    public val modifiedAtEpochMs: Long get() = manifest.modifiedAtEpochMs
    public val sizeBytes: Long get() = file.length()
}

/** The outcome of a store operation. */
public sealed interface StoreResult<out T> {
    public data class Success<out T>(val value: T) : StoreResult<T>
    public data class Failure(val failure: StoreFailure) : StoreResult<Nothing>

    public fun valueOrNull(): T? = (this as? Success)?.value
}

/**
 * The on-disk project library.
 *
 * Files are the source of truth: a project *is* its `.pafta` file, and the
 * library is the directory listing. There is deliberately no database mirroring
 * it — a cache of the filesystem can disagree with the filesystem, and when it
 * does the user loses work or sees projects that are not there.
 *
 * Uses only `java.io.File`, so the same code and the same tests cover Android
 * and the JVM.
 *
 * @param root directory holding the `.pafta` files
 * @param clock supplies timestamps; injected so tests are not time-dependent
 */
public class ProjectStore(
    private val root: File,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Refuse imports above this size rather than provoking an OOM. */
    private val importLimitBytes: Long = 256L * 1024 * 1024,
) {

    init {
        root.mkdirs()
    }

    /**
     * Imports a file and returns the project created around it.
     *
     * The payload is copied into the container, so the project keeps working
     * after the user deletes, renames, or moves the file they imported — which
     * on Android also means after the content URI permission lapses.
     */
    public fun import(
        fileName: String,
        payload: ByteArray,
        importedFrom: String? = null,
        projectName: String = defaultProjectName(fileName),
    ): StoreResult<ProjectEntry> {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val format = FileFormat.of(fileName)
            ?: return StoreResult.Failure(StoreFailure.UnknownFormat(extension))

        if (payload.isEmpty()) return StoreResult.Failure(StoreFailure.EmptyFile)
        if (payload.size > importLimitBytes) {
            return StoreResult.Failure(
                StoreFailure.TooLarge(payload.size.toLong(), importLimitBytes),
            )
        }

        // Validate now, while we can still refuse: importing a DXF that turns
        // out to be unparseable would otherwise leave a project that opens onto
        // an empty canvas with no explanation.
        if (format == FileFormat.DXF) {
            val failure = validateDxf(payload)
            if (failure != null) return StoreResult.Failure(failure)
        }

        val now = clock()
        val project = newProject(
            projectName = projectName,
            fileName = fileName,
            payload = payload,
            nowEpochMs = now,
            importedFrom = importedFrom,
        )

        return try {
            val target = uniqueFile(projectName)
            PaftaContainer.write(project, target)
            StoreResult.Success(ProjectEntry(target, project.manifest))
        } catch (e: IOException) {
            StoreResult.Failure(StoreFailure.Io(IoCause.CANNOT_WRITE_PROJECT, e.message))
        }
    }

    /** Imports from a stream, reading it fully before writing anything. */
    public fun import(
        fileName: String,
        payload: InputStream,
        importedFrom: String? = null,
        projectName: String = defaultProjectName(fileName),
    ): StoreResult<ProjectEntry> = try {
        import(fileName, payload.readBytes(), importedFrom, projectName)
    } catch (e: IOException) {
        StoreResult.Failure(StoreFailure.Io(IoCause.CANNOT_READ_FILE, e.message))
    }

    /**
     * Every project in the library, newest first.
     *
     * A file that cannot be read as a project is skipped rather than failing the
     * whole listing: one corrupt project must not make the library unopenable.
     */
    public fun list(): List<ProjectEntry> =
        (root.listFiles() ?: emptyArray())
            .asSequence()
            .filter { it.isFile && it.extension.equals(PAFTA_EXTENSION, ignoreCase = true) }
            .mapNotNull { file ->
                runCatching { ProjectEntry(file, PaftaContainer.readManifest(file)) }.getOrNull()
            }
            .sortedWith(
                compareByDescending<ProjectEntry> { it.modifiedAtEpochMs }
                    .thenBy { it.name.lowercase(Locale.ROOT) },
            )
            .toList()

    /** Files in the library that could not be read, for a "repair" affordance. */
    public fun listUnreadable(): List<File> =
        (root.listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension.equals(PAFTA_EXTENSION, ignoreCase = true) }
            .filter { runCatching { PaftaContainer.readManifest(it) }.isFailure }

    public fun open(file: File): StoreResult<PaftaProject> = try {
        StoreResult.Success(PaftaContainer.read(file))
    } catch (e: PaftaFormatException) {
        StoreResult.Failure(StoreFailure.Io(IoCause.NOT_A_PROJECT, e.message))
    } catch (e: IOException) {
        StoreResult.Failure(StoreFailure.Io(IoCause.CANNOT_READ_FILE, e.message))
    }

    /** Saves a project back to [file], stamping the modified time. */
    public fun save(project: PaftaProject, file: File): StoreResult<PaftaProject> {
        val stamped = project.copy(
            manifest = project.manifest.copy(modifiedAtEpochMs = clock()),
        )
        return try {
            PaftaContainer.write(stamped, file)
            StoreResult.Success(stamped)
        } catch (e: IOException) {
            StoreResult.Failure(StoreFailure.Io(IoCause.CANNOT_WRITE_PROJECT, e.message))
        }
    }

    public fun delete(file: File): Boolean = file.delete()

    /**
     * Renames a project. The display name and the file name are kept in step,
     * but the file is only moved when the new name is actually free — a clash
     * renames the project and leaves the file where it is, rather than failing.
     */
    public fun rename(entry: ProjectEntry, newName: String): StoreResult<ProjectEntry> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            return StoreResult.Failure(StoreFailure.Io(IoCause.NAME_REQUIRED))
        }

        return when (val opened = open(entry.file)) {
            is StoreResult.Failure -> opened
            is StoreResult.Success -> {
                val renamed = opened.value.let {
                    it.copy(manifest = it.manifest.copy(projectName = trimmed))
                }
                val desired = File(root, "${sanitise(trimmed)}.$PAFTA_EXTENSION")
                val target = if (desired == entry.file || !desired.exists()) desired else entry.file

                when (val saved = save(renamed, target)) {
                    is StoreResult.Failure -> saved
                    is StoreResult.Success -> {
                        if (target != entry.file) entry.file.delete()
                        StoreResult.Success(ProjectEntry(target, saved.value.manifest))
                    }
                }
            }
        }
    }

    /** A file name in the library that is not taken, derived from [projectName]. */
    internal fun uniqueFile(projectName: String): File {
        val base = sanitise(projectName)
        var candidate = File(root, "$base.$PAFTA_EXTENSION")
        var n = 2
        while (candidate.exists()) {
            candidate = File(root, "$base-$n.$PAFTA_EXTENSION")
            n++
        }
        return candidate
    }

    private fun validateDxf(payload: ByteArray): StoreFailure? = try {
        val drawing = com.harmen.pafta.dxf.DxfReader.read(payload.inputStream())
        if (drawing.entities.isEmpty()) {
            StoreFailure.Unreadable(FileFormat.DXF, UnreadableReason.NO_DRAWABLE_CONTENT)
        } else {
            null
        }
    } catch (e: Exception) {
        StoreFailure.Unreadable(FileFormat.DXF, UnreadableReason.MALFORMED)
    }

    public companion object {
        /**
         * Turns a project name into a safe file name: no separators, no device
         * names, no leading dot, and short enough for any filesystem.
         */
        internal fun sanitise(name: String): String {
            val cleaned = name
                .map { if (it.isLetterOrDigit() || it == '-' || it == '_' || it == ' ') it else '-' }
                .joinToString("")
                .replace(Regex("\\s+"), " ")
                .trim()
                .trim('.', '-')
                .take(80)
            return cleaned.ifEmpty { "project" }
        }

        /** `villa-ground-floor.dxf` -> `villa-ground-floor`. */
        internal fun defaultProjectName(fileName: String): String {
            val stem = fileName.substringBeforeLast('.', fileName).trim()
            return stem.ifEmpty { "Untitled" }
        }
    }
}
