package com.hardbasseq.eq.audio

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// The system media volume as a 0..1 position, for the volume-dependent loudness
// compensation (VolumeLevelMapper). Only the media stream matters - that is the
// stream the equalizer session plays on.
interface VolumeRepository {
    val volumeFraction: StateFlow<Float>

    fun startMonitoring()

    fun stopMonitoring()
}

@Singleton
class AndroidVolumeRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : VolumeRepository {
        private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        private val _volumeFraction = MutableStateFlow(readVolumeFraction())
        override val volumeFraction: StateFlow<Float> = _volumeFraction.asStateFlow()

        private var isMonitoring = false

        // Settings.System holds one "volume_<stream>..." row per stream, so any
        // volume change notifies this observer; re-reading the media stream is
        // cheap and MutableStateFlow drops the update if the value is unchanged.
        private val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    _volumeFraction.value = readVolumeFraction()
                }
            }

        override fun startMonitoring() {
            if (isMonitoring) return
            context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
            isMonitoring = true
            _volumeFraction.value = readVolumeFraction()
        }

        override fun stopMonitoring() {
            if (!isMonitoring) return
            context.contentResolver.unregisterContentObserver(observer)
            isMonitoring = false
        }

        private fun readVolumeFraction(): Float {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max <= 0) return 1f
            return (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max).coerceIn(0f, 1f)
        }
    }
