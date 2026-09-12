package com.harmen.pafta.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.harmen.pafta.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A build that is newer than the one running. */
public data class AvailableUpdate(
    val build: Int,
    val downloadUrl: String,
    val sizeBytes: Long,
)

/** Why an update could not be fetched. Data, not prose — `UiText` says it in Turkish. */
public enum class UpdateFailure {
    /** The device has no working connection, or the host could not be reached. */
    NO_NETWORK,

    /** The server answered, but no build has been published yet. */
    NO_RELEASE,

    /** The server answered with an error, or with something unreadable. */
    SERVER,

    /** The download could not be written to the device. */
    CANNOT_SAVE,
}

/** The outcome of looking for a newer build. */
public sealed interface UpdateCheck {
    public data class Available(val update: AvailableUpdate) : UpdateCheck
    public data object UpToDate : UpdateCheck
    public data class Failed(val cause: UpdateFailure) : UpdateCheck
}

/** Carries an [UpdateFailure] out of a download. */
public class UpdateException(public val failure: UpdateFailure) : Exception()

/**
 * Finding and downloading a newer build of PAFTA.
 *
 * The point is to replace a seven-step chore — open a browser, find the run,
 * download a zip, extract it, move it to the tablet, uninstall the old app,
 * install — with one button. Android will not let any app install another
 * silently, so the final confirmation stays with the user; everything before it
 * is now automatic.
 *
 * Nothing is added to the build for this: `HttpURLConnection` is in the
 * platform and `kotlinx.serialization` is already a dependency.
 */
public class UpdateService(
    private val context: Context,
    private val currentBuild: String = BuildConfig.BUILD_LABEL,
    private val releaseUrl: String = LATEST_RELEASE_URL,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Is this build older than the newest published one? */
    public suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        val body = try {
            fetch(releaseUrl)
        } catch (e: IOException) {
            return@withContext UpdateCheck.Failed(UpdateFailure.NO_NETWORK)
        }
        if (body == null) return@withContext UpdateCheck.Failed(UpdateFailure.SERVER)

        val release = try {
            json.decodeFromString<ReleaseJson>(body)
        } catch (e: Exception) {
            return@withContext UpdateCheck.Failed(UpdateFailure.SERVER)
        }

        val published = buildNumberOf(release.tag)
        val asset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        if (published == null || asset == null) {
            return@withContext UpdateCheck.Failed(UpdateFailure.NO_RELEASE)
        }

        val running = currentBuild.toIntOrNull()
        // A hand-made build labels itself `yerel` and carries no number, so it
        // is never told it is up to date — that would be a guess.
        if (running != null && published <= running) {
            UpdateCheck.UpToDate
        } else {
            UpdateCheck.Available(
                AvailableUpdate(published, asset.downloadUrl, asset.sizeBytes),
            )
        }
    }

    /**
     * Downloads the update into the app's own cache.
     *
     * [onProgress] receives 0f..1f, or -1f while the size is unknown, so the
     * screen can show something moving rather than a frozen button.
     */
    public suspend fun download(
        update: AvailableUpdate,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, CACHE_DIRECTORY)
        val target = File(directory, "PAFTA-yapim-${update.build}.apk")
        val partial = File(directory, target.name + ".indiriliyor")

        directory.mkdirs()
        // Leftovers from an interrupted attempt, and older builds nobody needs
        // any more: this cache is not a downloads folder.
        directory.listFiles()?.forEach { if (it != target) it.delete() }

        val connection = try {
            openConnection(update.downloadUrl, "application/octet-stream")
        } catch (e: IOException) {
            return@withContext Result.failure(UpdateException(UpdateFailure.NO_NETWORK))
        }

        try {
            if (connection.responseCode !in 200..299) {
                return@withContext Result.failure(UpdateException(UpdateFailure.SERVER))
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: update.sizeBytes
            var read = 0L

            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        onProgress(if (total > 0) read.toFloat() / total else -1f)
                    }
                }
            }
        } catch (e: IOException) {
            partial.delete()
            // A write that fails once the body is flowing is the device running
            // out of room far more often than it is the network.
            val cause =
                if (wroteSomething(partial)) UpdateFailure.CANNOT_SAVE else UpdateFailure.NO_NETWORK
            return@withContext Result.failure(UpdateException(cause))
        } finally {
            connection.disconnect()
        }

        // The installer only ever sees a complete file: the download writes to a
        // second name and is renamed once it is whole.
        val moved = partial.renameTo(target) || run {
            val copied = runCatching { partial.copyTo(target, overwrite = true) }.isSuccess
            partial.delete()
            copied
        }
        if (moved) {
            Result.success(target)
        } else {
            Result.failure(UpdateException(UpdateFailure.CANNOT_SAVE))
        }
    }

    /** True when Android will let PAFTA hand an APK to the installer. */
    public fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The one-time settings screen where that permission is granted. */
    public fun permissionIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

    /** Hands the downloaded file to Android's installer. */
    public fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.dosyalar", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** True when something was already written, so the failure is about storage. */
    private fun wroteSomething(partial: File): Boolean = partial.exists() && partial.length() > 0

    private fun fetch(url: String): String? {
        val connection = openConnection(url, "application/vnd.github+json")
        return try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
        }

    public companion object {
        /**
         * The build number lives in the release tag, `yapim-12`, so the app can
         * compare it with its own stamp without parsing a version name.
         */
        internal fun buildNumberOf(tag: String): Int? =
            tag.substringAfterLast('-', "").trim().toIntOrNull()

        private const val TIMEOUT_MS = 20_000
        private const val CACHE_DIRECTORY = "guncelleme"

        /** GitHub answers this anonymously while the repository is public. */
        public const val LATEST_RELEASE_URL: String =
            "https://api.github.com/repos/designharmen/PAFTA/releases/latest"
    }
}

@Serializable
private data class ReleaseJson(
    @SerialName("tag_name") val tag: String = "",
    val assets: List<AssetJson> = emptyList(),
)

@Serializable
private data class AssetJson(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
)
