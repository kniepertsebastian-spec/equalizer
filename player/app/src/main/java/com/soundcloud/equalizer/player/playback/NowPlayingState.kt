package com.soundcloud.equalizer.player.playback

import com.soundcloud.equalizer.player.model.TrackItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val artworkUrl: String? = null,
    // 0 while unknown (before the stream is ready, or for a live stream).
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    // Position inside PlaybackQueueState.queue, -1 when not playing from a queue.
    val queueIndex: Int = -1,
    // True from the moment a track is chosen until its stream is ready to play.
    val isLoading: Boolean = false,
    // The stream being played is only a ~30 second preview, not the full track.
    val isPreview: Boolean = false,
)

// AudioPlayerService isn't Hilt-managed (predates the merge into :app, see
// settings.gradle.kts), and HardBass EQ's MainViewModel has no service connection of
// its own to read playback state from - both are in the same process now, so a plain
// shared singleton is enough to let the main screen's mini-player bar reflect what
// AudioPlayerService is actually doing without either side needing to bind to the
// other.
object NowPlayingState {
    private val _current = MutableStateFlow<NowPlaying?>(null)
    val current: StateFlow<NowPlaying?> = _current.asStateFlow()

    fun update(value: NowPlaying?) {
        _current.value = value
    }
}

// The list the player steps through with next/previous. Written by the UI
// (PlayerController.playQueue) before it tells the service which index to start
// at; read by AudioPlayerService. Same shared-singleton approach as NowPlayingState,
// for the same reason: there is no service connection to pass it over.
object PlaybackQueueState {
    private val _queue = MutableStateFlow<List<TrackItem>>(emptyList())
    val queue: StateFlow<List<TrackItem>> = _queue.asStateFlow()

    fun setQueue(tracks: List<TrackItem>) {
        _queue.value = tracks
    }
}
