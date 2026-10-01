package com.hardbasseq.eq.link

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class SpotifyTrack(
    val title: String,
    val artist: String,
)

data class SpotifyPlaylist(
    val title: String,
    val tracks: List<SpotifyTrack>,
)

// Reads the track list out of a Spotify playlist's public embed page. The page carries
// its data as JSON in a <script id="__NEXT_DATA__"> tag; the track list is the array
// called "trackList" somewhere in it. This is not an official interface and Spotify can
// change it at any time, so the reader looks for the pieces by name instead of by a
// fixed path and gives up (null) rather than guessing when they are missing.
object SpotifyPlaylistPage {
    private val dataScript = Regex("<script[^>]*id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
    private val json = Json { isLenient = true }

    // The page's embedded JSON, or null when the page has none or it is unreadable.
    internal fun pageData(html: String): JsonElement? {
        val match = dataScript.find(html) ?: return null
        val raw = match.groupValues[1].trim()
        return runCatching { json.parseToJsonElement(raw) }.getOrNull()
    }

    fun parse(html: String): SpotifyPlaylist? {
        val root = pageData(html) ?: return null
        val holder = findHolder(root) ?: return null
        val list = holder["trackList"] as? JsonArray ?: return null
        val tracks = list.mapNotNull { toTrack(it) }
        if (tracks.isEmpty()) return null
        val title = text(holder["name"]) ?: text(holder["title"]) ?: DEFAULT_TITLE
        return SpotifyPlaylist(title, tracks)
    }

    // The object that has the "trackList" array as a member.
    private fun findHolder(element: JsonElement): JsonObject? =
        when (element) {
            is JsonObject ->
                if (element["trackList"] is JsonArray) element else element.values.firstNotNullOfOrNull { findHolder(it) }
            is JsonArray -> element.firstNotNullOfOrNull { findHolder(it) }
            else -> null
        }

    private fun toTrack(element: JsonElement): SpotifyTrack? {
        val entry = element as? JsonObject ?: return null
        val title = text(entry["title"]) ?: text(entry["name"]) ?: return null
        val artist = text(entry["subtitle"]) ?: firstArtistName(entry["artists"]) ?: return null
        return SpotifyTrack(title, artist)
    }

    internal fun firstArtistName(element: JsonElement?): String? =
        (element as? JsonArray)?.firstNotNullOfOrNull { text((it as? JsonObject)?.get("name")) }

    internal fun text(element: JsonElement?): String? =
        (element as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?.replace(' ', ' ')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    const val DEFAULT_TITLE = "Spotify-Playlist"
}
