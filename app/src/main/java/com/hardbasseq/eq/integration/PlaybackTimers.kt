package com.hardbasseq.eq.integration

import android.content.Context
import android.content.Intent
import com.hardbasseq.eq.playback.SleepTimerSetting
import com.hardbasseq.eq.playback.SleepTimerStatus
import com.soundcloud.equalizer.player.playback.PlaybackFadeState
import com.soundcloud.equalizer.player.playback.SleepTimerState
import com.soundcloud.equalizer.player.service.AudioPlayerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The built-in player's sleep timer and the fade between tracks (roadmap 06, P5-6). */
interface PlaybackTimers {
    /** Null = no timer running. */
    val sleepTimer: StateFlow<SleepTimerStatus?>

    /** Fade length at the edges of every track in ms, 0 = off. */
    val trackFadeMs: StateFlow<Long>

    fun startSleepTimer(setting: SleepTimerSetting)

    fun cancelSleepTimer()

    fun setTrackFade(fadeMs: Long)
}

@Singleton
class AndroidPlaybackTimers
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PlaybackTimers {
        init {
            PlaybackFadeState.load(context)
        }

        override val sleepTimer: StateFlow<SleepTimerStatus?> = SleepTimerState.status
        override val trackFadeMs: StateFlow<Long> = PlaybackFadeState.trackFadeMs

        override fun startSleepTimer(setting: SleepTimerSetting) {
            val minutes =
                when (setting) {
                    is SleepTimerSetting.AfterMinutes -> setting.minutes
                    SleepTimerSetting.EndOfTrack -> AudioPlayerService.SLEEP_END_OF_TRACK
                }
            send(AudioPlayerService.ACTION_START_SLEEP_TIMER) { putExtra(AudioPlayerService.EXTRA_SLEEP_MINUTES, minutes) }
        }

        override fun cancelSleepTimer() = send(AudioPlayerService.ACTION_CANCEL_SLEEP_TIMER)

        override fun setTrackFade(fadeMs: Long) {
            PlaybackFadeState.set(context, fadeMs)
            send(AudioPlayerService.ACTION_FADE_CHANGED)
        }

        private fun send(
            action: String,
            configure: Intent.() -> Unit = {},
        ) {
            context.startService(
                Intent(context, AudioPlayerService::class.java).apply {
                    this.action = action
                    configure()
                },
            )
        }
    }
