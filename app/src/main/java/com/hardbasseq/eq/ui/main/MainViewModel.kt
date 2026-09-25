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
import com.hardbasseq.eq.diagnostics.DiagnosticsRecorder
import com.hardbasseq.eq.dsp.EqualizerInterpolator
import com.hardbasseq.eq.integration.PlayerBridge
import com.hardbasseq.eq.integration.PlayerSource
import com.hardbasseq.eq.preset.BuiltInPresets
import com.hardbasseq.eq.preset.LimiterConfig
import com.hardbasseq.eq.preset.Preset
import com.hardbasseq.eq.preset.PresetMetadata
import com.hardbasseq.eq.preset.PresetRepository
import com.hardbasseq.eq.preset.TargetPoint
import com.hardbasseq.eq.settings.AppSettingsRepository
import com.hardbasseq.eq.settings.LiveSettings
import com.soundcloud.equalizer.player.playback.NowPlaying
import com.soundcloud.equalizer.player.playback.NowPlayingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

// How much of the peak EQ boost the automatic input-gain cut pre-cancels before the
// signal ever reaches dynamics processing. At 1.0 the boost is fully cancelled before
// the Limiter (still a hard, unchanged 10:1-ratio ceiling) gets a chance to do its own
// job, which made every preset sound indistinguishable from flat - see roadmap.md
// Session 16. Lowered further to 0.3 in Session 17 at the user's explicit request for
// more aggressive kicks/less pre-cancellation, after 0.5 still felt too subtle - the
// Limiter is still unchanged and remains the actual clipping safety net.
private const val INPUT_GAIN_SAFETY_RATIO = 0.3f

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val repository: AudioEffectRepository,
        private val audioEngine: AudioEngine,
        private val routeRepository: AudioRouteRepository,
        private val diagnosticsRecorder: DiagnosticsRecorder,
        private val playerBridge: PlayerBridge,
        private val presetRepository: PresetRepository,
        private val appSettingsRepository: AppSettingsRepository,
        @DefaultDispatcher private val backgroundDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _showDebugEffects = MutableStateFlow(false)
        val showDebugEffects: StateFlow<Boolean> = _showDebugEffects.asStateFlow()

        private val _showSourcePicker = MutableStateFlow(false)
        val showSourcePicker: StateFlow<Boolean> = _showSourcePicker.asStateFlow()

        val nowPlaying: StateFlow<NowPlaying?> = NowPlayingState.current

        private val _effectDescriptors = MutableStateFlow<List<AudioEffectDescriptor>>(emptyList())
        val effectDescriptors: StateFlow<List<AudioEffectDescriptor>> = _effectDescriptors.asStateFlow()

        val engineState: StateFlow<AudioEngineState> = audioEngine.state
        val capabilities = audioEngine.capabilities
        val currentRoute: StateFlow<AudioRoute> = routeRepository.activeRoute
        val diagnosticsEvents = diagnosticsRecorder.events

        private val _activePreset = MutableStateFlow<Preset>(BuiltInPresets.CleanPunch)
        val activePreset: StateFlow<Preset> = _activePreset.asStateFlow()

        // M2: "manuelle Änderung eines Presets automatisch als Custom markieren" -
        // true once a band/macro edit has moved processingSettings away from what
        // freshly selecting activePreset would produce. Cleared by selecting a
        // preset (including re-selecting the current one, i.e. "reset") or by
        // saving the edit as its own preset.
        private val _isDirty = MutableStateFlow(false)
        val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

        private val customPresetsState = MutableStateFlow<List<Preset>>(emptyList())

        // What the preset grid actually renders: built-ins (fixed, compile-time)
        // followed by whatever custom presets are currently saved.
        val allPresets: StateFlow<List<Preset>> =
            customPresetsState
                .map { custom -> BuiltInPresets.all + custom }
                .stateIn(viewModelScope, SharingStarted.Eagerly, BuiltInPresets.all)

        private val _pendingDeletePreset = MutableStateFlow<Preset?>(null)
        val pendingDeletePreset: StateFlow<Preset?> = _pendingDeletePreset.asStateFlow()

        private val _processingSettings =
            MutableStateFlow(ProcessingSettings().withPreset(BuiltInPresets.CleanPunch))
        val processingSettings: StateFlow<ProcessingSettings> = _processingSettings.asStateFlow()

        // Guards persistLiveSettings() against running before restoreSavedState()
        // has had a chance to load what was on disk - without this, the very
        // first capabilities/init-driven applySettings() call would write this
        // ViewModel's temporary just-constructed defaults over the real saved
        // state before it's even been read back.
        private var hasRestoredState = false

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
            viewModelScope.launch {
                restoreSavedState()
            }
        }

        private suspend fun restoreSavedState() {
            val initialCustomPresets = presetRepository.customPresets.first()
            customPresetsState.value = initialCustomPresets

            val saved = appSettingsRepository.liveSettings.first()
            if (saved != null) {
                val preset = findPresetById(saved.activePresetId, initialCustomPresets)
                if (preset != null) {
                    _activePreset.value = preset
                    _isDirty.value = saved.isDirty
                    _processingSettings.value = saved.processingSettings
                    audioEngine.apply(saved.processingSettings)
                }
                // A saved activePresetId that no longer resolves (its custom preset
                // was deleted from another install, say) just keeps this
                // ViewModel's own compiled-in default - not an error, nothing to
                // recover, per M2's "beschädigtes ... kann die App nicht am Start
                // hindern".
            }

            hasRestoredState = true

            // Keep collecting custom-preset changes after this point (deletions,
            // saves) - the one-shot .first() above was only for resolving the
            // restore above without a race against this collector's first emission.
            presetRepository.customPresets
                .drop(1)
                .onEach { customPresetsState.value = it }
                .launchIn(viewModelScope)
        }

        private fun findPresetById(
            id: String,
            customPresets: List<Preset>,
        ): Preset? = BuiltInPresets.all.find { it.id == id } ?: customPresets.find { it.id == id }

        private fun persistLiveSettings() {
            if (!hasRestoredState) return
            viewModelScope.launch {
                appSettingsRepository.save(
                    LiveSettings(
                        activePresetId = _activePreset.value.id,
                        isDirty = _isDirty.value,
                        processingSettings = _processingSettings.value,
                    ),
                )
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
            _isDirty.value = false
            _processingSettings.value = _processingSettings.value.withPreset(preset)
            recalculateBandGains()
            persistLiveSettings()
        }

        // Discards manual edits, re-applying activePreset fresh.
        fun resetToActivePreset() = selectPreset(_activePreset.value)

        fun saveAsNewPreset(name: String) {
            viewModelScope.launch {
                val newPreset = buildPresetFromCurrentState(name)
                presetRepository.save(newPreset)
                _activePreset.value = newPreset
                _isDirty.value = false
                persistLiveSettings()
            }
        }

        fun duplicatePreset(preset: Preset) {
            viewModelScope.launch {
                val copy =
                    preset.copy(
                        id = UUID.randomUUID().toString(),
                        name = "${preset.name} (Kopie)",
                        metadata = preset.metadata.copy(builtIn = false),
                    )
                presetRepository.save(copy)
                selectPreset(copy)
            }
        }

        // Built-ins can't be renamed - the caller (UI) shouldn't offer this action
        // for them, but guard here too since it's cheap and this is a public API.
        fun renamePreset(
            preset: Preset,
            newName: String,
        ) {
            if (preset.metadata.builtIn || newName.isBlank()) return
            viewModelScope.launch {
                val renamed = preset.copy(name = newName)
                presetRepository.save(renamed)
                if (_activePreset.value.id == preset.id) {
                    _activePreset.value = renamed
                    persistLiveSettings()
                }
            }
        }

        fun requestDeletePreset(preset: Preset) {
            if (preset.metadata.builtIn) return
            _pendingDeletePreset.value = preset
        }

        fun cancelDeletePreset() {
            _pendingDeletePreset.value = null
        }

        fun confirmDeletePreset() {
            val preset = _pendingDeletePreset.value ?: return
            _pendingDeletePreset.value = null
            viewModelScope.launch {
                presetRepository.delete(preset.id)
                if (_activePreset.value.id == preset.id) {
                    selectPreset(BuiltInPresets.CleanPunch)
                }
            }
        }

        private fun buildPresetFromCurrentState(name: String): Preset {
            val settings = _processingSettings.value
            val bands = capabilities.value.bands
            // PresetJsonSerializer.importFromJson rejects an empty targetCurve (and a
            // save with one would just silently disappear as "corrupted" the next
            // time it's read back) - bands is only populated once a session has
            // actually attached, so macro edits made before that (the only manual
            // edit possible with no bands to show sliders for) fall back to the
            // active preset's own curve instead of capturing nothing.
            val curve =
                if (bands.isNotEmpty()) {
                    bands.map { band ->
                        TargetPoint(frequencyHz = band.centerFreqHz.toFloat(), gainDb = settings.bandGainsDb[band.index] ?: 0f)
                    }
                } else {
                    _activePreset.value.targetCurve
                }
            return Preset(
                id = UUID.randomUUID().toString(),
                name = name,
                targetCurve = curve,
                macroBassDb = settings.macroBassDb,
                macroPunchDb = settings.macroPunchDb,
                macroHaerteDb = settings.macroHaerteDb,
                requestedHeadroomDb = _activePreset.value.requestedHeadroomDb,
                mbcEnabled = settings.mbcEnabled,
                mbcThresholdDb = settings.mbcThresholdDb,
                mbcRatio = settings.mbcRatio,
                limiter = LimiterConfig(enabled = settings.limiterEnabled, thresholdDb = settings.limiterThresholdDb),
                metadata = PresetMetadata(genre = _activePreset.value.metadata.genre, builtIn = false),
            )
        }

        fun setMasterEnabled(enabled: Boolean) {
            val newSettings = _processingSettings.value.copy(masterEnabled = enabled)
            applySettings(newSettings)

            if (enabled) {
                // Only offer to start the player when nothing is attached yet - if
                // some other app is already playing, flipping the master bar on
                // should just enable processing for that session, not also prompt
                // to launch an unrelated player on top of it. Detached and
                // Listening both mean "no session" (Detached is only the brief
                // cold-start window; the engine spends the rest of its idle time
                // in Listening, see docs/STATE_MACHINE.md).
                val hasNoSession = engineState.value.let { it is AudioEngineState.Detached || it is AudioEngineState.Listening }
                if (hasNoSession) {
                    _showSourcePicker.value = true
                }
            } else {
                _showSourcePicker.value = false
                playerBridge.stopPlayer()
            }
        }

        fun choosePlayerSource(source: PlayerSource) {
            _showSourcePicker.value = false
            playerBridge.launchPlayer(source)
        }

        fun dismissSourcePicker() {
            _showSourcePicker.value = false
        }

        // Backs the "Erneut versuchen" button shown when engineState is Error
        // (roadmap-2026.md M1: "Fehler: konkrete nächste Handlung anbieten").
        fun retryAttach() {
            viewModelScope.launch {
                audioEngine.retry()
            }
        }

        // Lets the user pick/switch a source directly, any time - the master switch's
        // own picker only fires on enabling it with nothing attached (setMasterEnabled
        // above), which meant switching sources while something was already playing
        // needed disabling the master bar and re-enabling it just to see the dialog
        // again.
        fun openSourcePicker() {
            _showSourcePicker.value = true
        }

        fun toggleNowPlayingPlayback() {
            playerBridge.togglePlayback()
        }

        fun reopenPlayer() {
            playerBridge.launchPlayer(PlayerSource.SOUNDCLOUD)
        }

        fun setBypass(bypass: Boolean) {
            val newSettings = _processingSettings.value.copy(bypass = bypass)
            applySettings(newSettings)
        }

        fun setMacroBass(gainDb: Float) {
            val newSettings = _processingSettings.value.copy(macroBassDb = gainDb)
            _processingSettings.value = newSettings
            _isDirty.value = true
            recalculateBandGains()
        }

        fun setMacroPunch(gainDb: Float) {
            val newSettings = _processingSettings.value.copy(macroPunchDb = gainDb)
            _processingSettings.value = newSettings
            _isDirty.value = true
            recalculateBandGains()
        }

        fun setMacroHaerte(gainDb: Float) {
            val newSettings = _processingSettings.value.copy(macroHaerteDb = gainDb)
            _processingSettings.value = newSettings
            _isDirty.value = true
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
            _isDirty.value = true
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
                inputGainDb = safetyScaledInputGainDb(preset.requestedHeadroomDb),
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
            return safetyScaledInputGainDb(peakBoostDb)
        }

        // Guard against returning -0.0f: boxed Float.equals() (used by assertEquals in
        // tests, and by anything else comparing boxed Floats) treats -0.0f and 0.0f as
        // unequal even though == says they're the same.
        private fun safetyScaledInputGainDb(boostDb: Float): Float {
            if (boostDb == 0f) return 0f
            return -(boostDb * INPUT_GAIN_SAFETY_RATIO)
        }

        private fun applySettings(settings: ProcessingSettings) {
            _processingSettings.value = settings
            viewModelScope.launch {
                audioEngine.apply(settings)
            }
            persistLiveSettings()
        }
    }
