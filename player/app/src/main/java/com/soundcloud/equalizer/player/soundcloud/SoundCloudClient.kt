package com.soundcloud.equalizer.player.soundcloud

import com.soundcloud.equalizer.player.model.PlaylistItem
import com.soundcloud.equalizer.player.model.TrackItem
import kotlinx.coroutines.Dispatchers
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
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
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

        val list = mutableListOf<TrackItem>()
        if (jsonStr.isBlank()) return@withContext list

        val root = JSONObject(jsonStr)
        val collection = root.optJSONArray("collection") ?: JSONArray()

        for (i in 0 until collection.length()) {
            val obj = collection.getJSONObject(i)
            parseTrack(obj, clientId)?.let { list.add(it) }
        }

        return@withContext list
    }

    suspend fun searchPlaylists(query: String, limit: Int = 10): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val (jsonStr, clientId) = executeWithClientId { clientId ->
            "https://api-v2.soundcloud.com/search/playlists?q=${java.net.URLEncoder.encode(query, "UTF-8")}&client_id=$clientId&limit=$limit"
        }

        val list = mutableListOf<PlaylistItem>()
        if (jsonStr.isBlank()) return@withContext list

        val root = JSONObject(jsonStr)
        val collection = root.optJSONArray("collection") ?: JSONArray()

        for (i in 0 until collection.length()) {
            val obj = collection.getJSONObject(i)
            val id = obj.optLong("id")
            val title = obj.optString("title", "Untitled Playlist")
            val trackCount = obj.optInt("track_count", 0)
            val artwork = obj.optString("artwork_url", "")

            val rawTracks = obj.optJSONArray("tracks") ?: JSONArray()
            val tracksList = mutableListOf<TrackItem>()
            for (j in 0 until rawTracks.length()) {
                val tObj = rawTracks.getJSONObject(j)
                parseTrack(tObj, clientId)?.let { tracksList.add(it) }
            }

            list.add(
                PlaylistItem(
                    id = id,
                    title = title,
                    trackCount = trackCount,
                    artworkUrl = if (artwork.isNotBlank()) artwork else null,
                    tracks = tracksList
                )
            )
        }

        return@withContext list
    }

    suspend fun resolveStreamUrl(transcodingsJsonArray: JSONArray, clientId: String): String? = withContext(Dispatchers.IO) {
        var hlsUrl: String? = null
        var progressiveUrl: String? = null

        for (i in 0 until transcodingsJsonArray.length()) {
            val tc = transcodingsJsonArray.getJSONObject(i)
            val format = tc.optJSONObject("format")
            val protocol = format?.optString("protocol")
            val url = tc.optString("url")

            if (protocol == "progressive" && progressiveUrl == null) {
                progressiveUrl = url
            } else if (protocol == "hls" && hlsUrl == null) {
                hlsUrl = url
            }
        }

        val targetUrl = progressiveUrl ?: hlsUrl ?: return@withContext null
        val fullReqUrl = if (targetUrl.contains("?")) "$targetUrl&client_id=$clientId" else "$targetUrl?client_id=$clientId"

        val jsonStr = executeRequest(fullReqUrl).use { response ->
            if (!response.isSuccessful) return@withContext null
            response.body?.string() ?: ""
        }

        if (jsonStr.isBlank()) return@withContext null
        val root = JSONObject(jsonStr)
        return@withContext root.optString("url", null)
    }

    private suspend fun parseTrack(obj: JSONObject, clientId: String): TrackItem? {
        val id = obj.optLong("id", -1)
        if (id == -1L) return null

        val title = obj.optString("title", "Unknown Track")
        val duration = obj.optLong("duration", 0L)
        val artworkUrl = obj.optString("artwork_url", null)

        val userObj = obj.optJSONObject("user")
        val artist = userObj?.optString("username", "Unknown Artist") ?: "Unknown Artist"

        var streamUrl: String? = null
        val mediaObj = obj.optJSONObject("media")
        val transcodings = mediaObj?.optJSONArray("transcodings")
        if (transcodings != null && transcodings.length() > 0) {
            streamUrl = resolveStreamUrl(transcodings, clientId)
        }

        return TrackItem(
            id = id,
            title = title,
            artist = artist,
            artworkUrl = artworkUrl,
            streamUrl = streamUrl,
            durationMs = duration
        )
    }
}
