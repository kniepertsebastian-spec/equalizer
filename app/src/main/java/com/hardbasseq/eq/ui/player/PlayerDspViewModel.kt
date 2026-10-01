package com.hardbasseq.eq.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import com.hardbasseq.eq.dsp.BassMonoSummerSettings
import com.hardbasseq.eq.dsp.PlayerDspSettings
import com.soundcloud.equalizer.player.playback.PlayerDspState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

// Mono bass and limiter of the built-in player: both on by default, each switchable.
@HiltViewModel
class PlayerDspViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : ViewModel() {
        init {
            PlayerDspState.load(context)
        }

        val settings: StateFlow<PlayerDspSettings> = PlayerDspState.settings

        fun setMonoBass(enabled: Boolean) = PlayerDspState.update(context) { it.copy(monoBassEnabled = enabled) }

        fun setMonoBassCutoff(hz: Float) =
            PlayerDspState.update(context) {
                it.copy(
                    monoBassCutoffHz = hz.coerceIn(BassMonoSummerSettings.MIN_CUTOFF_HZ, BassMonoSummerSettings.MAX_CUTOFF_HZ),
                )
            }

        fun setLimiter(enabled: Boolean) = PlayerDspState.update(context) { it.copy(limiterEnabled = enabled) }
    }
