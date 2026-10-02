package com.hardbasseq.eq.ui.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import com.hardbasseq.eq.BuildConfig
import com.hardbasseq.eq.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal class ReleaseUpdateClient(
    private val context: Context,
) {
    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/kniepertsebastian-spec/equalizer/releases?per_page=20"
        const val MAX_CATALOG_BYTES = 1_000_000
        const val MAX_MANIFEST_BYTES = 16_384
        const val MAX_APK_BYTES = 250_000_000L
    }

    suspend fun checkForUpdate(): AvailableUpdate? =
        withContext(Dispatchers.IO) {
            val releases = ReleaseCatalog.parseReleases(readText(RELEASES_URL, MAX_CATALOG_BYTES))
            releases
                .mapNotNull { release ->
                    val manifestAsset = ReleaseCatalog.manifestAsset(release) ?: return@mapNotNull null
                    if (!ReleaseCatalog.isRepositoryReleaseUrl(manifestAsset.downloadUrl)) return@mapNotNull null
                    runCatching {
                        ReleaseCatalog.eligibleUpdate(
                            release = release,
                            manifestPayload = readText(manifestAsset.downloadUrl, MAX_MANIFEST_BYTES),
                            packageName = context.packageName,
                            currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                        )
                    }.getOrNull()
                }.maxByOrNull { it.versionCode }
        }

    suspend fun download(
        update: AvailableUpdate,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val temporary = File(directory, "download.tmp")
            val apk = File(directory, "HardBassEQ-update.apk")
            temporary.delete()
            try {
                val connection = openConnection(update.apkUrl)
                try {
                    require(connection.responseCode == HttpURLConnection.HTTP_OK) { context.getString(R.string.upd_err_download) }
                    val total = connection.contentLengthLong
                    require(total <= MAX_APK_BYTES) { context.getString(R.string.upd_err_too_big) }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var downloaded = 0L
                    var lastReported = 0L
                    connection.inputStream.use { source ->
                        temporary.outputStream().use { destination ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                downloaded += count
                                require(downloaded <= MAX_APK_BYTES) { context.getString(R.string.upd_err_too_big) }
                                destination.write(buffer, 0, count)
                                digest.update(buffer, 0, count)
                                if (downloaded - lastReported >= 1_000_000 || downloaded == total) {
                                    withContext(Dispatchers.Main) { onProgress(downloaded, total) }
                                    lastReported = downloaded
                                }
                            }
                        }
                    }
                    require(downloaded > 0L && (total < 0L || downloaded == total)) { context.getString(R.string.upd_err_incomplete) }
                    val actualSha256 = digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
                    require(actualSha256 == update.sha256) { context.getString(R.string.upd_err_checksum) }
                } finally {
                    connection.disconnect()
                }
                verifyPackage(temporary, update)
                apk.delete()
                check(temporary.renameTo(apk)) { context.getString(R.string.upd_err_save) }
                apk
            } catch (error: Exception) {
                temporary.delete()
                throw error
            }
        }

    @Suppress("DEPRECATION")
    private fun verifyPackage(
        file: File,
        update: AvailableUpdate,
    ) {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive =
            context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
                ?: error(context.getString(R.string.upd_err_invalid))
        require(archive.packageName == context.packageName) { context.getString(R.string.upd_err_other_app) }
        require(archive.longVersionCode == update.versionCode.toLong()) { context.getString(R.string.upd_err_version) }
        require(archive.longVersionCode > BuildConfig.VERSION_CODE) { context.getString(R.string.upd_err_installed) }
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val currentSigners =
            installed.signingInfo
                ?.apkContentsSigners
                ?.map { it.toCharsString() }
                ?.toSet()
        val updateSigners =
            archive.signingInfo
                ?.apkContentsSigners
                ?.map { it.toCharsString() }
                ?.toSet()
        require(!currentSigners.isNullOrEmpty() && currentSigners == updateSigners) {
            context.getString(R.string.upd_err_signature)
        }
    }

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun readText(
        url: String,
        maxBytes: Int,
    ): String {
        val connection = openConnection(url)
        return try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) { context.getString(R.string.upd_err_query) }
            connection.inputStream.use { source ->
                val destination = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    require(destination.size() + count <= maxBytes) { context.getString(R.string.upd_err_data_big) }
                    destination.write(buffer, 0, count)
                }
                destination.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "HardBassEQ/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
}
