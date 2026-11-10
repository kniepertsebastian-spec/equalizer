package com.hardbasseq.eq.audio

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AudioPlaybackMonitor"

/**
 * Monitors system-wide audio playback using AudioManager.AudioPlaybackCallback (API 26+).
 * This allows detecting when ANY app (Spotify, YouTube, etc.) starts or stops playing audio,
 * even when our service isn't running. Used to automatically restart the EQ service when
 * music starts playing.
 */
interface AudioPlaybackMonitor {
    /** True if any app is currently playing audio on the system. */
    val isAnyAppPlaying: StateFlow<Boolean>

    /** Start monitoring for audio playback changes across all apps. */
    fun startMonitoring()

    /** Stop monitoring. */
    fun stopMonitoring()
}

@Singleton
@RequiresApi(Build.VERSION_CODES.O)
class AndroidAudioPlaybackMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AudioPlaybackMonitor {
        private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        private val _isAnyAppPlaying = MutableStateFlow(false)
        override val isAnyAppPlaying: StateFlow<Boolean> = _isAnyAppPlaying.asStateFlow()

        private var isMonitoring = false

        private val playbackCallback =
            object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                    // Filter for active playback: state STARTED and not silent
                    val hasActivePlayback =
                        configs.any { config ->
                            config.isActive &&
                                config.audioAttributes.usage != android.media.AudioAttributes.USAGE_UNKNOWN
                        }

                    if (_isAnyAppPlaying.value != hasActivePlayback) {
                        Log.d(
                            TAG,
                            "Audio playback state changed: $hasActivePlayback (${configs.size} configs, " +
                                "${configs.count { it.isActive }} active)",
                        )
                        _isAnyAppPlaying.value = hasActivePlayback
                    }
                }
            }

        override fun startMonitoring() {
            if (isMonitoring) return
            Log.d(TAG, "Starting audio playback monitoring")
            audioManager.registerAudioPlaybackCallback(playbackCallback, null)
            isMonitoring = true

            // Initial state check
            val currentConfigs = audioManager.activePlaybackConfigurations ?: emptyList()
            playbackCallback.onPlaybackConfigChanged(currentConfigs)
        }

        override fun stopMonitoring() {
            if (!isMonitoring) return
            Log.d(TAG, "Stopping audio playback monitoring")
            try {
                audioManager.unregisterAudioPlaybackCallback(playbackCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister playback callback", e)
            } finally {
                isMonitoring = false
            }
        }
    }

@Singleton
class NoOpAudioPlaybackMonitor
    @Inject
    constructor() : AudioPlaybackMonitor {
        private val _isAnyAppPlaying = MutableStateFlow(false)
        override val isAnyAppPlaying: StateFlow<Boolean> = _isAnyAppPlaying.asStateFlow()

        override fun startMonitoring() {
            Log.d(TAG, "Playback monitoring not available on API < 26")
        }

        override fun stopMonitoring() {}
    }
