package com.hardbasseq.eq.link

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// What was already imported from which Spotify source, so that sharing the same playlist
// again does not import the same songs twice. Per source (see SpotifyImportPlan.keyFor) it
// remembers each song and the SoundCloud track found for it (0 = nothing found).
@Serializable
data class SpotifyImportLedger(
    val sources: Map<String, Map<String, Long>> = emptyMap(),
) {
    companion object {
        const val NOT_FOUND = 0L
        private const val MAX_SOURCES = 50

        fun songKey(track: SpotifyTrack): String = "${track.artist.trim().lowercase()}|${track.title.trim().lowercase()}"

        // The songs that still have to be imported: everything not done before. A song counts as
        // done when its SoundCloud track is still in one of the user's playlists
        // ([presentTrackIds]) - delete the playlist and importing again brings it back. Songs
        // that were not found on SoundCloud are tried again (they may be there by now).
        fun remaining(
            ledger: SpotifyImportLedger,
            sourceKey: String,
            tracks: List<SpotifyTrack>,
            presentTrackIds: Set<Long>,
        ): List<SpotifyTrack> {
            val done = ledger.sources[sourceKey].orEmpty()
            return tracks.filterNot { track ->
                val id = done[songKey(track)]
                id != null && id != NOT_FOUND && id in presentTrackIds
            }
        }

        // The ledger with [songs] (song key -> SoundCloud id or NOT_FOUND) added for [sourceKey].
        fun record(
            ledger: SpotifyImportLedger,
            sourceKey: String,
            songs: Map<String, Long>,
        ): SpotifyImportLedger {
            val merged = ledger.sources[sourceKey].orEmpty() + songs
            // Oldest sources go first when there are too many.
            val all = (ledger.sources - sourceKey) + (sourceKey to merged)
            return SpotifyImportLedger(
                all.entries
                    .toList()
                    .takeLast(MAX_SOURCES)
                    .associate { it.key to it.value },
            )
        }

        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        fun encode(ledger: SpotifyImportLedger): String = json.encodeToString(serializer(), ledger)

        // Unreadable data reads as an empty ledger.
        fun decode(raw: String?): SpotifyImportLedger {
            if (raw.isNullOrBlank()) return SpotifyImportLedger()
            return runCatching { json.decodeFromString(serializer(), raw) }.getOrDefault(SpotifyImportLedger())
        }
    }
}
