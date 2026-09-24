package com.hardbasseq.eq.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.AudioEffect
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AudioSessionRepository"

interface AudioSessionRepository {
    val sessions: StateFlow<List<AudioSession>>
    val activeSession: StateFlow<AudioSession?>

    fun startListening()

    fun stopListening()

    fun setActiveSession(session: AudioSession?)
}

// Two independent, complementary discovery paths are combined here because neither one alone
// covers all target players (roadmap-2026.md Release-Gate A / docs/DECISIONS.md):
// - The broadcast path (ACTION_OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION) only fires for players
//   that explicitly choose to send it as a courtesy to third-party equalizers. Confirmed working
//   for Spotify; confirmed NOT sent by SoundCloud and YouTube on the M0 test device (see
//   docs/TEST_MATRIX.md) - this is an app-side opt-in, not something a receiver can force.
// - AudioManager.AudioPlaybackCallback (API 26+) reports ANY app's active playback system-wide,
//   independent of cooperation, and AudioPlaybackConfiguration.getAudioSessionId() (API 28+,
//   matches minSdk) returns the real session id as long as MODIFY_AUDIO_SETTINGS is held - a
//   normal, install-time-granted permission, not a runtime prompt. It does not report a package
//   name, so sessions discovered only this way keep AudioSession's "unknown" default.
@Singleton
class AndroidAudioSessionRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : AudioSessionRepository {
        private val _sessions = MutableStateFlow<List<AudioSession>>(emptyList())
        override val sessions: StateFlow<List<AudioSession>> = _sessions.asStateFlow()

        private val _activeSession = MutableStateFlow<AudioSession?>(null)
        override val activeSession: StateFlow<AudioSession?> = _activeSession.asStateFlow()

        private var isListening = false

        // Both callbacks below run on the main thread (no Handler passed to either
        // registration call), so plain mutable maps are safe without extra synchronization.
        private val broadcastSessions = mutableMapOf<Int, AudioSession>()
        private val playbackCallbackSessionIds = mutableSetOf<Int>()

        private val audioManager = context.getSystemService(AudioManager::class.java)

        private val sessionReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    if (intent == null) return
                    val action = intent.action ?: return
                    val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
                    val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "unknown"

                    if (sessionId == AudioEffect.ERROR_BAD_VALUE || sessionId <= 0) return

                    when (action) {
                        AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                            Log.d(TAG, "Audio effect control session opened (broadcast): $sessionId from $packageName")
                            broadcastSessions[sessionId] = AudioSession(sessionId = sessionId, packageName = packageName, active = true)
                            publishSessions()
                        }

                        AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                            Log.d(TAG, "Audio effect control session closed (broadcast): $sessionId")
                            broadcastSessions.remove(sessionId)
                            publishSessions()
                        }
                    }
                }
            }

        private val playbackCallback =
            object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                    val activeSessionIds =
                        configs
                            .orEmpty()
                            .filter { it.playerState == AudioPlaybackConfiguration.PLAYER_STATE_STARTED }
                            .mapNotNull { config ->
                                val sessionId =
                                    try {
                                        config.audioSessionId
                                    } catch (e: SecurityException) {
                                        Log.w(TAG, "No permission to read audio session id from playback callback", e)
                                        return@mapNotNull null
                                    }
                                sessionId.takeIf { it > 0 }
                            }.toSet()

                    if (activeSessionIds == playbackCallbackSessionIds) return
                    Log.d(TAG, "Playback-callback session ids changed: $activeSessionIds")
                    playbackCallbackSessionIds.clear()
                    playbackCallbackSessionIds.addAll(activeSessionIds)
                    publishSessions()
                }
            }

        private fun publishSessions() {
            val combined =
                broadcastSessions.values.toList() +
                    playbackCallbackSessionIds
                        .filterNot { broadcastSessions.containsKey(it) }
                        .map { AudioSession(sessionId = it, active = true) }
            _sessions.value = combined

            val currentActiveId = _activeSession.value?.sessionId
            if (currentActiveId == null || combined.none { it.sessionId == currentActiveId }) {
                _activeSession.value = combined.firstOrNull()
            }
        }

        override fun startListening() {
            if (isListening) return
            val filter =
                IntentFilter().apply {
                    addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                    addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                }
            ContextCompat.registerReceiver(
                context,
                sessionReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            try {
                audioManager?.registerAudioPlaybackCallback(playbackCallback, null)
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to register AudioPlaybackCallback, falling back to broadcast-only discovery", e)
            }
            isListening = true
        }

        override fun stopListening() {
            if (!isListening) return
            try {
                context.unregisterReceiver(sessionReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister session receiver", e)
            } finally {
                audioManager?.unregisterAudioPlaybackCallback(playbackCallback)
                broadcastSessions.clear()
                playbackCallbackSessionIds.clear()
                isListening = false
            }
        }

        override fun setActiveSession(session: AudioSession?) {
            _activeSession.value = session
        }
    }
