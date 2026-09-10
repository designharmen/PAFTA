package com.harmen.pafta.project

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Raised when a `.pafta` file cannot be read as a PAFTA project. */
public class PaftaFormatException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/** Entry names inside the container. */
internal object Entries {
    const val MANIFEST = "manifest.json"
    const val ANNOTATIONS = "annotations.json"
    const val MEASUREMENTS = "measurements.json"
    const val LAYERS = "layers.json"
    const val MATERIALS = "materials.json"
    const val CAMERAS = "cameras.json"
    const val THUMBNAIL = "thumbnail.png"
    const val PAYLOAD_DIR = "payload/"
}

private val json = Json {
    prettyPrint = true
    encodeDefaults = true
    // Tolerate fields a newer build added, so a project saved by a later
    // version still opens here instead of failing outright.
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}

/**
 * Reads and writes the `.pafta` container.
 *
 * The container is a plain ZIP: the source file is stored under `payload/` and
 * every PAFTA overlay is a JSON sibling. That choice is deliberate — a project
 * can be inspected, diffed, and recovered with any unzip tool, and a corrupt
 * overlay never costs the user their original drawing.
 */
public object PaftaContainer {

    public fun write(project: PaftaProject, file: File) {
        file.parentFile?.mkdirs()
        // Write to a sibling and move into place so that an interrupted save
        // (or a full disk) cannot destroy the previous good file.
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            temp.outputStream().buffered().use { write(project, it) }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    public fun write(project: PaftaProject, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)

            zip.text(Entries.MANIFEST, json.encodeToString(project.manifest))
            zip.text(Entries.ANNOTATIONS, json.encodeToString(project.annotations))
            zip.text(Entries.MEASUREMENTS, json.encodeToString(project.measurements))
            zip.text(Entries.LAYERS, json.encodeToString(project.layers))
            zip.text(Entries.MATERIALS, json.encodeToString(project.materials))
            zip.text(Entries.CAMERAS, json.encodeToString(project.cameras))

            project.thumbnail?.let { zip.bytes(Entries.THUMBNAIL, it) }
            zip.bytes(Entries.PAYLOAD_DIR + project.manifest.source.fileName, project.payload)
            zip.finish()
        }
    }

    public fun read(file: File): PaftaProject =
        file.inputStream().buffered().use { read(it) }

    public fun read(input: InputStream): PaftaProject {
        var manifest: PaftaManifest? = null
        var annotations: List<Annotation> = emptyList()
        var measurements: List<StoredMeasurement> = emptyList()
        var layers: List<LayerState> = emptyList()
        var materials: List<MaterialOverride> = emptyList()
        var cameras: List<CameraPreset> = emptyList()
        var thumbnail: ByteArray? = null
        var payload: ByteArray? = null

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                when {
                    name == Entries.MANIFEST ->
                        manifest = decode(name) { json.decodeFromString(zip.readText()) }

                    name == Entries.ANNOTATIONS ->
                        annotations = decode(name) { json.decodeFromString(zip.readText()) }

                    name == Entries.MEASUREMENTS ->
                        measurements = decode(name) { json.decodeFromString(zip.readText()) }

                    name == Entries.LAYERS ->
                        layers = decode(name) { json.decodeFromString(zip.readText()) }

                    name == Entries.MATERIALS ->
                        materials = decode(name) { json.decodeFromString(zip.readText()) }

                    name == Entries.CAMERAS ->
                        cameras = decode(name) { json.decodeFromString(zip.readText()) }

                    name == Entries.THUMBNAIL -> thumbnail = zip.readBytes()

                    name.startsWith(Entries.PAYLOAD_DIR) -> payload = zip.readBytes()
                }
            }
        }

        val m = manifest ?: throw PaftaFormatException("not a PAFTA project: ${Entries.MANIFEST} is missing")
        if (m.formatVersion > PAFTA_FORMAT_VERSION) {
            throw PaftaFormatException(
                "this project was saved by a newer version of PAFTA " +
                    "(format ${m.formatVersion}, this build reads up to $PAFTA_FORMAT_VERSION)",
            )
        }
        val p = payload ?: throw PaftaFormatException("not a PAFTA project: the payload is missing")

        return PaftaProject(
            manifest = m,
            payload = p,
            annotations = annotations,
            measurements = measurements,
            layers = layers,
            materials = materials,
            cameras = cameras,
            thumbnail = thumbnail,
        )
    }

    /** Reads just the manifest, for the file manager's project list. */
    public fun readManifest(file: File): PaftaManifest =
        file.inputStream().buffered().use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == Entries.MANIFEST) {
                        return@use decode(Entries.MANIFEST) { json.decodeFromString(zip.readText()) }
                    }
                }
                throw PaftaFormatException("not a PAFTA project: ${Entries.MANIFEST} is missing")
            }
        }

    private fun <T> decode(entryName: String, block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw PaftaFormatException("$entryName is not valid PAFTA JSON", e)
    }

    private fun ZipOutputStream.text(name: String, content: String) {
        bytes(name, content.toByteArray(Charsets.UTF_8))
    }

    private fun ZipOutputStream.bytes(name: String, content: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(content)
        closeEntry()
    }

    private fun ZipInputStream.readText(): String = readBytes().toString(Charsets.UTF_8)
}

/** Builds a fresh project around an imported file. */
public fun newProject(
    projectName: String,
    fileName: String,
    payload: ByteArray,
    nowEpochMs: Long,
    importedFrom: String? = null,
    units: UnitPreferences = UnitPreferences(),
): PaftaProject = PaftaProject(
    manifest = PaftaManifest(
        projectName = projectName,
        source = SourceRef(
            fileName = fileName,
            format = fileName.substringAfterLast('.', "").lowercase(),
            sizeBytes = payload.size.toLong(),
            importedFrom = importedFrom,
        ),
        units = units,
        createdAtEpochMs = nowEpochMs,
        modifiedAtEpochMs = nowEpochMs,
    ),
    payload = payload,
)
