package com.hardbasseq.eq.link

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// A long Spotify playlist is looked up on SoundCloud in blocks of 100 songs. After every
// block the position is saved, so an interrupted import (app closed, no network) goes on
// where it stopped instead of starting over, and each block becomes its own playlist.
@Serializable
data class SpotifyImportState(
    // Identifies the source (the playlist links), so sharing the same links again resumes.
    val key: String,
    val title: String,
    val tracks: List<SpotifyTrack>,
    // How many songs are done (looked up), counted from the start of [tracks].
    val nextIndex: Int = 0,
    // How many playlists were written so far.
    val partsWritten: Int = 0,
    // How many songs found on SoundCloud so far, over all blocks.
    val foundSoFar: Int = 0,
) {
    val total: Int get() = tracks.size
    val isDone: Boolean get() = nextIndex >= tracks.size
}

object SpotifyImportPlan {
    const val BATCH_SIZE = 100

    fun keyFor(playlistIds: List<String>): String = playlistIds.joinToString("+")

    fun start(
        key: String,
        title: String,
        tracks: List<SpotifyTrack>,
    ): SpotifyImportState = SpotifyImportState(key = key, title = title, tracks = tracks)

    // The next block to look up (empty when everything is done).
    fun nextBatch(state: SpotifyImportState): List<SpotifyTrack> = state.tracks.drop(state.nextIndex).take(BATCH_SIZE)

    // The state after a block of [size] songs was looked up; [found] of them were found
    // and written to a playlist ([wrotePart]).
    fun advance(
        state: SpotifyImportState,
        size: Int,
        found: Int,
        wrotePart: Boolean,
    ): SpotifyImportState =
        state.copy(
            nextIndex = (state.nextIndex + size).coerceAtMost(state.tracks.size),
            partsWritten = state.partsWritten + if (wrotePart) 1 else 0,
            foundSoFar = state.foundSoFar + found,
        )

    fun totalParts(trackCount: Int): Int = if (trackCount <= 0) 0 else (trackCount + BATCH_SIZE - 1) / BATCH_SIZE

    // "Mix (von Spotify)" for a list that fits one block, "Mix – Teil 2 von 4 (von Spotify)" otherwise.
    fun playlistName(
        title: String,
        partNumber: Int,
        totalParts: Int,
    ): String = if (totalParts <= 1) "$title (von Spotify)" else "$title – Teil $partNumber von $totalParts (von Spotify)"

    // Which block (1-based) the next one to look up is.
    fun nextPartNumber(state: SpotifyImportState): Int = state.nextIndex / BATCH_SIZE + 1
}

object SpotifyImportJson {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun encode(state: SpotifyImportState): String = json.encodeToString(SpotifyImportState.serializer(), state)

    // Unreadable data reads as "nothing pending".
    fun decode(raw: String?): SpotifyImportState? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(SpotifyImportState.serializer(), raw) }.getOrNull()
    }
}
