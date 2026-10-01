package com.soundcloud.equalizer.player.playback

import android.content.Context
import com.hardbasseq.eq.dsp.BassMonoSummerSettings
import com.hardbasseq.eq.dsp.PlayerDspSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// The player's output-stage switches (mono bass, limiter), shared between the UI that
// sets them and ExoPlayer's playback thread that reads them - same-process singleton
// for the same reason as VirtualBassState. Remembered in SharedPreferences so the
// choice survives restarts; both parts default to on.
object PlayerDspState {
    private const val PREFS = "player_dsp"
    private const val KEY_MONO_BASS = "mono_bass"
    private const val KEY_CUTOFF = "mono_bass_cutoff_hz"
    private const val KEY_LIMITER = "limiter"

    private val state = MutableStateFlow(PlayerDspSettings())
    val settings: StateFlow<PlayerDspSettings> = state

    private var loaded = false

    // Reads the saved choice once; safe to call from several places.
    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaults = PlayerDspSettings()
        state.value =
            PlayerDspSettings(
                monoBassEnabled = prefs.getBoolean(KEY_MONO_BASS, defaults.monoBassEnabled),
                monoBassCutoffHz =
                    prefs
                        .getFloat(KEY_CUTOFF, defaults.monoBassCutoffHz)
                        .coerceIn(BassMonoSummerSettings.MIN_CUTOFF_HZ, BassMonoSummerSettings.MAX_CUTOFF_HZ),
                limiterEnabled = prefs.getBoolean(KEY_LIMITER, defaults.limiterEnabled),
            )
    }

    fun update(
        context: Context,
        transform: (PlayerDspSettings) -> PlayerDspSettings,
    ) {
        load(context)
        val next = transform(state.value)
        state.value = next
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MONO_BASS, next.monoBassEnabled)
            .putFloat(KEY_CUTOFF, next.monoBassCutoffHz)
            .putBoolean(KEY_LIMITER, next.limiterEnabled)
            .apply()
    }
}
