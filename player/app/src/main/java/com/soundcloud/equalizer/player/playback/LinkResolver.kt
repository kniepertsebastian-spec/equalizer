package com.soundcloud.equalizer.player.playback

import android.content.Context
import com.hardbasseq.eq.link.LinkSource
import com.hardbasseq.eq.link.ShareLink
import com.soundcloud.equalizer.player.auth.SoundCloudLoginActivity
import com.soundcloud.equalizer.player.model.ExternalTrackInfo
import com.soundcloud.equalizer.player.model.ResolvedLink
import com.soundcloud.equalizer.player.model.TrackItem
import com.soundcloud.equalizer.player.soundcloud.SoundCloudClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// Entry point for turning a shared link into tracks, for callers outside this module.
// Keeps SoundCloudClient (and the OkHttp types in its constructor) inside :player, so
// :app only ever sees plain model classes.
class LinkResolver(
    private val context: Context,
) {
    private val client = SoundCloudClient()
    private val http =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    suspend fun resolve(url: String): ResolvedLink {
        SoundCloudLoginActivity.getSavedToken(context)?.let { client.setUserAuthToken(it) }
        return client.resolveLink(url)
    }

    /**
     * Title (and channel) of the song behind a YouTube or Spotify link, from the
     * service's public oEmbed preview endpoint - the same data a chat app shows as a link
     * preview. Nothing is played or downloaded from those services.
     */
    suspend fun describeExternal(url: String): ExternalTrackInfo =
        withContext(Dispatchers.IO) {
            val endpoint =
                when (ShareLink.classify(url)) {
                    LinkSource.YOUTUBE -> "https://www.youtube.com/oembed?format=json&url="
                    LinkSource.SPOTIFY -> "https://open.spotify.com/oembed?url="
                    else -> throw IOException("Dieser Link wird nicht unterstützt")
                } + URLEncoder.encode(url, "UTF-8")
            val request =
                Request
                    .Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        if (response.code in NO_DETAILS_CODES) {
                            "Zu diesem Link gibt es keine Angaben (Video privat, gesperrt oder nicht einbettbar)"
                        } else {
                            "Der Anbieter antwortet nicht (HTTP ${response.code})"
                        },
                    )
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val title = json.optString("title").takeIf { it.isNotBlank() } ?: throw IOException("Der Link enthält keinen Titel")
                val author = if (json.isNull("author_name")) null else json.optString("author_name").takeIf { it.isNotBlank() }
                ExternalTrackInfo(title = title, author = author)
            }
        }

    /**
     * The public embed page of a Spotify playlist as text (it lists the tracks); read
     * by SpotifyPlaylistPage. Nothing is played or downloaded from Spotify.
     */
    suspend fun fetchSpotifyPlaylistPage(playlistId: String): String =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(ShareLink.spotifyPlaylistEmbedUrl(playlistId))
                    .header("Accept", "text/html")
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        if (response.code in NO_DETAILS_CODES) {
                            "Die Playlist ist nicht öffentlich oder existiert nicht"
                        } else {
                            "Spotify antwortet nicht (HTTP ${response.code})"
                        },
                    )
                }
                response.body?.string().orEmpty()
            }
        }

    /** SoundCloud search results as candidates: metadata only, streams are resolved when one plays. */
    suspend fun searchTracks(
        query: String,
        limit: Int,
    ): List<TrackItem> {
        SoundCloudLoginActivity.getSavedToken(context)?.let { client.setUserAuthToken(it) }
        return client.searchTracks(query, limit, resolveStreams = false)
    }

    private companion object {
        val NO_DETAILS_CODES = setOf(401, 403, 404)
        const val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
