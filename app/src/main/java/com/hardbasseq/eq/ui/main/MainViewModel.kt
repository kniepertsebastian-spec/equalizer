package com.hardbasseq.eq.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardbasseq.eq.audio.AudioEffectDescriptor
import com.hardbasseq.eq.audio.AudioEffectRepository
import com.hardbasseq.eq.audio.AudioEngine
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.audio.AudioRouteRepository
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

        private val _processingSettings =
            MutableStateFlow(ProcessingSettings().withPreset(BuiltInPresets.CleanPunch))
        val processingSettings: StateFlow<ProcessingSettings> = _processingSettings.asStateFlow()

        // Session listening/attach and route monitoring are owned by
        // AudioSessionForegroundService now, not this ViewModel - that keeps them
        // running for as long as the app process is alive, not just while this
        // screen is open (see roadmap.md Session 13). This ViewModel only reacts to
        // capability changes the (singleton) engine reports.
        init {
            viewModelScope.launch {
                audioEngine.capabilities.collect { caps ->
                    recalculateBandGains(caps.bands)
                }
            }
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
            _processingSettings.value = _processingSettings.value.withPreset(preset)
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
            val newSettings =
                _processingSettings.value.copy(
                    bandGainsDb = currentGains,
                    inputGainDb = automaticInputGainDb(currentGains),
                )
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
            val newSettings =
                _processingSettings.value.copy(
                    bandGainsDb = calculatedGains,
                    inputGainDb = automaticInputGainDb(calculatedGains),
                )
            applySettings(newSettings)
        }

        private fun ProcessingSettings.withPreset(preset: Preset): ProcessingSettings =
            copy(
                inputGainDb = -preset.requestedHeadroomDb,
                macroBassDb = preset.macroBassDb,
                macroPunchDb = preset.macroPunchDb,
                macroHaerteDb = preset.macroHaerteDb,
                limiterEnabled = preset.limiter.enabled,
                limiterThresholdDb = preset.limiter.thresholdDb,
                mbcEnabled = preset.mbcEnabled,
                mbcThresholdDb = preset.mbcThresholdDb,
                mbcRatio = preset.mbcRatio,
            )

        private fun automaticInputGainDb(bandGainsDb: Map<Int, Float>): Float {
            val peakBoostDb = bandGainsDb.values.maxOrNull()?.coerceAtLeast(0f) ?: 0f
            val presetHeadroomDb = _activePreset.value.requestedHeadroomDb.coerceAtLeast(0f)
            val requiredHeadroomDb = maxOf(peakBoostDb, presetHeadroomDb)
            return if (requiredHeadroomDb == 0f) 0f else -requiredHeadroomDb
        }

        private fun applySettings(settings: ProcessingSettings) {
            _processingSettings.value = settings
            viewModelScope.launch {
                audioEngine.apply(settings)
            }
        }
    }
