package com.hardbasseq.eq.playlist

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SavedTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
)

// A playlist kept on the device after importing a share link. Only the track
// metadata is stored, never a stream URL: SoundCloud stream URLs are short-lived,
// so the player resolves a fresh one by track id when a track actually plays.
@Serializable
data class SavedPlaylist(
    val id: String,
    val title: String,
    val sourceUrl: String? = null,
    val tracks: List<SavedTrack>,
    val createdAtMs: Long = 0L,
    // Id of the copy in the user's SoundCloud account once it was sent there, so sending again
    // updates that playlist instead of making a second one. Defaulted: older data still decodes.
    val soundCloudId: Long? = null,
)

object SavedPlaylistJson {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun encode(playlists: List<SavedPlaylist>): String = json.encodeToString(SavedPlaylistList.serializer(), SavedPlaylistList(playlists))

    // Corrupt or unreadable data must never keep the app from starting: it simply
    // reads as "no playlists".
    fun decode(raw: String?): List<SavedPlaylist> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(SavedPlaylistList.serializer(), raw).playlists }
            .getOrDefault(emptyList())
    }

    @Serializable
    private data class SavedPlaylistList(
        val playlists: List<SavedPlaylist>,
    )
}
