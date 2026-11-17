package com.auracurve.app.ui.main

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auracurve.app.R
import com.auracurve.app.audio.AudioDeviceType
import com.auracurve.app.audio.AudioEffectDescriptor
import com.auracurve.app.audio.AudioEffectRepository
import com.auracurve.app.audio.AudioEngine
import com.auracurve.app.audio.AudioEngineState
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.AudioRouteRepository
import com.auracurve.app.audio.AudioSession
import com.auracurve.app.audio.AudioSessionRepository
import com.auracurve.app.audio.DeviceKind
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.audio.SystemVolumeRepository
import com.auracurve.app.auto.CarConnectionRepository
import com.auracurve.app.autoeq.AutoEqCatalogEntry
import com.auracurve.app.autoeq.AutoEqCatalogMatcher
import com.auracurve.app.autoeq.AutoEqParser
import com.auracurve.app.context.SoundContext
import com.auracurve.app.correction.CorrectionProfile
import com.auracurve.app.correction.CorrectionProfileRepository
import com.auracurve.app.data.correction.CorrectionProfileJsonSerializer
import com.auracurve.app.data.preset.PresetShareCodec
import com.auracurve.app.di.DefaultDispatcher
import com.auracurve.app.diagnostics.DiagnosticsRecorder
import com.auracurve.app.diagnostics.NoSystemDiagnosticsSource
import com.auracurve.app.diagnostics.OutputAudioInfo
import com.auracurve.app.diagnostics.ProcessExitRecord
import com.auracurve.app.diagnostics.SystemDiagnosticsSource
import com.auracurve.app.dsp.HeadphonePower
import com.auracurve.app.dsp.HeadroomCalculator
import com.auracurve.app.dsp.ParametricBand
import com.auracurve.app.eq.EqController
import com.auracurve.app.integration.PlayerBridge
import com.auracurve.app.integration.PlayerSource
import com.auracurve.app.preset.GenrePreset
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.PresetIntensity
import com.auracurve.app.preset.PresetRepository
import com.auracurve.app.preset.SoundGoal
import com.auracurve.app.profile.AppProfileRepository
import com.auracurve.app.profile.DeviceAliasStore
import com.auracurve.app.profile.DeviceProfileRepository
import com.auracurve.app.profile.InMemoryDeviceAliasStore
import com.auracurve.app.profile.NoAppProfileRepository
import com.auracurve.app.quick.QuickCommand
import com.auracurve.app.quick.QuickControls
import com.auracurve.app.settings.AppSettingsRepository
import com.auracurve.app.text.TextProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val FEEDBACK_BUFFER = 8

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

// E-008 stage 2: the comparison types moved to the eq package with the logic that uses them.
typealias CompareSide = com.auracurve.app.eq.CompareSide
typealias CompareState = com.auracurve.app.eq.CompareState

data class SystemDiagnosticsSnapshot(
    val processExits: List<ProcessExitRecord> = emptyList(),
    val outputAudio: OutputAudioInfo? = null,
    val extendedAppDetection: Boolean? = null,
)

/**
 * The equalizer screen's view model. Since E-008 stage 2 the equalizer's state and control logic
 * live in the process-wide [EqController]; this class only forwards to it (its public API is
 * unchanged, so the screens did not change) and keeps what belongs to the screen: dialogs,
 * previews, suggestions, the source picker and short confirmations.
 */
