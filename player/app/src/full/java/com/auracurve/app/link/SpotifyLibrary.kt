package com.auracurve.app.link

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

// A copy of the user's Spotify playlists and their songs, read through the Web API while the
// phone is online and kept for when it is not (Pixel-Car, way B: the car phone has no SIM, the
// songs themselves are Spotify downloads). Only ids and texts: playing goes through the Spotify
// app with a spotify:track:/spotify:playlist: address.

@Serializable
data class LibraryTrack(
    // Spotify's base-62 track id; the address to play it is spotify:track:<id>.
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String = "",
    val durationMs: Long = 0L,
)

@Serializable
data class LibraryPlaylist(
    val id: String,
    val name: String,
    val owner: String = "",
    // What Spotify says the playlist holds, also when its songs could not be read.
    val trackCount: Int = 0,
    // The songs in playlist order; null when Spotify does not hand them out (a playlist of
    // someone else that the user only follows).
    val trackIds: List<String>? = null,
)

@Serializable
data class SpotifyLibrary(
    val syncedAtMs: Long,
    val playlists: List<LibraryPlaylist>,
    // Every song once, by id; playlists refer to them through trackIds.
    val tracks: Map<String, LibraryTrack>,
) {
    fun playlist(id: String): LibraryPlaylist? = playlists.firstOrNull { it.id == id }

    companion object {
        val EMPTY = SpotifyLibrary(0L, emptyList(), emptyMap())
    }
}

// One entry of GET /me/playlists.
data class PlaylistInfo(
    val id: String,
    val name: String,
    val owner: String,
    val trackCount: Int,
)

data class SpotifyPage<T>(
    val entries: List<T>,
    // Address of the next page, null on the last one.
    val next: String?,
)

object SpotifyLibraryJson {
    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
        }
    private val ID = Regex("[0-9A-Za-z]{22}")

    // Spotify ids are 22 base-62 characters; anything else is never put into a spotify: address.
    fun isSpotifyId(value: String?): Boolean = value != null && ID.matches(value)

    // A page of GET /me/playlists. Null when the answer is not such a page.
    fun parsePlaylistsPage(raw: String): SpotifyPage<PlaylistInfo>? {
        val root = parse(raw) as? JsonObject ?: return null
        val entries = root["items"] as? JsonArray ?: return null
        val playlists =
            entries.mapNotNull { entry ->
                val item = entry as? JsonObject ?: return@mapNotNull null
                val id = text(item["id"])?.takeIf { isSpotifyId(it) } ?: return@mapNotNull null
                val owner = item["owner"] as? JsonObject
                // The playlist object calls its song count "items" since 2026, before that "tracks".
                val counter = (item["items"] ?: item["tracks"]) as? JsonObject
                PlaylistInfo(
                    id = id,
                    name = text(item["name"]) ?: id,
                    owner = text(owner?.get("display_name")) ?: text(owner?.get("id")).orEmpty(),
                    trackCount = (counter?.get("total") as? JsonPrimitive)?.intOrNull ?: 0,
                )
            }
        return SpotifyPage(playlists, text(root["next"]))
    }

    // A page of GET /playlists/{id}/items with everything the remote shows. Episodes, local files
    // and removed songs are skipped: they have no track id Spotify could play.
    fun parseTracksPage(raw: String): SpotifyPage<LibraryTrack>? {
        val root = parse(raw) as? JsonObject ?: return null
        val page = (root["items"] as? JsonObject) ?: root
        val entries = page["items"] as? JsonArray ?: return null
        val tracks =
            entries.mapNotNull { entry ->
                val holder = entry as? JsonObject ?: return@mapNotNull null
                val item = (holder["item"] ?: holder["track"]) as? JsonObject ?: return@mapNotNull null
                if (text(item["type"]) == "episode") return@mapNotNull null
                if ((item["is_local"] as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null
                val id = text(item["id"])?.takeIf { isSpotifyId(it) } ?: return@mapNotNull null
                val title = text(item["name"]) ?: return@mapNotNull null
                val artists =
                    (item["artists"] as? JsonArray)
                        ?.mapNotNull { text((it as? JsonObject)?.get("name")) }
                        .orEmpty()
                LibraryTrack(
                    id = id,
                    title = title,
                    artists = artists,
                    album = text((item["album"] as? JsonObject)?.get("name")).orEmpty(),
                    durationMs = (item["duration_ms"] as? JsonPrimitive)?.longOrNull ?: 0L,
                )
            }
        return SpotifyPage(tracks, text(page["next"]))
    }

    fun encode(library: SpotifyLibrary): String = json.encodeToString(SpotifyLibrary.serializer(), library)

    // Null for anything unreadable: the copy is then treated as missing, never as half a library.
    fun decode(raw: String?): SpotifyLibrary? =
        raw?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(SpotifyLibrary.serializer(), it) }.getOrNull() }

    private fun parse(raw: String): JsonElement? = runCatching { json.parseToJsonElement(raw) }.getOrNull()

    private fun text(element: JsonElement?): String? =
        (element as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
}
