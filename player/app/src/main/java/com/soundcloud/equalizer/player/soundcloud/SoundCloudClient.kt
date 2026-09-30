package com.soundcloud.equalizer.player.soundcloud

import com.hardbasseq.eq.link.ShareLink
import com.hardbasseq.eq.link.StreamOption
import com.hardbasseq.eq.link.StreamSelection
import com.soundcloud.equalizer.player.model.PlaylistItem
import com.soundcloud.equalizer.player.model.ResolvedLink
import com.soundcloud.equalizer.player.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class SoundCloudClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        // Matches SoundCloudLoginActivity's WebView UA - a generic OkHttp UA is an
        // easy anti-bot tell, this at least looks like the same browser that logged in.
        //
        // Must be a DESKTOP UA, not a mobile one: verified live that soundcloud.com
        // 307-redirects any mobile-looking UA to m.soundcloud.com, a completely
        // different (Next.js) site whose script bundles don't match the
        // a-v2.sndcdn.com/assets/*.js pattern below at all, so client_id scraping
        // silently found zero matches with a mobile UA.
        private const val MAX_IDS_PER_REQUEST = 50

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }

    @Volatile
    private var cachedClientId: String? = null
    @Volatile
    private var userAuthToken: String? = null

    fun setUserAuthToken(token: String?) {
        this.userAuthToken = token
    }

    fun getUserAuthToken(): String? = userAuthToken

    suspend fun getClientId(forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cachedClientId?.let { return@withContext it }
        }

        val mainPageRequest = Request.Builder()
            .url("https://soundcloud.com")
            .header("User-Agent", USER_AGENT)
            .build()

        val html = okHttpClient.newCall(mainPageRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to load soundcloud.com (HTTP ${response.code}) while looking for a client_id")
            }
            response.body?.string() ?: ""
        }

        val scriptPattern = Pattern.compile("src=\"(https://a-v2\\.sndcdn\\.com/assets/[^\"]+\\.js)\"")
        val matcher = scriptPattern.matcher(html)
        val scriptUrls = mutableListOf<String>()
        while (matcher.find()) {
            matcher.group(1)?.let { scriptUrls.add(it) }
        }

        val clientIdPattern = Pattern.compile("client_id:\"([a-zA-Z0-9]{32})\"")
        for (scriptUrl in scriptUrls.reversed()) {
            val scriptReq = Request.Builder()
                .url(scriptUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            val scriptContent = runCatching {
                okHttpClient.newCall(scriptReq).execute().use { it.body?.string() ?: "" }
            }.getOrDefault("")

            val clientMatcher = clientIdPattern.matcher(scriptContent)
            if (clientMatcher.find()) {
                val foundId = clientMatcher.group(1)
                if (!foundId.isNullOrEmpty()) {
                    cachedClientId = foundId
                    return@withContext foundId
                }
            }
        }

        throw IOException(
            "Couldn't find a client_id in any of soundcloud.com's ${scriptUrls.size} asset " +
                "bundles - SoundCloud likely changed how it's embedded"
        )
    }

    private fun buildRequest(url: String): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")

        userAuthToken?.let { token ->
            builder.header("Authorization", if (token.startsWith("OAuth")) token else "OAuth $token")
        }

        return builder.build()
    }

    // client_id values rotate periodically; a cached one that used to work can
    // start getting rejected. Executes the request, and on a 401/403 refreshes
    // the client_id from scratch and retries exactly once before giving up.
    private suspend fun executeWithClientId(
        buildUrl: (clientId: String) -> String,
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        var clientId = getClientId()
        var response = executeRequest(buildUrl(clientId))

        if (!response.isSuccessful && (response.code == 401 || response.code == 403)) {
            response.close()
            clientId = getClientId(forceRefresh = true)
            response = executeRequest(buildUrl(clientId))
        }

        response.use {
            if (!it.isSuccessful) {
                throw IOException("SoundCloud returned HTTP ${it.code} for this request")
            }
            (it.body?.string() ?: "") to clientId
        }
    }

    private fun executeRequest(url: String): Response = okHttpClient.newCall(buildRequest(url)).execute()

    suspend fun searchTracks(query: String, limit: Int = 20): List<TrackItem> = withContext(Dispatchers.IO) {
        val (jsonStr, clientId) = executeWithClientId { clientId ->
            "https://api-v2.soundcloud.com/search/tracks?q=${java.net.URLEncoder.encode(query, "UTF-8")}&client_id=$clientId&limit=$limit"
        }

        if (jsonStr.isBlank()) return@withContext emptyList()

        val root = JSONObject(jsonStr)
        val collection = root.optJSONArray("collection") ?: JSONArray()

        // Each track needs its own network round-trip to resolve a stream URL
        // (see parseTrack/resolveStreamUrl) - resolving them one at a time made a
        // single slow or dropped connection stall the whole search, and since a
        // failed one used to throw, it silently wiped out every track already
        // parsed before it. Resolve them all concurrently instead: faster, and one
        // track failing no longer takes the rest down with it.
        return@withContext coroutineScope {
            (0 until collection.length())
                .map { i -> async { runCatching { parseTrack(collection.getJSONObject(i), clientId) }.getOrNull() } }
                .awaitAll()
                .filterNotNull()
        }
    }

    suspend fun searchPlaylists(query: String, limit: Int = 10): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val (jsonStr, clientId) = executeWithClientId { clientId ->
            "https://api-v2.soundcloud.com/search/playlists?q=${java.net.URLEncoder.encode(query, "UTF-8")}&client_id=$clientId&limit=$limit"
        }

        if (jsonStr.isBlank()) return@withContext emptyList()

        val root = JSONObject(jsonStr)
        val collection = root.optJSONArray("collection") ?: JSONArray()

        // See searchTracks: resolve every playlist's tracks concurrently rather
        // than one network round-trip at a time, so one slow/failed track can't
        // stall or wipe out the rest.
        return@withContext coroutineScope {
            (0 until collection.length())
                .map { i ->
                    async {
                        val obj = collection.getJSONObject(i)
                        val id = obj.optLong("id")
                        val title = obj.optString("title", "Untitled Playlist")
                        val trackCount = obj.optInt("track_count", 0)
                        val artwork = obj.optString("artwork_url", "")

                        val rawTracks = obj.optJSONArray("tracks") ?: JSONArray()
                        val tracksList =
                            (0 until rawTracks.length())
                                .map { j ->
                                    async { runCatching { parseTrack(rawTracks.getJSONObject(j), clientId) }.getOrNull() }
                                }
                                .awaitAll()
                                .filterNotNull()

                        PlaylistItem(
                            id = id,
                            title = title,
                            trackCount = trackCount,
                            artworkUrl = if (artwork.isNotBlank()) artwork else null,
                            tracks = tracksList
                        )
                    }
                }
                .awaitAll()
        }
    }

    suspend fun resolveStreamUrl(transcodingsJsonArray: JSONArray, clientId: String): String? =
        resolveStream(transcodingsJsonArray, clientId)?.url

    private class ResolvedStream(val url: String, val isPreview: Boolean)

    // Picks the best stream SoundCloud offers for a track - full length before a
    // ~30 s preview (StreamSelection) - and resolves it to a playable URL. Requests
    // carry the signed-in user's token when there is one, which is what makes a
    // SoundCloud Go subscription's full-length streams appear in the list at all.
    private suspend fun resolveStream(transcodingsJsonArray: JSONArray, clientId: String): ResolvedStream? =
        withContext(Dispatchers.IO) {
            val options = (0 until transcodingsJsonArray.length()).mapNotNull { i ->
                val tc = transcodingsJsonArray.getJSONObject(i)
                val protocol = tc.optJSONObject("format")?.optString("protocol") ?: return@mapNotNull null
                val url = tc.optString("url")
                if (url.isBlank()) null else StreamOption(protocol, tc.optBoolean("snipped", false), url)
            }
            val chosen = StreamSelection.pick(options) ?: return@withContext null
            val targetUrl = chosen.url
            val fullReqUrl = if (targetUrl.contains("?")) "$targetUrl&client_id=$clientId" else "$targetUrl?client_id=$clientId"

            // A single track's stream-URL lookup failing outright (timeout, dropped
            // connection) must not take down the whole search result - one bad track
            // is worth showing as unplayable, not worth losing every other result
            // over. Callers already treat a null streamUrl as "resolve on tap".
            val jsonStr = runCatching {
                executeRequest(fullReqUrl).use { response ->
                    if (!response.isSuccessful) return@withContext null
                    response.body?.string() ?: ""
                }
            }.getOrNull() ?: return@withContext null

            if (jsonStr.isBlank()) return@withContext null
            val streamUrl = JSONObject(jsonStr).optString("url", null) ?: return@withContext null
            ResolvedStream(streamUrl, chosen.snipped)
        }

    /**
     * Resolves a pasted/shared SoundCloud link to the track or playlist behind it.
     * Stream URLs are NOT resolved here (they are short-lived and only needed when
     * a track actually plays - see [getTrack]), so importing a long playlist costs a
     * handful of requests rather than one per track.
     */
    suspend fun resolveLink(url: String): ResolvedLink = withContext(Dispatchers.IO) {
        val target = if (ShareLink.isSoundCloudShortLink(url)) expandShortLink(url) else url
        val normalized = ShareLink.normalizeSoundCloud(target)

        val (jsonStr, clientId) = executeWithClientId { clientId ->
            "https://api-v2.soundcloud.com/resolve?url=${java.net.URLEncoder.encode(normalized, "UTF-8")}&client_id=$clientId"
        }
        if (jsonStr.isBlank()) return@withContext ResolvedLink.Unsupported("SoundCloud hat nichts zu diesem Link geliefert")

        val root = JSONObject(jsonStr)
        when (root.optString("kind")) {
            "track" -> {
                val track = parseTrack(root, clientId, resolveStream = false)
                    ?: return@withContext ResolvedLink.Unsupported("Der Titel konnte nicht gelesen werden")
                ResolvedLink.SingleTrack(track)
            }

            "playlist" -> {
                val raw = root.optJSONArray("tracks") ?: JSONArray()
                // The resolve response only carries full data for the first few
                // tracks; the rest are bare {id} stubs, hydrated in batches below.
                val ordered = (0 until raw.length()).map { raw.getJSONObject(it) }
                val stubIds = ordered.filter { !it.has("title") }.map { it.optLong("id", -1) }.filter { it > 0 }
                val hydrated = getTracksByIds(stubIds).associateBy { it.id }
                val tracks = ordered.mapNotNull { obj ->
                    if (obj.has("title")) {
                        runCatching { parseTrack(obj, clientId, resolveStream = false) }.getOrNull()
                    } else {
                        hydrated[obj.optLong("id", -1)]
                    }
                }
                if (tracks.isEmpty()) {
                    ResolvedLink.Unsupported("Die Playlist ist leer oder privat")
                } else {
                    ResolvedLink.Playlist(
                        title = root.optString("title", "Playlist").ifBlank { "Playlist" },
                        sourceUrl = normalized,
                        tracks = tracks,
                    )
                }
            }

            else -> ResolvedLink.Unsupported("Nur Titel- und Playlist-Links werden unterstützt")
        }
    }

    /** One track with a freshly resolved stream URL, or null if it cannot be played. */
    suspend fun getTrack(id: Long): TrackItem? = withContext(Dispatchers.IO) {
        val (jsonStr, clientId) = executeWithClientId { clientId ->
            "https://api-v2.soundcloud.com/tracks/$id?client_id=$clientId"
        }
        if (jsonStr.isBlank()) return@withContext null
        parseTrack(JSONObject(jsonStr), clientId, resolveStream = true)
    }

    // Full metadata for bare track ids, in the order asked for where SoundCloud
    // returned them. The endpoint takes a limited number of ids per call.
    private suspend fun getTracksByIds(ids: List<Long>): List<TrackItem> = withContext(Dispatchers.IO) {
        ids.chunked(MAX_IDS_PER_REQUEST).flatMap { chunk ->
            runCatching {
                val (jsonStr, clientId) = executeWithClientId { clientId ->
                    "https://api-v2.soundcloud.com/tracks?ids=${chunk.joinToString(",")}&client_id=$clientId"
                }
                val array = JSONArray(jsonStr)
                (0 until array.length()).mapNotNull { i ->
                    runCatching { parseTrack(array.getJSONObject(i), clientId, resolveStream = false) }.getOrNull()
                }
            }.getOrDefault(emptyList())
        }
    }

    // on.soundcloud.com/... links only redirect to the real page - OkHttp follows
    // the redirect, the final URL is what the resolve API needs. The body is never
    // read, so this costs one round trip, not a page download.
    private fun expandShortLink(url: String): String =
        executeRequest(url).use { response -> response.request.url.toString() }

    private suspend fun parseTrack(obj: JSONObject, clientId: String, resolveStream: Boolean = true): TrackItem? {
        val id = obj.optLong("id", -1)
        if (id == -1L) return null

        val title = obj.optString("title", "Unknown Track")
        val duration = obj.optLong("duration", 0L)
        val artworkUrl = obj.optString("artwork_url", null)

        val userObj = obj.optJSONObject("user")
        val artist = userObj?.optString("username", "Unknown Artist") ?: "Unknown Artist"

        var streamUrl: String? = null
        var isPreview = false
        val mediaObj = obj.optJSONObject("media")
        val transcodings = mediaObj?.optJSONArray("transcodings")
        if (resolveStream && transcodings != null && transcodings.length() > 0) {
            val stream = resolveStream(transcodings, clientId)
            streamUrl = stream?.url
            isPreview = stream?.isPreview == true
        }

        return TrackItem(
            id = id,
            title = title,
            artist = artist,
            artworkUrl = artworkUrl,
            streamUrl = streamUrl,
            durationMs = duration,
            isPreview = isPreview
        )
    }
}
