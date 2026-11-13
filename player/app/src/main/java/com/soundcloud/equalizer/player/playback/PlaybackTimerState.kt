package com.soundcloud.equalizer.player.playback

import android.content.Context
import com.hardbasseq.eq.playback.PlaybackFade
import com.hardbasseq.eq.playback.SleepTimerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// What the sleep timer is doing; written only by AudioPlayerService (same-process singleton,
// like NowPlayingState). Null = no timer running.
object SleepTimerState {
    private val _status = MutableStateFlow<SleepTimerStatus?>(null)
    val status: StateFlow<SleepTimerStatus?> = _status.asStateFlow()

    fun publish(status: SleepTimerStatus?) {
        _status.value = status
    }
}

// Length of the fade out/in at the edges of every track in ms, 0 = off. Remembered in
// SharedPreferences; read by AudioPlayerService, set by the sound sheet.
//
// This is a dip, not an overlap: the old track fades out, the next fades in. A real crossfade
// plays the end of one track while the next already starts, which needs a second decoder and
// output path running at once - not what the single ExoPlayer here does.
object PlaybackFadeState {
    private const val PREFS = "playback_fade"
    private const val KEY_FADE_MS = "track_fade_ms"

    private val state = MutableStateFlow(0L)
    val trackFadeMs: StateFlow<Long> = state.asStateFlow()

    private var loaded = false

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val saved =
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_FADE_MS, 0L)
        state.value = saved.coerceIn(0L, PlaybackFade.MAX_TRACK_FADE_MS)
    }

    fun set(
        context: Context,
        fadeMs: Long,
    ) {
        load(context)
        val value = fadeMs.coerceIn(0L, PlaybackFade.MAX_TRACK_FADE_MS)
        state.value = value
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_FADE_MS, value)
            .apply()
    }
}
