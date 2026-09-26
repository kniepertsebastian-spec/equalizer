package com.hardbasseq.eq.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardbasseq.eq.audio.AudioDeviceType
import com.hardbasseq.eq.audio.AudioEffectDescriptor
import com.hardbasseq.eq.audio.AudioEffectRepository
import com.hardbasseq.eq.audio.AudioEngine
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.audio.AudioRouteRepository
import com.hardbasseq.eq.audio.EqualizerBandCapabilities
import com.hardbasseq.eq.audio.ProcessingSettings
import com.hardbasseq.eq.audio.defaultHeadphoneAcoustics
import com.hardbasseq.eq.autoeq.AutoEqCatalogEntry
import com.hardbasseq.eq.autoeq.AutoEqCatalogMatcher
import com.hardbasseq.eq.autoeq.AutoEqParser
import com.hardbasseq.eq.correction.BuiltInCorrectionProfiles
import com.hardbasseq.eq.correction.CorrectionProfile
import com.hardbasseq.eq.correction.CorrectionProfileRepository
import com.hardbasseq.eq.data.correction.CorrectionProfileJsonSerializer
import com.hardbasseq.eq.di.DefaultDispatcher
import com.hardbasseq.eq.diagnostics.DiagnosticsRecorder
import com.hardbasseq.eq.dsp.CurveComposer
import com.hardbasseq.eq.dsp.EqualizerInterpolator
import com.hardbasseq.eq.dsp.HeadphoneComfortCurve
import com.hardbasseq.eq.dsp.HeadroomCalculator
import com.hardbasseq.eq.integration.PlayerBridge
import com.hardbasseq.eq.integration.PlayerSource
import com.hardbasseq.eq.preset.BuiltInPresets
import com.hardbasseq.eq.preset.LimiterConfig
import com.hardbasseq.eq.preset.PortableSoundProfile
import com.hardbasseq.eq.preset.PortableSoundProfileJson
import com.hardbasseq.eq.preset.Preset
import com.hardbasseq.eq.preset.PresetMetadata
import com.hardbasseq.eq.preset.PresetRepository
import com.hardbasseq.eq.preset.TargetPoint
import com.hardbasseq.eq.profile.DeviceProfileRepository
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
import kotlinx.coroutines.flow.combine
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
// Limiter is still unchanged and remains the actual clipping safety net. Lowered again
// to 0.2 in Session 24, alongside a matching loosening of the MBC makeup gain
// (AndroidAudioEngine.applyInternal), at the user's request to give back more of the
// punch these safety layers were taking out on kick-heavy material - same reasoning as
// Session 17, the Limiter downstream is still the actual, unchanged clipping backstop.
private const val INPUT_GAIN_SAFETY_RATIO = 0.2f

// roadmap-2026.md M5: "Extreme Boosts werden nicht still angewandt, sondern
// begrenzt oder bestätigt" - an imported correction curve peaking above this
// needs an explicit confirmation in the import preview, not just a silent clamp
// (AutoEqParser/CorrectionProfileJsonSerializer already hard-clamp to ±24 dB;
// this is a much lower, "are you sure" style threshold on top of that).
private const val EXTREME_BOOST_WARNING_THRESHOLD_DB = 12f

