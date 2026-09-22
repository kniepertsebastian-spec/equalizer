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

@Singleton
class AndroidAudioSessionRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : AudioSessionRepository {
        private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        private val _sessions = MutableStateFlow<List<AudioSession>>(emptyList())
        override val sessions: StateFlow<List<AudioSession>> = _sessions.asStateFlow()

        private val _activeSession = MutableStateFlow<AudioSession?>(null)
        override val activeSession: StateFlow<AudioSession?> = _activeSession.asStateFlow()

        private var isListening = false

        // Best-effort package-name annotations picked up from sessionReceiver's OPEN
        // broadcast - AudioPlaybackConfiguration doesn't expose the owning package to
        // a normal (non-privileged) app.
        private val packageNamesBySessionId = mutableMapOf<Int, String>()

        // Primary session source: AudioManager reports every currently active
        // AudioTrack/MediaPlayer session system-wide, driven by the audio framework
        // itself - unlike ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION below, it doesn't
        // depend on the playing app choosing to send a broadcast. Many modern players
        // (e.g. built on ExoPlayer/Media3, seemingly including SoundCloud) never send
        // that broadcast at all; this is what lets HardBass EQ attach to those too.
        // getAudioSessionId() requires the MODIFY_AUDIO_SETTINGS permission to return
        // real (non-zero) values for other apps' sessions.
        private val playbackCallback =
            object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                    val activeIds =
                        configs
                            .orEmpty()
                            .map { it.audioSessionId }
                            .filter { it != AudioManager.AUDIO_SESSION_ID_GENERATE }
                            .distinct()

                    val newSessions =
                        activeIds.map { id ->
                            AudioSession(
                                sessionId = id,
                                packageName = packageNamesBySessionId[id] ?: "unknown",
                                active = true,
                            )
                        }
                    _sessions.value = newSessions

                    val currentActiveId = _activeSession.value?.sessionId
                    _activeSession.value =
                        when {
                            currentActiveId != null && activeIds.contains(currentActiveId) ->
                                newSessions.first { it.sessionId == currentActiveId }
                            newSessions.isNotEmpty() -> newSessions.first()
                            else -> null
                        }
                }
            }

        // Secondary, cooperative-only source: gives the real package name and fires
        // slightly earlier than the next AudioPlaybackCallback tick when a player does
        // send it - but per the M0 spike and real-world testing, many players don't.
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
                            Log.d(TAG, "Audio effect control session opened: $sessionId from $packageName")
                            packageNamesBySessionId[sessionId] = packageName

                            val annotated =
                                AudioSession(sessionId = sessionId, packageName = packageName, active = true)
                            _sessions.value = _sessions.value.filter { it.sessionId != sessionId } + annotated
                            if (_activeSession.value == null || _activeSession.value?.sessionId == sessionId) {
                                _activeSession.value = annotated
                            }
                        }

                        AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                            Log.d(TAG, "Audio effect control session closed: $sessionId")
                            packageNamesBySessionId.remove(sessionId)
                            // Actual removal from `sessions` is left to the next
                            // AudioPlaybackCallback tick (the authoritative source, and
                            // it does fire promptly on this exact change) - this only
                            // moves `activeSession` off a session that's closing.
                            if (_activeSession.value?.sessionId == sessionId) {
                                _activeSession.value = _sessions.value.firstOrNull { it.sessionId != sessionId }
                            }
                        }
                    }
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
            audioManager.registerAudioPlaybackCallback(playbackCallback, null)
            isListening = true
        }

        override fun stopListening() {
            if (!isListening) return
            try {
                context.unregisterReceiver(sessionReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister session receiver", e)
            }
            audioManager.unregisterAudioPlaybackCallback(playbackCallback)
            isListening = false
        }

        override fun setActiveSession(session: AudioSession?) {
            _activeSession.value = session
        }
    }
