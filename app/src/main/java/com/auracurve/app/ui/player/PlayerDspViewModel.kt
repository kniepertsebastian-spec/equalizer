package com.auracurve.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auracurve.app.dsp.BassMonoSummerSettings
import com.auracurve.app.dsp.PlayerDspSettings
import com.auracurve.app.player.playback.PlayerDspStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Mono bass and limiter of the built-in player: both on by default, each switchable.
@HiltViewModel
class PlayerDspViewModel
    @Inject
    constructor(
        private val store: PlayerDspStore,
    ) : ViewModel() {
        init {
            // Off the main thread (SharedPreferences); the defaults show until it is read.
            viewModelScope.launch { store.load() }
        }

        val settings: StateFlow<PlayerDspSettings> = store.settings

        fun setMonoBass(enabled: Boolean) = store.updateSettings { it.copy(monoBassEnabled = enabled) }

        fun setMonoBassCutoff(hz: Float) =
            store.updateSettings {
                it.copy(
                    monoBassCutoffHz = hz.coerceIn(BassMonoSummerSettings.MIN_CUTOFF_HZ, BassMonoSummerSettings.MAX_CUTOFF_HZ),
                )
            }

        fun setLimiter(enabled: Boolean) = store.updateSettings { it.copy(limiterEnabled = enabled) }
    }
