package com.hardbasseq.eq.ui.player

import androidx.lifecycle.ViewModel
import com.hardbasseq.eq.integration.PlaybackTimers
import com.hardbasseq.eq.playback.SleepTimerSetting
import com.hardbasseq.eq.playback.SleepTimerStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlaybackTimersViewModel
    @Inject
    constructor(
        private val timers: PlaybackTimers,
    ) : ViewModel() {
        val sleepTimer: StateFlow<SleepTimerStatus?> = timers.sleepTimer
        val trackFadeMs: StateFlow<Long> = timers.trackFadeMs

        fun startSleepTimer(setting: SleepTimerSetting) = timers.startSleepTimer(setting)

        fun cancelSleepTimer() = timers.cancelSleepTimer()

        fun setTrackFadeSeconds(seconds: Int) = timers.setTrackFade(seconds * 1000L)
    }
