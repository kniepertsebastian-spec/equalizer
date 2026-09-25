package com.soundcloud.equalizer.player.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
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
