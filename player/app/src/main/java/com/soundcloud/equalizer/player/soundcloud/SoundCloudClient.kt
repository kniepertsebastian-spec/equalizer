package com.soundcloud.equalizer.player.soundcloud

import com.soundcloud.equalizer.player.model.PlaylistItem
import com.soundcloud.equalizer.player.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class SoundCloudClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    @Volatile
    private var cachedClientId: String? = null
    @Volatile
    private var userAuthToken: String? = null

    fun setUserAuthToken(token: String?) {
        this.userAuthToken = token
    }

    fun getUserAuthToken(): String? = userAuthToken

    suspend fun getClientId(): String = withContext(Dispatchers.IO) {
        cachedClientId?.let { return@withContext it }

        val mainPageRequest = Request.Builder()
            .url("https://soundcloud.com")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .build()

        val html = runCatching {
            okHttpClient.newCall(mainPageRequest).execute().use { response ->
                response.body?.string() ?: ""
            }
        }.getOrDefault("")

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
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10)")
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

        val fallbackId = "iZ2A8L121980838080"
        cachedClientId = fallbackId
        return@withContext fallbackId
    }

    private fun buildRequest(url: String): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")

        userAuthToken?.let { token ->
            builder.header("Authorization", if (token.startsWith("OAuth")) token else "OAuth $token")
        }

        return builder.build()
    }

    suspend fun searchTracks(query: String, limit: Int = 20): List<TrackItem> = withContext(Dispatchers.IO) {
        val clientId = getClientId()
        val url = "https://api-v2.soundcloud.com/search/tracks?q=${java.net.URLEncoder.encode(query, "UTF-8")}&client_id=$clientId&limit=$limit"
        val request = buildRequest(url)

        val jsonStr = runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                response.body?.string() ?: ""
            }
        }.getOrDefault("")

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
        val clientId = getClientId()
        val url = "https://api-v2.soundcloud.com/search/playlists?q=${java.net.URLEncoder.encode(query, "UTF-8")}&client_id=$clientId&limit=$limit"
        val request = buildRequest(url)

        val jsonStr = runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                response.body?.string() ?: ""
            }
        }.getOrDefault("")

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
        val request = buildRequest(fullReqUrl)

        val jsonStr = runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                response.body?.string() ?: ""
            }
        }.getOrDefault("")

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
