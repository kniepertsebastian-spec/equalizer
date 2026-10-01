package com.hardbasseq.eq.discovery

import com.hardbasseq.eq.playlist.SavedTrack
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// One suggested upload: the track, which watched artist it was found for, and when it
// was uploaded.
@Serializable
data class DiscoveryTrack(
    val track: SavedTrack,
    val artist: String,
    val uploadedAtMs: Long,
    // The genre the uploader gave it ("" if none) and whether it positively fits the
    // wanted genres - false for uploads with no genre info, which are only used after
    // all confirmed ones.
    val genre: String = "",
    val genreConfirmed: Boolean = true,
)

// Which genres an upload has to fit to be suggested.
@Serializable
enum class GenreMode {
    // No genre filtering.
    OFF,

    // The genres of the tracks the user liked (derived weekly).
    AUTO,

    // The keywords the user typed in.
    MANUAL,
}

// Everything behind "Interesting new uploads": the artists to watch, the current
// weekly playlist, candidates held back for refills, and what was already shown or
// swiped away (so it never comes back).
@Serializable
data class DiscoveryState(
    val artists: List<String> = emptyList(),
    val playlist: List<DiscoveryTrack> = emptyList(),
    val pool: List<DiscoveryTrack> = emptyList(),
    val seenIds: List<Long> = emptyList(),
    val dismissedIds: List<Long> = emptyList(),
    val genreMode: GenreMode = GenreMode.AUTO,
    // MANUAL: what the user typed. AUTO: what was derived from their likes, and when.
    val genres: List<String> = emptyList(),
    val autoGenres: List<String> = emptyList(),
    val autoGenresWeek: Long = NEVER,
    // The week (WeekKey) the playlist was last built for; NEVER forces a rebuild.
    val weekKey: Long = NEVER,
    val updatedAtMs: Long = 0L,
) {
    companion object {
        const val NEVER = -1L
    }
}

object DiscoveryStateJson {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun encode(state: DiscoveryState): String = json.encodeToString(DiscoveryState.serializer(), state)

    // Unreadable data reads as a fresh, empty state - never a reason the app cannot start.
    fun decode(raw: String?): DiscoveryState {
        if (raw.isNullOrBlank()) return DiscoveryState()
        return runCatching { json.decodeFromString(DiscoveryState.serializer(), raw) }.getOrDefault(DiscoveryState())
    }
}
