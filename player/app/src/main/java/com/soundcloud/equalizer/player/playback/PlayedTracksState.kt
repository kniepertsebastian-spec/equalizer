package com.soundcloud.equalizer.player.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

// Which tracks were started in the player since the app process began, so lists can
// show what was already played. Same-process singleton like NowPlayingState; not saved
// across app restarts.
object PlayedTracksState {
    private const val MAX_REMEMBERED = 5_000

    private val state = MutableStateFlow<Set<Long>>(emptySet())
    val ids: StateFlow<Set<Long>> = state

    fun mark(trackId: Long) {
        state.update { played ->
            if (trackId in played) {
                played
            } else {
                // Oldest first, so the cap forgets the oldest entries.
                (played + trackId).let { all -> if (all.size > MAX_REMEMBERED) all.drop(all.size - MAX_REMEMBERED).toSet() else all }
            }
        }
    }
}
