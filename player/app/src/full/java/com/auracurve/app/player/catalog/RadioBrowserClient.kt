package com.auracurve.app.player.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Internet radio from Radio Browser (https://api.radio-browser.info): free and open, also for
 * non-free software. As its docs ask: a speaking User-Agent, a randomly chosen server from the
 * server list (retrying the next one on failure), and a "click" whenever a station is played.
 */
class RadioBrowserClient(
    private val userAgent: String,
    private val http: OkHttpClient = defaultHttp(),
) : RadioCatalog {
    @Volatile private var servers: List<String>? = null

    // "All electronic" asks one request per tag: a few at a time, to spare the free directory.
    private val parallel = Semaphore(MAX_PARALLEL)

    /** The stations of [genre], most listened first, only encrypted streams; ALL merges every genre. */
    override suspend fun stations(genre: RadioGenre): List<RadioStation> =
        coroutineScope {
            val tags = genre.queryTags()
            val perTag = if (genre == RadioGenre.ALL) ALL_PER_TAG else PER_TAG
            tags
                .map { tag -> async { parallel.withPermit { stationsForTag(tag, perTag) } } }
                .awaitAll()
                .flatten()
                .distinctBy { it.uuid }
                .distinctBy { it.streamUrl }
                .sortedByDescending { it.clickCount }
                .take(MAX_STATIONS)
        }

    /** Counts a play for the station's popularity, as Radio Browser asks. Failures are ignored. */
    override suspend fun countClick(uuid: String) {
        runCatching { get { server -> server.newBuilder().addPathSegments("json/url").addPathSegment(uuid).build() } }
    }

    private suspend fun stationsForTag(
        tag: String,
        limit: Int,
    ): List<RadioStation> =
        CatalogJson.radioStations(
            get { server ->
                server
                    .newBuilder()
                    .addPathSegments("json/stations/search")
                    .addQueryParameter("tag", tag)
                    .addQueryParameter("tagExact", "true")
                    .addQueryParameter("hidebroken", "true")
                    .addQueryParameter("is_https", "true")
                    .addQueryParameter("order", "clickcount")
                    .addQueryParameter("reverse", "true")
                    .addQueryParameter("limit", limit.toString())
                    .build()
            },
        )

    // Tries the servers in random order until one answers.
    private suspend fun get(url: (HttpUrl) -> HttpUrl): String =
        withContext(Dispatchers.IO) {
            var last: IOException? = null
            for (host in serverList()) {
                try {
                    return@withContext fetch(url("https://$host/".toHttpUrl()))
                } catch (e: IOException) {
                    last = e
                }
            }
            throw last ?: IOException("No Radio Browser server")
        }

    private fun serverList(): List<String> {
        servers?.let { return it }
        val found =
            runCatching {
                val array = JSONArray(fetch(SERVER_LIST.toHttpUrl()))
                (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("name") }.filter { it.isNotEmpty() }.distinct()
            }.getOrNull().orEmpty()
        return (found.ifEmpty { FALLBACK_SERVERS }).shuffled().also { servers = it }
    }

    private fun fetch(url: HttpUrl): String {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Radio Browser answered HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private companion object {
        const val SERVER_LIST = "https://all.api.radio-browser.info/json/servers"
        val FALLBACK_SERVERS = listOf("de1.api.radio-browser.info", "de2.api.radio-browser.info")
        const val PER_TAG = 80
        const val ALL_PER_TAG = 25
        const val MAX_STATIONS = 80
        const val MAX_PARALLEL = 4

        fun defaultHttp(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
    }
}
