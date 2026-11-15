package com.auracurve.app.player.catalog

import com.auracurve.app.player.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Tracks from Audius (https://docs.audius.co): its open REST API, read-only, identified by
 * `app_name` (no key needed for reading; the free plan allows 10 requests per second). A track
 * plays from `/v1/tracks/{id}/stream`, which redirects to a freshly signed address each time, so a
 * queued track never expires.
 */
class AudiusClient(
    private val appName: String,
    private val userAgent: String,
    private val http: OkHttpClient = defaultHttp(),
) : AudiusCatalog {
    // The free plan allows 10 requests per second; "All electronic" asks one per genre.
    private val parallel = Semaphore(MAX_PARALLEL)

    /** This week's trending tracks of [genre]; ALL mixes every genre of the list. */
    override suspend fun trending(genre: AudiusGenre): List<TrackItem> =
        coroutineScope {
            val genres = genre.apiGenres()
            val perGenre = if (genres.size > 1) MIXED_PER_GENRE else SINGLE_LIMIT
            val lists =
                genres
                    .map { name ->
                        async {
                            CatalogJson.audiusTracks(fetch(url("v1/tracks/trending") { addQueryParameter("genre", name).addQueryParameter("time", "week").addQueryParameter("limit", perGenre.toString()) }), ::streamUrl)
                        }
                    }.awaitAll()
            // Round robin, so "All electronic" is not one genre after the other.
            interleave(lists.map { list -> list.map { it.track } }).distinctBy { it.id }.take(MAX_RESULTS)
        }

    /** Search, narrowed to the genres of [genre]. */
    override suspend fun search(
        query: String,
        genre: AudiusGenre,
    ): List<TrackItem> {
        val allowed = genre.apiGenres().toSet()
        return CatalogJson
            .audiusTracks(fetch(url("v1/tracks/search") { addQueryParameter("query", query).addQueryParameter("limit", SEARCH_LIMIT.toString()) }), ::streamUrl)
            .filter { it.genre in allowed }
            .map { it.track }
    }

    fun streamUrl(id: String): String =
        BASE
            .toHttpUrl()
            .newBuilder()
            .addPathSegments("v1/tracks")
            .addPathSegment(id)
            .addPathSegment("stream")
            .addQueryParameter("app_name", appName)
            .build()
            .toString()

    private fun url(
        path: String,
        params: HttpUrl.Builder.() -> HttpUrl.Builder,
    ): HttpUrl =
        BASE
            .toHttpUrl()
            .newBuilder()
            .addPathSegments(path)
            .params()
            .addQueryParameter("app_name", appName)
            .build()

    // At most MAX_PARALLEL at once; a "too many requests" (429) is tried once more after a pause.
    private suspend fun fetch(url: HttpUrl): String =
        parallel.withPermit {
            repeat(RETRIES_ON_429) {
                val (code, body) = get(url)
                if (code != TOO_MANY_REQUESTS) return@withPermit checked(code, body)
                delay(RETRY_PAUSE_MS)
            }
            val (code, body) = get(url)
            checked(code, body)
        }

    private fun checked(
        code: Int,
        body: String,
    ): String = if (code in 200..299) body else throw IOException("Audius answered HTTP $code")

    private suspend fun get(url: HttpUrl): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
            http.newCall(request).execute().use { response -> response.code to response.body?.string().orEmpty() }
        }

    private fun <T> interleave(lists: List<List<T>>): List<T> {
        val out = mutableListOf<T>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) lists.forEach { list -> list.getOrNull(i)?.let(out::add) }
        return out
    }

    private companion object {
        const val BASE = "https://api.audius.co/"
        const val SINGLE_LIMIT = 50
        const val MIXED_PER_GENRE = 8
        const val MAX_RESULTS = 80
        const val SEARCH_LIMIT = 50
        const val MAX_PARALLEL = 3
        const val RETRIES_ON_429 = 1
        const val RETRY_PAUSE_MS = 1_200L
        const val TOO_MANY_REQUESTS = 429

        fun defaultHttp(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
    }
}
