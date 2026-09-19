package com.hardbasseq.eq.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
        private val _sessions = MutableStateFlow<List<AudioSession>>(emptyList())
        override val sessions: StateFlow<List<AudioSession>> = _sessions.asStateFlow()

        private val _activeSession = MutableStateFlow<AudioSession?>(null)
        override val activeSession: StateFlow<AudioSession?> = _activeSession.asStateFlow()

        private var isListening = false

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
                            val newSession = AudioSession(sessionId = sessionId, packageName = packageName, active = true)
                            val currentList = _sessions.value.filter { it.sessionId != sessionId }
                            _sessions.value = currentList + newSession
                            if (_activeSession.value == null) {
                                _activeSession.value = newSession
                            }
                        }

                        AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                            Log.d(TAG, "Audio effect control session closed: $sessionId")
                            _sessions.value = _sessions.value.filter { it.sessionId != sessionId }
                            if (_activeSession.value?.sessionId == sessionId) {
                                _activeSession.value = _sessions.value.lastOrNull()
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
            isListening = true
        }

        override fun stopListening() {
            if (!isListening) return
            try {
                context.unregisterReceiver(sessionReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister session receiver", e)
            } finally {
                isListening = false
            }
        }

        override fun setActiveSession(session: AudioSession?) {
            _activeSession.value = session
        }
    }
