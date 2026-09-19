package com.hardbasseq.eq.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardbasseq.eq.audio.AudioEffectDescriptor
import com.hardbasseq.eq.audio.AudioEffectRepository
import com.hardbasseq.eq.audio.AudioEngine
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.audio.AudioRouteRepository
import com.hardbasseq.eq.audio.AudioSessionRepository
import com.hardbasseq.eq.audio.EqualizerBandCapabilities
import com.hardbasseq.eq.audio.ProcessingSettings
import com.hardbasseq.eq.di.DefaultDispatcher
import com.hardbasseq.eq.dsp.EqualizerInterpolator
import com.hardbasseq.eq.preset.BuiltInPresets
import com.hardbasseq.eq.preset.Preset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val repository: AudioEffectRepository,
        private val audioEngine: AudioEngine,
        private val sessionRepository: AudioSessionRepository,
        private val routeRepository: AudioRouteRepository,
        @DefaultDispatcher private val backgroundDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _showDebugEffects = MutableStateFlow(false)
        val showDebugEffects: StateFlow<Boolean> = _showDebugEffects.asStateFlow()

        private val _effectDescriptors = MutableStateFlow<List<AudioEffectDescriptor>>(emptyList())
        val effectDescriptors: StateFlow<List<AudioEffectDescriptor>> = _effectDescriptors.asStateFlow()

        val engineState: StateFlow<AudioEngineState> = audioEngine.state
        val capabilities = audioEngine.capabilities
        val currentRoute: StateFlow<AudioRoute> = routeRepository.activeRoute

        private val _activePreset = MutableStateFlow<Preset>(BuiltInPresets.CleanPunch)
        val activePreset: StateFlow<Preset> = _activePreset.asStateFlow()

        private val _processingSettings = MutableStateFlow(ProcessingSettings())
        val processingSettings: StateFlow<ProcessingSettings> = _processingSettings.asStateFlow()

        init {
            sessionRepository.startListening()
            routeRepository.startMonitoring()

            viewModelScope.launch {
                sessionRepository.activeSession.collect { session ->
                    if (session != null) {
                        audioEngine.attach(session)
                    } else {
                        audioEngine.detach()
                    }
                }
            }

            viewModelScope.launch {
                audioEngine.capabilities.collect { caps ->
                    recalculateBandGains(caps.bands)
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            sessionRepository.stopListening()
            routeRepository.stopMonitoring()
        }

        fun toggleDebugEffects() {
            val showing = !_showDebugEffects.value
            _showDebugEffects.value = showing
            if (showing) {
                viewModelScope.launch {
                    _effectDescriptors.value =
                        withContext(backgroundDispatcher) {
                            repository.queryAvailableEffects()
                        }
                }
            }
        }

        fun selectPreset(preset: Preset) {
            _activePreset.value = preset
            _processingSettings.value =
                _processingSettings.value.copy(
                    inputGainDb = -preset.requestedHeadroomDb,
                    macroBassDb = preset.macroBassDb,
                    macroPunchDb = preset.macroPunchDb,
                    macroHaerteDb = preset.macroHaerteDb,
                    limiterEnabled = preset.limiter.enabled,
                    limiterThresholdDb = preset.limiter.thresholdDb,
                )
            recalculateBandGains()
        }

        fun setMasterEnabled(enabled: Boolean) {
            val newSettings = _processingSettings.value.copy(masterEnabled = enabled)
            applySettings(newSettings)
        }

        fun setBypass(bypass: Boolean) {
            val newSettings = _processingSettings.value.copy(bypass = bypass)
            applySettings(newSettings)
        }

        fun setMacroBass(gainDb: Float) {
            val newSettings = _processingSettings.value.copy(macroBassDb = gainDb)
            _processingSettings.value = newSettings
            recalculateBandGains()
        }

        fun setMacroPunch(gainDb: Float) {
            val newSettings = _processingSettings.value.copy(macroPunchDb = gainDb)
            _processingSettings.value = newSettings
            recalculateBandGains()
        }

        fun setMacroHaerte(gainDb: Float) {
            val newSettings = _processingSettings.value.copy(macroHaerteDb = gainDb)
            _processingSettings.value = newSettings
            recalculateBandGains()
        }

        fun setBandGain(
            bandIndex: Int,
            gainDb: Float,
        ) {
            val currentGains = _processingSettings.value.bandGainsDb.toMutableMap()
            currentGains[bandIndex] = gainDb
            val newSettings = _processingSettings.value.copy(bandGainsDb = currentGains)
            applySettings(newSettings)
        }

        private fun recalculateBandGains(bands: List<EqualizerBandCapabilities> = audioEngine.capabilities.value.bands) {
            val calculatedGains =
                EqualizerInterpolator.interpolatePresetToBands(
                    preset = _activePreset.value,
                    bands = bands,
                    macroBassDb = _processingSettings.value.macroBassDb,
                    macroPunchDb = _processingSettings.value.macroPunchDb,
                    macroHaerteDb = _processingSettings.value.macroHaerteDb,
                )
            val newSettings = _processingSettings.value.copy(bandGainsDb = calculatedGains)
            applySettings(newSettings)
        }

        private fun applySettings(settings: ProcessingSettings) {
            _processingSettings.value = settings
            viewModelScope.launch {
                audioEngine.apply(settings)
            }
        }
    }
