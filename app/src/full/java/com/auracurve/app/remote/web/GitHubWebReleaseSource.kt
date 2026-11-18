package com.auracurve.app.remote.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Bundles from the public release repository (module 10, E-012, Q-10-1b): the code of the remote
 * stays private, only the signed bundles are public, so the phone needs no GitHub token. Their
 * authenticity comes from the signature, not from where they were loaded.
 */
class GitHubWebReleaseSource(
    private val userAgent: String,
) : WebReleaseSource {
    override suspend fun latest(): WebRelease? =
        withContext(Dispatchers.IO) {
            parseLatest(read(LATEST_URL, MAX_CATALOG_BYTES).toString(Charsets.UTF_8))
        }

    override suspend fun download(
        release: WebRelease,
        maxBytes: Long,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            require(isReleaseDownload(release.url)) { "Fremde Download-Adresse" }
            read(release.url, maxBytes)
        }

    private fun read(
        url: String,
        maxBytes: Long,
    ): ByteArray {
        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 30_000
                // https → https redirects (to GitHub's file storage) are followed, nothing else.
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
            }
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            check(connection.url.protocol == "https") { "Kein HTTPS" }
            return connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(out.size() + count <= maxBytes) { "Zu groß" }
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val REPOSITORY = "kniepertsebastian-spec/pixel-car-remote-releases"
        const val LATEST_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
        private const val DOWNLOAD_PREFIX = "https://github.com/$REPOSITORY/releases/download/"
        private const val MAX_CATALOG_BYTES = 256L * 1024
        private val ASSET = Regex("^pixel-car-remote-(\\d{1,4}\\.\\d{1,4}\\.\\d{1,4})\\.zip$")

        fun isReleaseDownload(url: String): Boolean = url.startsWith(DOWNLOAD_PREFIX) && ".." !in url

        /** The bundle of a `releases/latest` answer; drafts, pre-releases and foreign links are skipped. */
        fun parseLatest(json: String): WebRelease? {
            val release = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
            if (release.flag("draft") || release.flag("prerelease")) return null
            val assets = runCatching { release["assets"]!!.jsonArray }.getOrNull() ?: return null
            return assets
                .mapNotNull { element ->
                    val asset = element as? JsonObject ?: return@mapNotNull null
                    val name = asset.text("name") ?: return@mapNotNull null
                    val version = ASSET.matchEntire(name)?.groupValues?.get(1) ?: return@mapNotNull null
                    val url = asset.text("browser_download_url")?.takeIf(::isReleaseDownload) ?: return@mapNotNull null
                    val size = runCatching { asset["size"]?.jsonPrimitive?.longOrNull }.getOrNull() ?: 0L
                    WebRelease(version, url, size)
                }.maxWithOrNull { a, b -> WebVersion.compare(a.version, b.version) }
        }

        private fun JsonObject.text(key: String) = runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

        private fun JsonObject.flag(key: String) = text(key) == "true"
    }
}
