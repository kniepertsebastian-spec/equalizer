package com.hardbasseq.eq.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10

interface SystemVolumeRepository {
    val currentLevelDb: StateFlow<Float>

    fun startMonitoring()

    fun stopMonitoring()
}

// Chat feature: "quality changes for headphones" (item not chosen back when
// gentler dynamics was picked instead) - a rough Fletcher-Munson-shaped bass/
// treble boost that grows the quieter the user listens, see
// dsp/LoudnessCompensationCurve.kt. That function only needs the *relative*
// gap to a reference level, not calibrated SPL, which this app has no way to
// measure - the media stream's current/max volume index ratio, expressed as
// a dB-ish quantity via 20*log10(fraction), is a reasonable proxy without any
// device- or headphone-specific calibration.
@Singleton
class AndroidSystemVolumeRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : SystemVolumeRepository {
        private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        private val _currentLevelDb = MutableStateFlow(levelDbFromCurrentVolume())
        override val currentLevelDb: StateFlow<Float> = _currentLevelDb.asStateFlow()

        private var isMonitoring = false

        private val volumeReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    receivedContext: Context?,
                    intent: Intent?,
                ) {
                    _currentLevelDb.value = levelDbFromCurrentVolume()
                }
            }

        override fun startMonitoring() {
            if (isMonitoring) return
            // "android.media.VOLUME_CHANGED_ACTION" has no public SDK constant but is
            // a protected system broadcast (fired by AudioService itself, including
            // for hardware volume-key presses) - same registration pattern as
            // AudioSessionRepository's own receiver.
            ContextCompat.registerReceiver(
                context,
                volumeReceiver,
                IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            isMonitoring = true
            _currentLevelDb.value = levelDbFromCurrentVolume()
        }

        override fun stopMonitoring() {
            if (!isMonitoring) return
            context.unregisterReceiver(volumeReceiver)
            isMonitoring = false
        }

        private fun levelDbFromCurrentVolume(): Float {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (maxVolume <= 0 || currentVolume <= 0) return MUTE_LEVEL_DB
            val fraction = currentVolume.toFloat() / maxVolume.toFloat()
            return (20f * log10(fraction)).coerceAtLeast(MUTE_LEVEL_DB)
        }

        companion object {
            // Silence isn't -infinity dB in a bounded model - clamped to comfortably
            // below LoudnessCompensationCurve's own 40 dB drop-to-max-boost range, so
            // it always reads as "fully compensated" rather than a huge but finite
            // number.
            private const val MUTE_LEVEL_DB = -60f
        }
    }