// M5 "Importvorschau mit Quelle, Frequenzbereich, maximalem Boost und benötigtem
// Headroom anzeigen" - shown to the user before anything is written to
// CorrectionProfileRepository.
data class CorrectionProfileImportPreview(
    val profile: CorrectionProfile,
    val minFreqHz: Float,
    val maxFreqHz: Float,
    val maxBoostDb: Float,
    val requiredHeadroomDb: Float,
    val isExtremeBoost: Boolean,
)

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
        private val correctionProfileRepository: CorrectionProfileRepository,
        private val deviceProfileRepository: DeviceProfileRepository,
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

        // Chat feature (item 1 of "setz alle Punkte um", not a roadmap-2026.md
        // milestone): null until applyDeviceProfileForRoute() loads whatever the
        // current route's DeviceProfileEntity has saved (or confirms there's none
        // yet) - see effectiveHeadphoneAcoustics for how null resolves to a
        // per-route-type default instead of a hardcoded one.
        private val headphoneAcousticsOverrideState = MutableStateFlow<Boolean?>(null)

        val effectiveHeadphoneAcoustics: StateFlow<Boolean> =
            combine(currentRoute, headphoneAcousticsOverrideState) { route, override ->
                override ?: route.type.defaultHeadphoneAcoustics()
            }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                routeRepository.activeRoute.value.type
                    .defaultHeadphoneAcoustics(),
            )

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

        // M3 "Mein Kopfhörer": the headphone/speaker correction curve, combined with
        // activePreset ("Klangstil") via CurveComposer before mapping to hardware
        // bands - see recalculateBandGains().
        private val _activeCorrectionProfile = MutableStateFlow(BuiltInCorrectionProfiles.None)
        val activeCorrectionProfile: StateFlow<CorrectionProfile> = _activeCorrectionProfile.asStateFlow()

        private val customCorrectionProfilesState = MutableStateFlow<List<CorrectionProfile>>(emptyList())

        val allCorrectionProfiles: StateFlow<List<CorrectionProfile>> =
            customCorrectionProfilesState
                .map { custom -> BuiltInCorrectionProfiles.all + custom }
                .stateIn(viewModelScope, SharingStarted.Eagerly, BuiltInCorrectionProfiles.all)

        // "Mein Kopfhörer" auto-detection (chat feature, not a roadmap-2026.md
        // milestone): a suggestion only, never a silent auto-import - see
        // AutoEqCatalogMatcher's own doc comment for why. Cleared whenever the
        // route already has an explicit DeviceProfileRepository binding (the user
        // already chose something for it, suggested or not) or was dismissed for
        // this route already - dismissedSuggestionRouteIds is intentionally only
        // in-memory (this ViewModel's lifetime), not persisted: a fresh app start
        // asking again after a dismissal is an acceptable tradeoff against the
        // complexity of persisting a third kind of per-route state.
        private val _suggestedCorrectionProfile = MutableStateFlow<AutoEqCatalogEntry?>(null)
        val suggestedCorrectionProfile: StateFlow<AutoEqCatalogEntry?> = _suggestedCorrectionProfile.asStateFlow()

        private val dismissedSuggestionRouteIds = mutableSetOf<String>()

        // M5 "Importvorschau ... anzeigen" / "Extreme Boosts werden nicht still
        // angewandt, sondern begrenzt oder bestätigt": set by
        // previewCorrectionProfileImport(), cleared by confirm/cancel. Nothing is
        // saved to CorrectionProfileRepository until the user actually confirms.
        private val _pendingImportPreview = MutableStateFlow<CorrectionProfileImportPreview?>(null)
        val pendingImportPreview: StateFlow<CorrectionProfileImportPreview?> = _pendingImportPreview.asStateFlow()

        private val _importError = MutableStateFlow<String?>(null)
        val importError: StateFlow<String?> = _importError.asStateFlow()

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
            // M3 "Profilwechsel bei Bluetooth-, USB- und Lautsprecherwechsel": the
            // starting route is handled inside restoreSavedState() itself (it may
            // still be resolving when this collector's first emission fires); this
            // only reacts to routes that change *after* that, e.g. a Bluetooth
            // headset connecting/disconnecting during the session. AudioRoute is a
            // data class and activeRoute is a StateFlow, so equal consecutive
            // routes (including a rapid double-emission of the same route) are
            // already deduplicated before this collector ever sees them.
            viewModelScope.launch {
                routeRepository.activeRoute
                    .drop(1)
                    .collect { route ->
                        if (hasRestoredState) applyDeviceProfileForRoute(route)
                    }
            }
            // Independent of the restore race above: a suggestion is advisory,
            // read-only UI state, not something that needs to wait for
            // restoreSavedState() - it never mutates activePreset/
            // activeCorrectionProfile itself (acceptSuggestedCorrectionProfile()
            // does, but only once the user actually taps it).
            viewModelScope.launch {
                routeRepository.activeRoute.collect { route ->
                    updateSuggestedCorrectionProfile(route)
                }
            }
            // Makes the "Kopfhörer-Modus" switch (and automatic route-based
            // defaulting) actually change the applied EQ curve - combinedCurve()
            // already reads effectiveHeadphoneAcoustics.value itself, but nothing
            // re-triggers recalculateBandGains() when *only* this changes (e.g. a
            // route switch that changes the default with no other route-profile
            // fields differing) without this collector.
            viewModelScope.launch {
                effectiveHeadphoneAcoustics.collect {
                    recalculateBandGains()
                }
            }
        }

        private suspend fun restoreSavedState() {
            val initialCustomPresets = presetRepository.customPresets.first()
            customPresetsState.value = initialCustomPresets

            val initialCorrectionProfiles = correctionProfileRepository.customProfiles.first()
            customCorrectionProfilesState.value = initialCorrectionProfiles

            val saved = appSettingsRepository.liveSettings.first()
            if (saved != null) {
                val preset = findPresetById(saved.activePresetId, initialCustomPresets)
                if (preset != null) {
                    _activePreset.value = preset
                    _isDirty.value = saved.isDirty
                    _processingSettings.value = saved.processingSettings
                    audioEngine.apply(saved.processingSettings)
                }
                _activeCorrectionProfile.value =
                    findCorrectionProfileById(saved.activeCorrectionProfileId, initialCorrectionProfiles)
                // A saved activePresetId/activeCorrectionProfileId that no longer
                // resolves (its custom entry was deleted from another install, say)
                // just keeps this ViewModel's own compiled-in default - not an
                // error, nothing to recover, per M2's "beschädigtes ... kann die
                // App nicht am Start hindern".
            }

            hasRestoredState = true

            // Route-specific binding takes precedence over the plain "last active"
            // LiveSettings restored above, if this route has one - that's the
            // actual point of M3 (same taste, different headphones).
            applyDeviceProfileForRoute(routeRepository.activeRoute.value)

            // Keep collecting custom-preset/-correction-profile changes after this
            // point (deletions, saves) - the one-shot .first() calls above were
            // only for resolving the restore above without a race against these
            // collectors' first emission.
            presetRepository.customPresets
                .drop(1)
                .onEach { customPresetsState.value = it }
                .launchIn(viewModelScope)
            correctionProfileRepository.customProfiles
                .drop(1)
                .onEach { customCorrectionProfilesState.value = it }
                .launchIn(viewModelScope)
        }

        private fun findPresetById(
            id: String,
            customPresets: List<Preset>,
        ): Preset? = BuiltInPresets.all.find { it.id == id } ?: customPresets.find { it.id == id }

        private fun findCorrectionProfileById(
            id: String,
            customProfiles: List<CorrectionProfile>,
        ): CorrectionProfile =
            BuiltInCorrectionProfiles.all.find { it.id == id }
                ?: customProfiles.find { it.id == id }
                ?: BuiltInCorrectionProfiles.None

        // Looks up whether `route` has its own saved voicing/correction binding
        // (DeviceProfileRepository) and, if so, switches to it - the M3 conflict
        // rule ("manuelle Auswahl gilt bis zum nächsten Route-Wechsel") means this
        // is the *only* place a route is allowed to change the active
        // preset/correction; selectPreset()/selectCorrectionProfile() never do.
        private suspend fun applyDeviceProfileForRoute(route: AudioRoute) {
            val binding = deviceProfileRepository.getProfileForRoute(route.id)
            // Independent of whether a full binding exists below - a route with no
            // saved profile at all still needs its override reset to null (no
            // explicit choice made for it), not left holding the previous route's
            // value.
            headphoneAcousticsOverrideState.value = binding?.headphoneAcousticsOverride
            if (binding == null) return
            val preset = findPresetById(binding.boundPresetId, customPresetsState.value) ?: return
            val correction = findCorrectionProfileById(binding.boundCorrectionProfileId, customCorrectionProfilesState.value)

            _activePreset.value = preset
            _activeCorrectionProfile.value = correction
            _isDirty.value = false
            _processingSettings.value = _processingSettings.value.withPreset(preset)
            recalculateBandGains()
            persistLiveSettings()
        }

        // Persists which voicing/correction are active while `route` (the current
        // route unless stated otherwise) is the active one - called from every
        // user-initiated preset/correction selection, never from
        // applyDeviceProfileForRoute() above (that would just save back the exact
        // binding it just read).
        private fun saveDeviceProfileBinding() {
            val route = currentRoute.value
            val headphoneAcousticsOverride = headphoneAcousticsOverrideState.value
            viewModelScope.launch {
                deviceProfileRepository.saveProfile(
                    routeId = route.id,
                    routeType = route.type.name,
                    displayName = route.name,
                    boundPresetId = _activePreset.value.id,
                    boundCorrectionProfileId = _activeCorrectionProfile.value.id,
                    headphoneAcousticsOverride = headphoneAcousticsOverride,
                )
            }
        }

        // Chat feature (item 1): explicit per-route override, e.g. for a USB DAC
        // feeding studio monitors (defaultHeadphoneAcoustics() guesses "true" for
        // any USB route, which is wrong there). Goes through the same
        // saveDeviceProfileBinding() write path as preset/correction changes so
        // the three stay merged into one row instead of racing each other.
        fun setHeadphoneAcousticsOverride(override: Boolean) {
            headphoneAcousticsOverrideState.value = override
            saveDeviceProfileBinding()
        }

        private fun persistLiveSettings() {
            if (!hasRestoredState) return
            viewModelScope.launch {
                appSettingsRepository.save(
                    LiveSettings(
                        activePresetId = _activePreset.value.id,
                        isDirty = _isDirty.value,
                        processingSettings = _processingSettings.value,
                        activeCorrectionProfileId = _activeCorrectionProfile.value.id,
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
            saveDeviceProfileBinding()
        }

        // M3 "Mein Kopfhörer": switches the active correction curve, independent of
        // the voicing (selectPreset above) it gets combined with.
        fun selectCorrectionProfile(profile: CorrectionProfile) {
            _activeCorrectionProfile.value = profile
            recalculateBandGains()
            persistLiveSettings()
            saveDeviceProfileBinding()
        }

        // Only WIRED_HEADPHONES/SPEAKER routes always carry a fixed, generic name
        // (see AndroidAudioRouteRepository.detectCurrentRoute()) - matching those
        // against the catalog would be meaningless, so only BLUETOOTH/USB routes
        // (which carry the actual device.productName) are ever considered.
        private suspend fun updateSuggestedCorrectionProfile(route: AudioRoute) {
            val eligibleType = route.type == AudioDeviceType.BLUETOOTH || route.type == AudioDeviceType.USB
            val alreadyBound = eligibleType && deviceProfileRepository.getProfileForRoute(route.id) != null
            _suggestedCorrectionProfile.value =
                if (!eligibleType || route.id in dismissedSuggestionRouteIds || alreadyBound) {
                    null
                } else {
                    AutoEqCatalogMatcher.findBestMatch(route.name)
                }
        }

        // Saves the suggested catalog profile the same way an AutoEQ file import
        // gets saved (confirmCorrectionProfileImport above) - it needs to exist in
        // CorrectionProfileRepository, not just be selected in memory, or the
        // device-profile binding saveDeviceProfileBinding() writes next would point
        // at an id findCorrectionProfileById() can never resolve again later.
        fun acceptSuggestedCorrectionProfile() {
            val entry = _suggestedCorrectionProfile.value ?: return
            _suggestedCorrectionProfile.value = null
            viewModelScope.launch {
                correctionProfileRepository.save(entry.profile)
            }
            selectCorrectionProfile(entry.profile)
        }

        fun dismissSuggestedCorrectionProfile() {
            dismissedSuggestionRouteIds.add(currentRoute.value.id)
            _suggestedCorrectionProfile.value = null
        }

        // M5: parses `text` (a file the caller already read, e.g. via a SAF
        // OpenDocument picker) and, on success, populates pendingImportPreview for
        // the UI to show before anything is saved - see confirmCorrectionProfileImport.
        // Malformed/empty input surfaces through importError instead (M5: "Ungültige,
        // doppelte oder extreme Punkte verständlich melden").
        fun previewCorrectionProfileImport(
            sourceName: String,
            text: String,
        ) {
            AutoEqParser.parseAutoEqText(sourceName, text).fold(
                onSuccess = { profile ->
                    val minFreq = profile.curve.minOf { it.frequencyHz }
                    val maxFreq = profile.curve.maxOf { it.frequencyHz }
                    val maxBoostDb = profile.curve.maxOf { it.gainDb }.coerceAtLeast(0f)
                    val headroom = HeadroomCalculator.fromCombinedCurve(profile.curve)
                    _pendingImportPreview.value =
                        CorrectionProfileImportPreview(
                            profile = profile,
                            minFreqHz = minFreq,
                            maxFreqHz = maxFreq,
                            maxBoostDb = maxBoostDb,
                            requiredHeadroomDb = headroom.maxPositiveGainDb,
                            isExtremeBoost = maxBoostDb > EXTREME_BOOST_WARNING_THRESHOLD_DB,
                        )
                    _importError.value = null
                },
                onFailure = { e ->
                    _importError.value = e.message ?: "Import fehlgeschlagen: unbekannter Fehler"
                    _pendingImportPreview.value = null
                },
            )
        }

        // Only actually writes the imported profile once the user has seen the
        // preview and confirmed (also the extreme-boost confirmation, if the UI
        // asked for one first) - selects it immediately after, same as picking any
        // other correction profile.
        fun confirmCorrectionProfileImport() {
            val preview = _pendingImportPreview.value ?: return
            _pendingImportPreview.value = null
            viewModelScope.launch {
                correctionProfileRepository.save(preview.profile)
            }
            selectCorrectionProfile(preview.profile)
        }

        fun cancelCorrectionProfileImport() {
            _pendingImportPreview.value = null
        }

        fun dismissImportError() {
            _importError.value = null
        }

        // M5 "Import, Export und Teilen ... integrieren": the JSON the UI hands to
        // a Share Sheet intent (ACTION_SEND) - the same validated format
        // previewCorrectionProfileImport()/CorrectionProfileRepository already use,
        // so re-importing an exported file round-trips losslessly.
        fun exportCorrectionProfileJson(profile: CorrectionProfile): String = CorrectionProfileJsonSerializer.exportToJson(profile)

        /** Includes the live band levels, so manual edits survive the desktop transfer. */
        fun exportDesktopSoundProfileJson(): String {
            val settings = _processingSettings.value
            val hasActiveSession = engineState.value is AudioEngineState.Active
            val appliedCurve =
                if (hasActiveSession) {
                    capabilities.value.bands.mapNotNull { band ->
                        settings.bandGainsDb[band.index]?.let { gain ->
                            TargetPoint(band.centerFreqHz.toFloat(), gain)
                        }
                    }
                } else {
                    emptyList()
                }
            return PortableSoundProfileJson.export(
                PortableSoundProfile(
                    preset = _activePreset.value,
                    correction = _activeCorrectionProfile.value,
                    appliedEqCurve = appliedCurve,
                    macroBassDb = settings.macroBassDb,
                    macroPunchDb = settings.macroPunchDb,
                    macroHaerteDb = settings.macroHaerteDb,
                    inputGainDb = settings.inputGainDb,
                    mbcEnabled = settings.mbcEnabled && (!hasActiveSession || capabilities.value.hasMbc),
                    mbcThresholdDb = settings.mbcThresholdDb,
                    mbcRatio = settings.mbcRatio,
                    limiter =
                        LimiterConfig(
                            settings.limiterEnabled && (!hasActiveSession || capabilities.value.hasLimiter),
                            settings.limiterThresholdDb,
                        ),
                    processingEnabled = settings.masterEnabled && !settings.bypass,
                ),
            )
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

        // M3 "Zielkurven kombinieren": correction ("Mein Kopfhörer") and voicing
        // ("Klangstil") are combined into one curve before anything else - band
        // mapping, headroom - happens. EqualizerScreen recomputes this same
        // combination from activeCorrectionProfile/activePreset/effective-
        // HeadphoneAcoustics (all already collected there) for its own headroom
        // banner via the same CurveComposer call, rather than this ViewModel
        // exposing the combined curve itself.
        //
        // Chat feature: HeadphoneComfortCurve only gets folded in while
        // effectiveHeadphoneAcoustics is true - this is what makes the
        // "Kopfhörer-Modus" switch (item 1) actually audible through the real,
        // already-working AndroidAudioEngine Equalizer path, since true
        // Crossfeed/Bass-Mono-Summing can't run there (Android's system
        // Equalizer/DynamicsProcessing effects have no such algorithm).
        private fun combinedCurve(): List<TargetPoint> {
            val headphoneCurve = if (effectiveHeadphoneAcoustics.value) HeadphoneComfortCurve.curve else emptyList()
            return CurveComposer.combine(
                listOf(_activeCorrectionProfile.value.curve, _activePreset.value.targetCurve, headphoneCurve),
            )
        }

        private fun recalculateBandGains(bands: List<EqualizerBandCapabilities> = audioEngine.capabilities.value.bands) {
            val curve = combinedCurve()
            val macroBassDb = _processingSettings.value.macroBassDb
            val macroPunchDb = _processingSettings.value.macroPunchDb
            val macroHaerteDb = _processingSettings.value.macroHaerteDb

            val calculatedGains =
                EqualizerInterpolator.interpolateCurveToBands(
                    curve = curve,
                    bands = bands,
                    macroBassDb = macroBassDb,
                    macroPunchDb = macroPunchDb,
                    macroHaerteDb = macroHaerteDb,
                )
            // roadmap-2026.md M4: headroom from the combined *continuous* curve, not
            // just the highest of these discrete post-mapping band gains - see
            // HeadroomCalculator for why that undercounts a peak that falls between
            // two hardware band centers.
            val headroom =
                HeadroomCalculator.fromCombinedCurve(
                    combinedCurve = curve,
                    macroBassDb = macroBassDb,
                    macroPunchDb = macroPunchDb,
                    macroHaerteDb = macroHaerteDb,
                )
            val newSettings =
                _processingSettings.value.copy(
                    bandGainsDb = calculatedGains,
                    inputGainDb = safetyScaledInputGainDb(headroom.maxPositiveGainDb),
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
