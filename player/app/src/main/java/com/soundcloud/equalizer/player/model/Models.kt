package com.soundcloud.equalizer.player.model

data class TrackItem(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val streamUrl: String?,
    val durationMs: Long
)

data class PlaylistItem(
    val id: Long,
    val title: String,
    val trackCount: Int,
    val artworkUrl: String?,
    val tracks: List<TrackItem> = emptyList()
)
