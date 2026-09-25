package com.hardbasseq.eq.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAudioEngine(
    initialCapabilities: AudioCapabilities = defaultFakeCapabilities(),
) : AudioEngine {
    private val _capabilities = MutableStateFlow(initialCapabilities)
    override val capabilities: StateFlow<AudioCapabilities> = _capabilities.asStateFlow()

    private val _state = MutableStateFlow<AudioEngineState>(AudioEngineState.Detached)
    override val state: StateFlow<AudioEngineState> = _state.asStateFlow()

    private val _currentSettings = MutableStateFlow(ProcessingSettings())
    override val currentSettings: StateFlow<ProcessingSettings> = _currentSettings.asStateFlow()

    override suspend fun attach(session: AudioSession): Boolean {
        _state.value = AudioEngineState.Attaching(session.sessionId)
        _state.value = AudioEngineState.Active(session.sessionId)
        return true
    }

    override suspend fun detach() {
        _state.value = AudioEngineState.Listening
    }

    override suspend fun apply(settings: ProcessingSettings): Boolean {
        _currentSettings.value = settings
        return true
    }

    fun updateCapabilities(capabilities: AudioCapabilities) {
        _capabilities.value = capabilities
    }

    companion object {
        fun defaultFakeCapabilities(): AudioCapabilities =
            AudioCapabilities(
                hasEqualizer = true,
                hasDynamicsProcessing = true,
                hasBassBoost = true,
                hasLoudnessEnhancer = true,
                totalEffectCount = 4,
                bands =
                    listOf(
                        EqualizerBandCapabilities(0, 60, -15f, 15f),
                        EqualizerBandCapabilities(1, 230, -15f, 15f),
                        EqualizerBandCapabilities(2, 910, -15f, 15f),
                        EqualizerBandCapabilities(3, 3600, -15f, 15f),
                        EqualizerBandCapabilities(4, 14000, -15f, 15f),
                    ),
                hasInputGain = true,
                hasLimiter = true,
                hasMbc = true,
            )
    }
}