@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val controller: EqController,
        private val repository: AudioEffectRepository,
        private val audioEngine: AudioEngine,
        private val routeRepository: AudioRouteRepository,
        private val diagnosticsRecorder: DiagnosticsRecorder,
        private val playerBridge: PlayerBridge,
        private val correctionProfileRepository: CorrectionProfileRepository,
        private val deviceProfileRepository: DeviceProfileRepository,
        private val texts: TextProvider,
        private val deviceAliases: DeviceAliasStore,
        private val sessionRepository: AudioSessionRepository,
        private val carConnectionRepository: CarConnectionRepository,
        private val systemDiagnostics: SystemDiagnosticsSource,
        @DefaultDispatcher private val backgroundDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        /**
         * The constructor the tests have always used: builds a private [EqController] on the main
         * thread (as viewModelScope did) from the same dependencies, so the view model behaves as
         * before E-008 stage 2 without any test changing.
         */
        constructor(
            repository: AudioEffectRepository,
            audioEngine: AudioEngine,
            routeRepository: AudioRouteRepository,
            volumeRepository: SystemVolumeRepository,
            diagnosticsRecorder: DiagnosticsRecorder,
            playerBridge: PlayerBridge,
            presetRepository: PresetRepository,
            appSettingsRepository: AppSettingsRepository,
            correctionProfileRepository: CorrectionProfileRepository,
            deviceProfileRepository: DeviceProfileRepository,
            texts: TextProvider,
            deviceAliases: DeviceAliasStore = InMemoryDeviceAliasStore(),
            sessionRepository: AudioSessionRepository = NoAudioSessionRepository,
            carConnectionRepository: CarConnectionRepository = NoCarConnectionRepository,
            systemDiagnostics: SystemDiagnosticsSource = NoSystemDiagnosticsSource,
            quickControls: QuickControls = QuickControls(),
            appProfileRepository: AppProfileRepository = NoAppProfileRepository,
            backgroundDispatcher: CoroutineDispatcher,
        ) : this(
            controller =
                EqController(
                    audioEngine = audioEngine,
                    routeRepository = routeRepository,
                    volumeRepository = volumeRepository,
                    playerBridge = playerBridge,
                    presetRepository = presetRepository,
                    appSettingsRepository = appSettingsRepository,
                    correctionProfileRepository = correctionProfileRepository,
                    deviceProfileRepository = deviceProfileRepository,
                    sessionRepository = sessionRepository,
                    carConnectionRepository = carConnectionRepository,
                    quickControls = quickControls,
                    appProfileRepository = appProfileRepository,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                ),
            repository = repository,
            audioEngine = audioEngine,
            routeRepository = routeRepository,
            diagnosticsRecorder = diagnosticsRecorder,
            playerBridge = playerBridge,
            correctionProfileRepository = correctionProfileRepository,
            deviceProfileRepository = deviceProfileRepository,
            texts = texts,
            deviceAliases = deviceAliases,
            sessionRepository = sessionRepository,
            carConnectionRepository = carConnectionRepository,
            systemDiagnostics = systemDiagnostics,
            backgroundDispatcher = backgroundDispatcher,
        )

        private val _showDebugEffects = MutableStateFlow(false)
        val showDebugEffects: StateFlow<Boolean> = _showDebugEffects.asStateFlow()

        private val _showSourcePicker = MutableStateFlow(false)
        val showSourcePicker: StateFlow<Boolean> = _showSourcePicker.asStateFlow()

        private val _effectDescriptors = MutableStateFlow<List<AudioEffectDescriptor>>(emptyList())
        val effectDescriptors: StateFlow<List<AudioEffectDescriptor>> = _effectDescriptors.asStateFlow()

        val engineState: StateFlow<AudioEngineState> = audioEngine.state

        // Every app currently holding an audio effect session, and the one the EQ is bound to.
        val audioSessions: StateFlow<List<AudioSession>> = sessionRepository.sessions
        val activeAudioSession: StateFlow<AudioSession?> = sessionRepository.activeSession

        val isOwnPlayerSession: StateFlow<Boolean> = controller.isOwnPlayerSession

        // Android Auto / Automotive OS is running - a safe "car" signal that does not depend on
        // the Bluetooth device name (and also holds when the audio goes over USB).
        val androidAutoConnected: StateFlow<Boolean> = carConnectionRepository.carConnected

        fun selectAudioSession(session: AudioSession) {
            sessionRepository.setActiveSession(session)
        }

        val appProfileBound: StateFlow<Boolean> = controller.appProfileBound

        fun setAppProfileBound(bound: Boolean) = controller.setAppProfileBound(bound)

        val capabilities = audioEngine.capabilities
        val currentRoute: StateFlow<AudioRoute> = routeRepository.activeRoute

        // The route as shown to the user: the device's own name unless the user gave it one.
        val displayRoute: StateFlow<AudioRoute> =
            combine(currentRoute, deviceAliases.aliases) { route, aliases ->
                aliases[route.id]?.let { route.copy(name = it) } ?: route
            }.stateIn(viewModelScope, SharingStarted.Eagerly, routeRepository.activeRoute.value)

        // Blank resets to the device's own name.
        fun renameCurrentRoute(name: String) {
            deviceAliases.setAlias(currentRoute.value.id, name)
        }

        val diagnosticsEvents = diagnosticsRecorder.events

        // Read when the diagnostics screen opens (roadmap 06, P6-4/P5-8): platform calls, so off the main thread.
        suspend fun loadSystemDiagnostics(): SystemDiagnosticsSnapshot =
            withContext(backgroundDispatcher) {
                SystemDiagnosticsSnapshot(
                    systemDiagnostics.recentProcessExits(),
                    systemDiagnostics.outputAudioInfo(),
                    systemDiagnostics.extendedAppDetectionGranted(),
                )
            }

        val currentLevelDb: StateFlow<Float> = controller.currentLevelDb
        val effectiveHeadphoneAcoustics: StateFlow<Boolean> = controller.effectiveHeadphoneAcoustics
        val activePreset: StateFlow<Preset> = controller.activePreset
        val isDirty: StateFlow<Boolean> = controller.isDirty
        val activeContext: StateFlow<SoundContext?> = controller.activeContext
        val deviceKindPrompt: StateFlow<AudioRoute?> = controller.deviceKindPrompt
        val allPresets: StateFlow<List<Preset>> = controller.allPresets
        val customPresetsOnly: StateFlow<List<Preset>> = controller.customPresetsOnly
        val selectedGenreIntensity: StateFlow<Pair<GenrePreset, PresetIntensity>?> = controller.selectedGenreIntensity
        val activeCorrectionProfile: StateFlow<CorrectionProfile> = controller.activeCorrectionProfile
        val allCorrectionProfiles: StateFlow<List<CorrectionProfile>> = controller.allCorrectionProfiles
        val processingSettings: StateFlow<ProcessingSettings> = controller.processingSettings
        val compare: StateFlow<CompareState?> = controller.compare
        val activeGoal: StateFlow<SoundGoal> = controller.activeGoal
        val headphonePower: StateFlow<HeadphonePower> = controller.headphonePower
        val parametricBands: StateFlow<List<ParametricBand>> = controller.parametricBands

        private val _pendingDeletePreset = MutableStateFlow<Preset?>(null)
        val pendingDeletePreset: StateFlow<Preset?> = _pendingDeletePreset.asStateFlow()

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

        // Short confirmations of preset actions ("saved", "duplicated", ...), shown by the screen as a
        // message; nothing is stored. Dropped when nobody is listening.
        private val _feedback = MutableSharedFlow<String>(extraBufferCapacity = FEEDBACK_BUFFER)
        val feedback: SharedFlow<String> = _feedback.asSharedFlow()

        init {
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
        }

        fun setSoundGoal(goal: SoundGoal) = controller.setSoundGoal(goal)

        fun setHeadphonePower(power: HeadphonePower) = controller.setHeadphonePower(power)

        fun answerDeviceKind(kind: DeviceKind) = controller.answerDeviceKind(kind)

        fun dismissDeviceKindPrompt() = controller.dismissDeviceKindPrompt()

        /** The user's own choice of listening-context mode (null = off), remembered for the current route. */
        fun setContextMode(context: SoundContext?) = controller.setContextMode(context)

        fun setHeadphoneAcousticsOverride(override: Boolean) = controller.setHeadphoneAcousticsOverride(override)

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

        fun selectPreset(preset: Preset) = controller.selectPreset(preset)

        fun selectGenreIntensity(
            genre: GenrePreset,
            intensity: PresetIntensity,
        ) = controller.selectGenreIntensity(genre, intensity)

        fun selectCorrectionProfile(profile: CorrectionProfile) = controller.selectCorrectionProfile(profile)

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
        // gets saved - it needs to exist in CorrectionProfileRepository, not just be
        // selected in memory, or the device-profile binding written next would point
        // at an id that can never be resolved again later.
        fun acceptSuggestedCorrectionProfile() {
            val entry = _suggestedCorrectionProfile.value ?: return
            _suggestedCorrectionProfile.value = null
            val profile = entry.profile.copy(deviceName = entry.displayName)
            controller.saveCorrectionProfile(profile)
            selectCorrectionProfile(profile)
            feedback(R.string.feedback_profile_applied, profile.name)
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
                    _importError.value = e.message ?: texts.get(R.string.import_failed_unknown)
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
            controller.saveCorrectionProfile(preview.profile)
            selectCorrectionProfile(preview.profile)
            feedback(R.string.feedback_profile_imported, preview.profile.name)
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

        // Discards manual edits, re-applying activePreset fresh.
        fun resetToActivePreset() {
            selectPreset(activePreset.value)
            feedback(R.string.feedback_reset, activePreset.value.name)
        }

        private fun feedback(
            @StringRes id: Int,
            vararg args: Any,
        ) {
            _feedback.tryEmit(texts.get(id, *args))
        }

        fun saveAsNewPreset(name: String) {
            viewModelScope.launch {
                val newPreset = controller.saveAsNewPreset(name)
                feedback(R.string.feedback_saved, newPreset.name)
            }
        }

        // P5-4: the link a preset is shared as (also what the QR code encodes).
        fun presetShareLink(preset: Preset): String = PresetShareCodec.encode(preset)

        // P5-4: a pasted or scanned link becomes an ordinary, editable user preset and is selected -
        // the same as a duplicate, so it can be renamed or deleted like any other.
        fun importPresetLink(text: String) {
            if (text.isBlank()) {
                feedback(R.string.feedback_preset_link_empty)
                return
            }
            if (PresetShareCodec.decode(text).isFailure) {
                feedback(R.string.feedback_preset_link_invalid)
                return
            }
            viewModelScope.launch {
                controller.importPresetLink(text).onSuccess { feedback(R.string.feedback_preset_imported, it.name) }
            }
        }

        fun duplicatePreset(preset: Preset) {
            viewModelScope.launch {
                val copy = controller.duplicatePreset(preset, texts.get(R.string.preset_copy_name, preset.name))
                feedback(R.string.feedback_duplicated, copy.name)
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
                if (controller.renamePreset(preset, newName)) feedback(R.string.feedback_renamed, newName)
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
                controller.deletePreset(preset)
                feedback(R.string.feedback_deleted, preset.name)
            }
        }

        // From the quick-settings tiles, the widget, or an intent that opened the app for one. With the
        // screen open, switching on may offer the source picker (setMasterEnabled below).
        fun handleQuickCommand(command: QuickCommand) {
            when (command) {
                QuickCommand.TOGGLE_MASTER -> setMasterEnabled(!processingSettings.value.masterEnabled)
                QuickCommand.NEXT_PRESET -> controller.handleQuickCommand(command)
            }
        }

        // For a command that opened the app: the saved state has to be loaded before it is acted on.
        fun queueQuickCommand(command: QuickCommand) {
            viewModelScope.launch {
                controller.awaitRestored()
                handleQuickCommand(command)
            }
        }

        fun setMasterEnabled(enabled: Boolean) {
            controller.setMasterEnabled(enabled)

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
            }
        }

        // Last player the user started from here - reported in the diagnostics report.
        private val _lastPlayerSource = MutableStateFlow<PlayerSource?>(null)
        val lastPlayerSource: StateFlow<PlayerSource?> = _lastPlayerSource

        fun choosePlayerSource(source: PlayerSource) {
            _showSourcePicker.value = false
            _lastPlayerSource.value = source
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

        fun reopenPlayer() {
            _lastPlayerSource.value = PlayerSource.SOUNDCLOUD
            playerBridge.launchPlayer(PlayerSource.SOUNDCLOUD)
        }

        fun setBypass(bypass: Boolean) = controller.setBypass(bypass)

        fun setMacroBass(gainDb: Float) = controller.setMacroBass(gainDb)

        fun setMacroPunch(gainDb: Float) = controller.setMacroPunch(gainDb)

        fun setMacroHaerte(gainDb: Float) = controller.setMacroHaerte(gainDb)

        fun setBandGain(
            bandIndex: Int,
            gainDb: Float,
        ) = controller.setBandGain(bandIndex, gainDb)

        fun addParametricBand() = controller.addParametricBand()

        fun updateParametricBand(
            index: Int,
            band: ParametricBand,
        ) = controller.updateParametricBand(index, band)

        fun removeParametricBand(index: Int) = controller.removeParametricBand(index)

        /** Switch between the classic 5-band equalizer and the DynamicsProcessing pre-EQ (experimental). */
        fun setHighResolutionEq(enabled: Boolean) = controller.setHighResolutionEq(enabled)

        fun setPreEqResponseMode(mode: com.auracurve.app.dsp.PreEqResponseMode) = controller.setPreEqResponseMode(mode)

        fun startCompare() = controller.startCompare()

        fun setCompareSide(side: CompareSide) = controller.setCompareSide(side)

        fun endCompare() = controller.endCompare()

        fun setLoudnessCompensationEnabled(enabled: Boolean) = controller.setLoudnessCompensationEnabled(enabled)

        fun setSubsonicFilterEnabled(enabled: Boolean) = controller.setSubsonicFilterEnabled(enabled)

        fun setVirtualBassEnabled(enabled: Boolean) = controller.setVirtualBassEnabled(enabled)
    }

private object NoCarConnectionRepository : CarConnectionRepository {
    override val carConnected: StateFlow<Boolean> = MutableStateFlow(false)
}

private object NoAudioSessionRepository : AudioSessionRepository {
    override val sessions: StateFlow<List<AudioSession>> = MutableStateFlow(emptyList())
    override val activeSession: StateFlow<AudioSession?> = MutableStateFlow(null)

    override fun startListening() = Unit

    override fun stopListening() = Unit

    override fun setActiveSession(session: AudioSession?) = Unit
}
