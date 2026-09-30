package com.soundcloud.equalizer.player.model

data class TrackItem(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val streamUrl: String?,
    val durationMs: Long,
    // True when the resolved stream is only SoundCloud's ~30 second preview of a
    // Go track (see StreamSelection). Only known once a stream was resolved.
    val isPreview: Boolean = false
)

data class PlaylistItem(
    val id: Long,
    val title: String,
    val trackCount: Int,
    val artworkUrl: String?,
    val tracks: List<TrackItem> = emptyList()
)

// Outcome of resolving a pasted/shared SoundCloud link (see SoundCloudClient.resolveLink).
sealed class ResolvedLink {
    data class SingleTrack(val track: TrackItem) : ResolvedLink()

    data class Playlist(
        val title: String,
        val sourceUrl: String,
        val tracks: List<TrackItem>,
    ) : ResolvedLink()

    // Well-formed SoundCloud link that is not a track or playlist (a profile, a
    // station, ...), or one the API does not know.
    data class Unsupported(val reason: String) : ResolvedLink()
}
