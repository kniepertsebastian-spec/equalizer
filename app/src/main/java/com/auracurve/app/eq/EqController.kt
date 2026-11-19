package com.auracurve.app.eq

import com.auracurve.app.BuildConfig
import com.auracurve.app.audio.AudioEngine
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.AudioRouteRepository
import com.auracurve.app.audio.AudioSessionRepository
import com.auracurve.app.audio.DeviceKind
import com.auracurve.app.audio.EqualizerBandCapabilities
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.audio.SystemVolumeRepository
import com.auracurve.app.audio.defaultHeadphoneAcoustics
import com.auracurve.app.auto.CarConnectionRepository
import com.auracurve.app.context.SoundContext
import com.auracurve.app.correction.BuiltInCorrectionProfiles
import com.auracurve.app.correction.CorrectionProfile
import com.auracurve.app.correction.CorrectionProfileRepository
import com.auracurve.app.data.preset.PresetShareCodec
import com.auracurve.app.dsp.EqualizerInterpolator
import com.auracurve.app.dsp.HeadphoneDynamicsEasing
import com.auracurve.app.dsp.HeadphonePower
import com.auracurve.app.dsp.HeadroomCalculator
import com.auracurve.app.dsp.LoudnessMatch
import com.auracurve.app.dsp.ParametricBand
import com.auracurve.app.dsp.ParametricCurve
import com.auracurve.app.integration.PlayerBridge
import com.auracurve.app.preset.BuiltInContextPresets
import com.auracurve.app.preset.BuiltInGenrePresets
import com.auracurve.app.preset.BuiltInPresets
import com.auracurve.app.preset.GenrePreset
import com.auracurve.app.preset.LimiterConfig
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.PresetCycle
import com.auracurve.app.preset.PresetIntensity
import com.auracurve.app.preset.PresetIntensityResolver
import com.auracurve.app.preset.PresetMetadata
import com.auracurve.app.preset.PresetRepository
import com.auracurve.app.preset.SoundGoal
import com.auracurve.app.preset.TargetPoint
import com.auracurve.app.profile.AppProfileRepository
import com.auracurve.app.profile.DeviceProfileRepository
import com.auracurve.app.quick.QuickCommand
import com.auracurve.app.quick.QuickControls
import com.auracurve.app.quick.QuickState
import com.auracurve.app.settings.AppSettingsRepository
import com.auracurve.app.settings.LiveSettings
import com.auracurve.app.ui.main.CONTEXT_MODE_NONE
import com.auracurve.app.ui.main.CurveCalculator
import com.auracurve.app.ui.main.CurveInputs
import com.auracurve.app.ui.main.RouteContextResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.math.abs

// How much of the peak EQ boost the automatic input-gain cut pre-cancels before the
// signal ever reaches dynamics processing. At 1.0 the boost is fully cancelled before
// the Limiter (still a hard, unchanged 10:1-ratio ceiling) gets a chance to do its own
// job, which made every preset sound indistinguishable from flat - see roadmap.md
// Session 16. Lowered further to 0.3 in Session 17 at the user's explicit request for
// more aggressive kicks/less pre-cancellation, after 0.5 still felt too subtle - the
// Limiter is still unchanged and remains the actual clipping safety net. Lowered again
// to 0.2, then to 0.1, both in Session 24 (same sitting - the first cut "soll ja
// knallen"), alongside a matching loosening of the MBC makeup gain
// (AndroidAudioEngine.applyInternal). At 0.1 this pre-cancellation stage is nearly a
// no-op; the Limiter downstream is now doing almost all of the actual safety work and
// is the only thing standing between a hot preset and true clipping - still an
// unchanged, hard 10:1 ceiling, so it always holds the line, but it may need to work
// noticeably harder/more audibly on already-loud presets or devices than before.
// Raised back to 0.5 after a sound review: at 0.1 a +12 dB bass preset left the limiter
// over 10 dB of reduction on bass-heavy tracks, which flattened and distorted exactly the
// kicks it was meant to make hit harder. At 0.5 it works a few dB at most; the punch comes
// from the MBC makeup gain instead.
private const val INPUT_GAIN_SAFETY_RATIO = 0.5f
private const val SPEAKER_INPUT_GAIN_SAFETY_RATIO = 0.8f
private const val FEEDBACK_BUFFER = 8

/** Pixel-Car module 11, F6: the most the speed regulator may add (SpeedVolumeSettings.MAX_BOOST_DB). */
const val MAX_SPEED_GAIN_DB = 6f

// Where a new parametric band is put first: free spots from the mids outwards.
private val PARAMETRIC_START_FREQUENCIES_HZ = listOf(1000f, 100f, 4000f, 250f, 8000f, 60f, 2000f, 500f)

// The player's curve is handed over as points on this log grid (about 6 per octave).
private const val PLAYER_CURVE_MIN_HZ = 20f
private const val PLAYER_CURVE_MAX_HZ = 20_000f
private const val PLAYER_CURVE_STEP = 1.12f

/** The scope the [EqController] runs its work in: process lifetime, main thread (see EqControllerModule). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EqControllerScope

// The Original/EQ comparison: which side is heard and how much louder (+) or quieter (-) the EQ
// side is estimated to be before the match.
enum class CompareSide { ORIGINAL, EQ }

data class CompareState(
    val side: CompareSide,
    val eqLevelDeltaDb: Float,
)

/**
 * E-008 stage 2: the equalizer's control logic, process-wide. It used to live only in
 * MainViewModel, so without an open activity nobody switched presets (quick tile, later the
 * remote server), followed route changes or saved anything. Now MainViewModel is a thin layer on
 * top of this, and the session service starts it (see [awaitRestored]) so it also works with the
 * app closed.
 *
 * Moved here unchanged: the state (preset, correction, context mode, goal, headphone power,
 * parametric bands, comparison), the reactions to routes, sessions, Android Auto, capabilities
 * and volume, the curve pipeline to the engine and the built-in player, and saving. Everything
 * runs in [scope] on the main thread, exactly as it did in scope, so the code moved
 * without any new threading.
 */
@Singleton
class EqController
    @Inject
    constructor(
        private val audioEngine: AudioEngine,
        private val routeRepository: AudioRouteRepository,
        private val volumeRepository: SystemVolumeRepository,
        private val playerBridge: PlayerBridge,
        private val presetRepository: PresetRepository,
        private val appSettingsRepository: AppSettingsRepository,
        private val correctionProfileRepository: CorrectionProfileRepository,
        private val deviceProfileRepository: DeviceProfileRepository,
        private val sessionRepository: AudioSessionRepository,
        private val carConnectionRepository: CarConnectionRepository,
        private val quickControls: QuickControls,
        private val appProfileRepository: AppProfileRepository,
        @param:EqControllerScope private val scope: CoroutineScope,
    ) {
        val capabilities = audioEngine.capabilities
        val currentRoute: StateFlow<AudioRoute> = routeRepository.activeRoute
        val currentLevelDb: StateFlow<Float> = volumeRepository.currentLevelDb

        // False only while the EQ is bound to another app's session: Virtual Bass runs in
        // this app's own player only, so for a foreign session it has no effect there.
        val isOwnPlayerSession: StateFlow<Boolean> =
            sessionRepository.activeSession
                .map { it == null || it.packageName == BuildConfig.APPLICATION_ID }
                .stateIn(scope, SharingStarted.Eagerly, true)

        // P5-5: true while the app the EQ is attached to has its own remembered sound.
        val appProfileBound: StateFlow<Boolean> =
            combine(appProfileRepository.allProfiles, sessionRepository.activeSession) { profiles, session ->
                session != null && profiles.any { it.packageName == session.packageName }
            }.stateIn(scope, SharingStarted.Eagerly, false)

        // "Use this sound for <app>": remembers the current preset and correction for the app the EQ is
        // attached to (and keeps them up to date with every later choice), or forgets them again.
        fun setAppProfileBound(bound: Boolean) {
            val packageName = sessionRepository.activeSession.value?.packageName ?: return
            scope.launch {
                if (bound) {
                    appProfileRepository.saveProfile(packageName, _activePreset.value.id, _activeCorrectionProfile.value.id)
                } else {
                    appProfileRepository.deleteProfile(packageName)
                }
            }
        }

        // An app with a remembered sound gets it as soon as it becomes the active one. The device profile
        // stays in charge for apps without one; on a route change the app's sound wins again afterwards.
        private suspend fun applyAppProfile(packageName: String?) {
            if (packageName == null) return
            val profile = appProfileRepository.getProfile(packageName) ?: return
            val preset = findPresetById(profile.boundPresetId, customPresetsState.value) ?: return
            if (BuiltInContextPresets.contextOf(preset) != null) return
            _activePreset.value = preset
            _activeCorrectionProfile.value =
                findCorrectionProfileById(profile.boundCorrectionProfileId, customCorrectionProfilesState.value)
            _isDirty.value = false
            _processingSettings.value = _processingSettings.value.withPreset(preset)
            recalculateBandGains()
            persistLiveSettings()
        }

        private fun rememberForActiveAppIfBound() {
            val packageName = sessionRepository.activeSession.value?.packageName ?: return
            if (!appProfileBound.value) return
            scope.launch {
                appProfileRepository.saveProfile(packageName, _activePreset.value.id, _activeCorrectionProfile.value.id)
            }
        }

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
                scope,
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

        // Listening-context mode ("Auto" / "Bluetooth-Box"): an overlay on whatever
        // preset is active, not a preset of its own - so picking another genre keeps
        // it on. It adds the context's curve, subsonic filter, volume loudness,
        // virtual bass and (for small speakers) a tighter limiter. null = off.
        private val _activeContext = MutableStateFlow<SoundContext?>(null)
        val activeContext: StateFlow<SoundContext?> = _activeContext.asStateFlow()

        // True when route detection switched the mode on, so it is switched off again
        // when that route goes away; false once the user chose it themselves.
        private var contextSetAutomatically = false

        // A Bluetooth output the name heuristic could not place, asked about once so the
        // user's answer (stored like a manual mode choice) replaces the guesswork.
        private val _deviceKindPrompt = MutableStateFlow<AudioRoute?>(null)
        val deviceKindPrompt: StateFlow<AudioRoute?> = _deviceKindPrompt.asStateFlow()
        private val dismissedDeviceKindRouteIds = mutableSetOf<String>()

        // Saved context choice of the current route: null = none, CONTEXT_MODE_NONE = off.
        private var routeContextMode: String? = null
            set(value) {
                field = value
                _contextFollowsRoute.value = value == null
            }
        private val _contextFollowsRoute = MutableStateFlow(true)

        /** True while the route's detection decides the mode (no saved choice for the current route). */
        val contextFollowsRoute: StateFlow<Boolean> = _contextFollowsRoute.asStateFlow()
        private var pendingRestoredContext: SoundContext? = null

        // What the preset grid actually renders: built-ins (fixed, compile-time)
        // followed by whatever custom presets are currently saved.
        val allPresets: StateFlow<List<Preset>> =
            customPresetsState
                .map { custom -> BuiltInPresets.all + custom }
                .stateIn(scope, SharingStarted.Eagerly, BuiltInPresets.all)

        // Chat feature: genre x intensity preset redesign. The old preset grid
        // (still used for user-duplicated custom presets, see EqualizerScreen) is
        // no longer how the built-in sounds are picked - a genre dropdown plus 5
        // intensity buttons is. customPresetsOnly feeds that grid instead of
        // allPresets now that BuiltInPresets.all also carries every genre x
        // intensity combination (35 of them, plus the original 9 hand-authored
        // presets - far too many for a 3-per-row icon grid).
        val customPresetsOnly: StateFlow<List<Preset>> = customPresetsState.asStateFlow()

        // Mirrors activePreset, parsed back into which genre/intensity button
        // combination it came from (null if the active preset isn't one of
        // those - a legacy built-in or a custom preset). Drives which dropdown
        // entry and which intensity button the new selector shows as active,
        // without persisting that pair anywhere separately from boundPresetId.
        val selectedGenreIntensity: StateFlow<Pair<GenrePreset, PresetIntensity>?> =
            activePreset
                .map { preset -> PresetIntensityResolver.parse(preset.id, BuiltInGenrePresets.all) }
                .stateIn(
                    scope,
                    SharingStarted.Eagerly,
                    PresetIntensityResolver.parse(_activePreset.value.id, BuiltInGenrePresets.all),
                )

        // M3 "Mein Kopfhörer": the headphone/speaker correction curve, combined with
        // activePreset ("Klangstil") via CurveComposer before mapping to hardware
        // bands - see recalculateBandGains().
        private val _activeCorrectionProfile = MutableStateFlow(BuiltInCorrectionProfiles.None)
        val activeCorrectionProfile: StateFlow<CorrectionProfile> = _activeCorrectionProfile.asStateFlow()

        private val customCorrectionProfilesState = MutableStateFlow<List<CorrectionProfile>>(emptyList())

        val allCorrectionProfiles: StateFlow<List<CorrectionProfile>> =
            customCorrectionProfilesState
                .map { custom -> BuiltInCorrectionProfiles.all + custom }
                .stateIn(scope, SharingStarted.Eagerly, BuiltInCorrectionProfiles.all)

        private val _processingSettings =
            MutableStateFlow(ProcessingSettings().withPreset(BuiltInPresets.CleanPunch))
        val processingSettings: StateFlow<ProcessingSettings> = _processingSettings.asStateFlow()

        // Original/EQ comparison with matched loudness: both sides run through the effect, the louder
        // one turned down by the estimated difference. Ends with endCompare(); nothing is saved.
        private val _compare = MutableStateFlow<CompareState?>(null)

        // Pixel-Car module 11, F6: extra level at speed, set by the remote server's regulator.
        // Live only, never saved; 0 leaves the chain exactly as it was.
        private val _speedGainDb = MutableStateFlow(0f)
        val speedGainDb: StateFlow<Float> = _speedGainDb.asStateFlow()
        val compare: StateFlow<CompareState?> = _compare.asStateFlow()

        // Sound goal ("Gesang vorne", "Mehr Power", ...): a small curve on top of the genre preset.
        private val _activeGoal = MutableStateFlow(SoundGoal.BALANCED)
        val activeGoal: StateFlow<SoundGoal> = _activeGoal.asStateFlow()

        fun setSoundGoal(goal: SoundGoal) {
            _activeGoal.value = goal
            recalculateBandGains()
            persistLiveSettings()
        }

        // "Kopfhörer-Power": wirkt nur im Kopfhörer-Modus.
        private val _headphonePower = MutableStateFlow(HeadphonePower())
        val headphonePower: StateFlow<HeadphonePower> = _headphonePower.asStateFlow()

        fun setHeadphonePower(power: HeadphonePower) {
            _headphonePower.value = power.sanitized()
            recalculateBandGains()
            persistLiveSettings()
        }

        // P5-3: the free bands and their evaluated curve (re-evaluated only when a band changes).
        //
        // Declared here, above the init block, on purpose: the collectors launched in init run at
        // once on the main thread (scope is Dispatchers.Main.immediate) and already
        // compute the curve. A property declared below init is still null at that point - that
        // crashed the app at every start (NullPointerException in CurveInputs) in every release
        // after 1.1.2-beta.5. Everything the init block reads has to be declared above it.
        private val _parametricBands = MutableStateFlow<List<ParametricBand>>(emptyList())
        val parametricBands: StateFlow<List<ParametricBand>> = _parametricBands.asStateFlow()
        private var parametricCurve: List<TargetPoint> = emptyList()

        // Guards persistLiveSettings() against running before restoreSavedState()
        // has had a chance to load what was on disk - without this, the very
        // first capabilities/init-driven applySettings() call would write this
        // ViewModel's temporary just-constructed defaults over the real saved
        // state before it's even been read back.
        private var hasRestoredState = false
        private val stateRestored = CompletableDeferred<Unit>()

        // Session listening/attach and route monitoring are owned by
        // AudioSessionForegroundService now, not this ViewModel - that keeps them
        // running for as long as the app process is alive, not just while this
        // screen is open (see roadmap.md Session 13). This ViewModel only reacts to
        // capability changes the (singleton) engine reports.
        init {
            quickControls.consumerAttached()
            scope.launch {
                // Tiles and widget show this; republished on every change that matters to them.
                combine(_processingSettings, _activePreset) { settings, preset ->
                    QuickState(masterEnabled = settings.masterEnabled, presetName = preset.name)
                }.collect { state -> if (hasRestoredState) quickControls.publish(state) }
            }
            scope.launch {
                quickControls.commands.collect { command ->
                    stateRestored.await()
                    handleQuickCommand(command)
                }
            }
            scope.launch {
                audioEngine.capabilities.collect { caps ->
                    recalculateBandGains(caps.bands)
                }
            }
            scope.launch {
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
            scope.launch {
                routeRepository.activeRoute
                    .drop(1)
                    .collect { route ->
                        if (hasRestoredState) {
                            applyDeviceProfileForRoute(route)
                            applyAppProfile(sessionRepository.activeSession.value?.packageName)
                        }
                    }
            }
            scope.launch {
                sessionRepository.activeSession
                    .map { it?.packageName }
                    .distinctUntilChanged()
                    .collect { packageName ->
                        stateRestored.await()
                        applyAppProfile(packageName)
                    }
            }
            // The Virtual Bass approximation exists only for foreign sessions.
            scope.launch {
                isOwnPlayerSession
                    .drop(1)
                    .collect { if (hasRestoredState) recalculateBandGains() }
            }
            // Binding to this app's own player (or away from it) moves the curve between the
            // player's PCM equalizer and the system equalizer.
            scope.launch {
                sessionRepository.activeSession
                    .map { it?.packageName == BuildConfig.APPLICATION_ID }
                    .drop(1)
                    .collect { if (hasRestoredState) pushToEngine(_processingSettings.value) }
            }
            // Android Auto connecting or disconnecting changes what "detected" means for the
            // current route: re-decide the mode (a saved choice for the route still wins).
            scope.launch {
                carConnectionRepository.carConnected
                    .drop(1)
                    .collect {
                        if (hasRestoredState) reapplyContextModeForCurrentRoute()
                    }
            }
            // Makes the "Kopfhörer-Modus" switch (and automatic route-based
            // defaulting) actually change the applied EQ curve - combinedCurve()
            // already reads effectiveHeadphoneAcoustics.value itself, but nothing
            // re-triggers recalculateBandGains() when *only* this changes (e.g. a
            // route switch that changes the default with no other route-profile
            // fields differing) without this collector.
            scope.launch {
                effectiveHeadphoneAcoustics.collect {
                    recalculateBandGains()
                }
            }
            // Loudness compensation follows the system volume, which can change at any
            // time (hardware keys, another app): re-run the band mapping, but only while
            // something actually uses it - headphone mode's loudness curve or the active
            // sound's own loudnessMaxBoostDb - so volume key presses do not needlessly
            // re-apply unchanged settings to the engine.
            scope.launch {
                volumeRepository.currentLevelDb.collect {
                    if (hasRestoredState && (effectiveHeadphoneAcoustics.value || _processingSettings.value.loudnessMaxBoostDb > 0f)) {
                        recalculateBandGains()
                    }
                }
            }
        }

        private suspend fun restoreSavedState() {
            val initialCustomPresets = presetRepository.customPresets.first()
            customPresetsState.value = initialCustomPresets

            val initialCorrectionProfiles = correctionProfileRepository.customProfiles.first()
            customCorrectionProfilesState.value = initialCorrectionProfiles

            // Damaged saved data must not stop the controller (it would never report itself restored,
            // and the session service waits for that): the compiled-in defaults stay then.
            val saved =
                try {
                    appSettingsRepository.liveSettings.first()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            if (saved != null) {
                val preset = findPresetById(saved.activePresetId, initialCustomPresets)
                if (preset != null) {
                    _activePreset.value = preset
                    _isDirty.value = saved.isDirty
                    _processingSettings.value = saved.processingSettings
                    audioEngine.apply(saved.processingSettings)
                    pushVirtualBass(saved.processingSettings)
                }
                _activeCorrectionProfile.value =
                    findCorrectionProfileById(saved.activeCorrectionProfileId, initialCorrectionProfiles)
                _activeContext.value = saved.activeContext?.let { name -> SoundContext.entries.firstOrNull { it.name == name } }
                contextSetAutomatically = saved.activeContextAutomatic
                // A mode the user switched on by hand survives the restart until the first
                // route decision; from then on the route's own saved choice or detection rules.
                pendingRestoredContext = if (saved.activeContextAutomatic) null else _activeContext.value
                _headphonePower.value = saved.headphonePower.sanitized()
                _activeGoal.value = SoundGoal.fromId(saved.activeGoal)
                _parametricBands.value = saved.parametricBands.take(ParametricCurve.MAX_BANDS).map { it.clamped() }
                parametricCurve = ParametricCurve.bandsToCurve(_parametricBands.value)
                // A saved activePresetId/activeCorrectionProfileId that no longer
                // resolves (its custom entry was deleted from another install, say)
                // just keeps this ViewModel's own compiled-in default - not an
                // error, nothing to recover, per M2's "beschädigtes ... kann die
                // App nicht am Start hindern".
            }

            hasRestoredState = true
            stateRestored.complete(Unit)
            quickControls.publish(QuickState(_processingSettings.value.masterEnabled, _activePreset.value.name))

            // Route-specific binding takes precedence over the plain "last active"
            // LiveSettings restored above, if this route has one - that's the
            // actual point of M3 (same taste, different headphones).
            applyDeviceProfileForRoute(routeRepository.activeRoute.value)
            applyAppProfile(sessionRepository.activeSession.value?.packageName)

            // Keep collecting custom-preset/-correction-profile changes after this
            // point (deletions, saves) - the one-shot .first() calls above were
            // only for resolving the restore above without a race against these
            // collectors' first emission.
            presetRepository.customPresets
                .drop(1)
                .onEach { customPresetsState.value = it }
                .launchIn(scope)
            correctionProfileRepository.customProfiles
                .drop(1)
                .onEach { customCorrectionProfilesState.value = it }
                .launchIn(scope)
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
            routeContextMode = binding?.contextMode
            val preset = binding?.let { findPresetById(it.boundPresetId, customPresetsState.value) }
            // A profile saved while the context tunings were still presets of their own
            // means "this route uses that context" - an explicit choice for it.
            val legacyContext = preset?.let { BuiltInContextPresets.contextOf(it) }
            applyContextModeForRoute(route, legacyContext)
            _deviceKindPrompt.value =
                route.takeIf {
                    RouteContextResolver.shouldAskDeviceKind(
                        route = it,
                        storedMode = routeContextMode,
                        legacyContext = legacyContext,
                        dismissedRouteIds = dismissedDeviceKindRouteIds,
                        carConnected = carConnectionRepository.carConnected.value,
                    )
                }
            if (binding == null || preset == null) return

            // A profile saved while the context tunings were still presets of their own
            // means "this route uses that context": keep the genre preset as it is and
            // turn the mode on instead of replacing the sound with it.
            if (BuiltInContextPresets.contextOf(preset) != null) return
            val correction = findCorrectionProfileById(binding.boundCorrectionProfileId, customCorrectionProfilesState.value)

            _activePreset.value = preset
            _activeCorrectionProfile.value = correction
            _isDirty.value = false
            _processingSettings.value = _processingSettings.value.withPreset(preset)
            recalculateBandGains()
            persistLiveSettings()
        }

        // Car / Bluetooth-speaker mode is decided per route, from the route's saved choice
        // (DeviceProfileEntity.contextMode) or, with none, from detection (see
        // SoundContextClassifier - a name heuristic). A manual choice therefore never
        // follows the user to another route. The genre preset is never touched.
        private fun applyContextModeForRoute(
            route: AudioRoute,
            legacyContext: SoundContext?,
        ) {
            val restored = pendingRestoredContext
            pendingRestoredContext = null
            val decision =
                RouteContextResolver.resolve(
                    route = route,
                    carConnected = carConnectionRepository.carConnected.value,
                    storedMode = routeContextMode,
                    legacyContext = legacyContext,
                    restoredContext = restored,
                )
            contextSetAutomatically = decision.automatic
            val mode = decision.mode
            applyContextMode(mode)
        }

        fun answerDeviceKind(kind: DeviceKind) {
            _deviceKindPrompt.value ?: return
            _deviceKindPrompt.value = null
            headphoneAcousticsOverrideState.value = kind == DeviceKind.HEADPHONES
            setContextMode(
                when (kind) {
                    DeviceKind.HEADPHONES -> null
                    DeviceKind.CAR -> SoundContext.CAR
                    DeviceKind.SPEAKER -> SoundContext.BLUETOOTH_SPEAKER
                },
            )
        }

        fun dismissDeviceKindPrompt() {
            _deviceKindPrompt.value?.let { dismissedDeviceKindRouteIds.add(it.id) }
            _deviceKindPrompt.value = null
        }

        private suspend fun reapplyContextModeForCurrentRoute() {
            val binding = deviceProfileRepository.getProfileForRoute(currentRoute.value.id)
            val preset = binding?.let { findPresetById(it.boundPresetId, customPresetsState.value) }
            applyContextModeForRoute(currentRoute.value, preset?.let { BuiltInContextPresets.contextOf(it) })
        }

        /** The user's own choice of listening-context mode (null = off), remembered for the current route. */
        fun setContextMode(context: SoundContext?) {
            contextSetAutomatically = false
            routeContextMode = context?.name ?: CONTEXT_MODE_NONE
            applyContextMode(context)
            saveDeviceProfileBinding()
        }

        /**
         * Hands the mode back to the current route's detection (remote control: `eq/context` with
         * `{"auto":true}`): the route's saved choice is cleared and detection decides again.
         */
        fun setContextAutomatic() {
            routeContextMode = null
            applyContextModeForRoute(currentRoute.value, legacyContext = null)
            saveDeviceProfileBinding()
        }

        private fun applyContextMode(context: SoundContext?) {
            _activeContext.value = context
            _processingSettings.value = _processingSettings.value.withContextFeatures(_activePreset.value)
            recalculateBandGains()
            persistLiveSettings()
        }

        // Persists which voicing/correction are active while `route` (the current
        // route unless stated otherwise) is the active one - called from every
        // user-initiated preset/correction selection, never from
        // applyDeviceProfileForRoute() above (that would just save back the exact
        // binding it just read).
        private fun saveDeviceProfileBinding() {
            rememberForActiveAppIfBound()
            val route = currentRoute.value
            val headphoneAcousticsOverride = headphoneAcousticsOverrideState.value
            scope.launch {
                deviceProfileRepository.saveProfile(
                    routeId = route.id,
                    routeType = route.type.name,
                    displayName = route.name,
                    boundPresetId = _activePreset.value.id,
                    boundCorrectionProfileId = _activeCorrectionProfile.value.id,
                    headphoneAcousticsOverride = headphoneAcousticsOverride,
                    contextMode = routeContextMode,
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
            scope.launch {
                appSettingsRepository.save(
                    LiveSettings(
                        activePresetId = _activePreset.value.id,
                        isDirty = _isDirty.value,
                        processingSettings = _processingSettings.value,
                        activeCorrectionProfileId = _activeCorrectionProfile.value.id,
                        activeContext = _activeContext.value?.name,
                        activeContextAutomatic = contextSetAutomatically,
                        headphonePower = _headphonePower.value,
                        activeGoal = _activeGoal.value.id.takeIf { _activeGoal.value != SoundGoal.BALANCED },
                        parametricBands = _parametricBands.value,
                    ),
                )
            }
        }

        fun selectPreset(preset: Preset) {
            // A context tuning is a mode, not a sound: selecting one (an older
            // shortcut) switches the mode on and leaves the active preset alone.
            BuiltInContextPresets.contextOf(preset)?.let { context ->
                setContextMode(context)
                return
            }
            _activePreset.value = preset
            _isDirty.value = false
            _processingSettings.value = _processingSettings.value.withPreset(preset)
            recalculateBandGains()
            persistLiveSettings()
            saveDeviceProfileBinding()
        }

        // Chat feature: genre x intensity preset redesign. Resolves the pair into
        // a concrete Preset and routes through the existing selectPreset() -
        // same persistence/headroom/dirty-flag pipeline as any other preset pick,
        // no separate code path to keep in sync.
        fun selectGenreIntensity(
            genre: GenrePreset,
            intensity: PresetIntensity,
        ) {
            selectPreset(PresetIntensityResolver.resolve(genre, intensity))
        }

        // M3 "Mein Kopfhörer": switches the active correction curve, independent of
        // the voicing (selectPreset above) it gets combined with.
        fun selectCorrectionProfile(profile: CorrectionProfile) {
            _activeCorrectionProfile.value = profile
            recalculateBandGains()
            persistLiveSettings()
            saveDeviceProfileBinding()
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
                loudnessMaxBoostDb = settings.loudnessMaxBoostDb,
                subsonicCutoffHz = settings.subsonicCutoffHz,
                virtualBassMix = settings.virtualBassMix,
            )
        }

        // From the quick-settings tiles, the widget, or an intent that opened the app for one.
        fun handleQuickCommand(command: QuickCommand) {
            when (command) {
                QuickCommand.TOGGLE_MASTER -> setMasterEnabled(!_processingSettings.value.masterEnabled)
                QuickCommand.NEXT_PRESET -> {
                    val presets = allPresets.value
                    val nextId = PresetCycle.nextId(presets.map { it.id }, _activePreset.value.id) ?: return
                    presets.firstOrNull { it.id == nextId }?.let { selectPreset(it) }
                }
            }
        }

        // Switching off also stops the built-in player. Whether switching on offers the source
        // picker is the screen's business (MainViewModel.setMasterEnabled): without a screen, as
        // from a quick tile, no dialog opens.
        fun setMasterEnabled(enabled: Boolean) {
            val newSettings = _processingSettings.value.copy(masterEnabled = enabled)
            applySettings(newSettings)
            if (!enabled) playerBridge.stopPlayer()
        }

        /** Suspends until the saved state has been loaded and applied (E-008: the session service waits for this). */
        suspend fun awaitRestored() = stateRestored.await()

        /** True once [awaitRestored] would return at once. */
        val isRestored: Boolean get() = stateRestored.isCompleted

        // Preset management. The screen adds its own confirmations (MainViewModel.feedback).

        /** Saves the current sound as a new user preset and makes it the active one. */
        suspend fun saveAsNewPreset(name: String): Preset {
            val newPreset = buildPresetFromCurrentState(name)
            presetRepository.save(newPreset)
            _activePreset.value = newPreset
            _isDirty.value = false
            persistLiveSettings()
            return newPreset
        }

        /** A pasted or scanned link becomes an ordinary, editable user preset and is selected. */
        suspend fun importPresetLink(text: String): Result<Preset> =
            PresetShareCodec.decode(text).onSuccess { preset ->
                presetRepository.save(preset)
                selectPreset(preset)
            }

        /** Saves a copy of [preset] under a new id and [name] and selects it. */
        suspend fun duplicatePreset(
            preset: Preset,
            name: String,
        ): Preset {
            val copy =
                preset.copy(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    metadata = preset.metadata.copy(builtIn = false),
                )
            presetRepository.save(copy)
            selectPreset(copy)
            return copy
        }

        /** Built-ins can't be renamed; returns false then. */
        suspend fun renamePreset(
            preset: Preset,
            newName: String,
        ): Boolean {
            if (preset.metadata.builtIn || newName.isBlank()) return false
            val renamed = preset.copy(name = newName)
            presetRepository.save(renamed)
            if (_activePreset.value.id == preset.id) {
                _activePreset.value = renamed
                persistLiveSettings()
            }
            return true
        }

        /** Deletes a user preset; the active one falls back to Clean Punch. */
        suspend fun deletePreset(preset: Preset) {
            presetRepository.delete(preset.id)
            if (_activePreset.value.id == preset.id) {
                selectPreset(BuiltInPresets.CleanPunch)
            }
        }

        /** Saves an imported or suggested correction profile (it must exist before a route binding names it). */
        fun saveCorrectionProfile(profile: CorrectionProfile) {
            scope.launch { correctionProfileRepository.save(profile) }
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
                    preEqGainsDb = preEqGainsFor(currentGains),
                )
            _isDirty.value = true
            applySettings(newSettings)
        }

        fun addParametricBand() {
            val current = _parametricBands.value
            if (current.size >= ParametricCurve.MAX_BANDS) return
            // A new band starts where there is room: at 1 kHz first, then at the next free octave.
            val taken = current.map { it.frequencyHz }
            val frequency = PARAMETRIC_START_FREQUENCIES_HZ.firstOrNull { start -> taken.none { abs(it - start) < start * 0.2f } } ?: 1000f
            setParametricBands(current + ParametricBand(frequencyHz = frequency, gainDb = 0f, q = 1f))
        }

        fun updateParametricBand(
            index: Int,
            band: ParametricBand,
        ) {
            val current = _parametricBands.value
            if (index !in current.indices) return
            setParametricBands(current.toMutableList().also { it[index] = band.clamped() })
        }

        fun removeParametricBand(index: Int) {
            val current = _parametricBands.value
            if (index !in current.indices) return
            setParametricBands(current.toMutableList().also { it.removeAt(index) })
        }

        /** Sets the whole list at once (remote control, 04f); values are clamped as everything from outside. */
        fun replaceParametricBands(bands: List<ParametricBand>) {
            setParametricBands(bands.take(ParametricCurve.MAX_BANDS).map { it.clamped() })
        }

        /**
         * False when this device cannot run the 34-band DynamicsProcessing pre-EQ: it refused it,
         * or an attached effect chain has no DynamicsProcessing at all.
         */
        val highResolutionAvailable: Boolean
            get() {
                val caps = capabilities.value
                return caps.preEqUnavailableReason == null && (caps.totalEffectCount == 0 || caps.hasDynamicsProcessing)
            }

        private fun setParametricBands(bands: List<ParametricBand>) {
            _parametricBands.value = bands
            parametricCurve = ParametricCurve.bandsToCurve(bands)
            _isDirty.value = true
            recalculateBandGains()
            persistLiveSettings()
        }

        // The inputs of the target curve as they are right now; the arithmetic lives in CurveCalculator.
        private fun curveInputs(): CurveInputs =
            CurveInputs(
                correctionCurve = _activeCorrectionProfile.value.curve,
                presetCurve = _activePreset.value.targetCurve,
                headphoneAcoustics = effectiveHeadphoneAcoustics.value,
                headphonePower = _headphonePower.value,
                currentLevelDb = volumeRepository.currentLevelDb.value,
                goalCurve = _activeGoal.value.curve,
                context = _activeContext.value,
                ownPlayerSession = isOwnPlayerSession.value,
                preEqAvailable = capabilities.value.preEqCutoffsHz.isNotEmpty(),
                settings = _processingSettings.value,
                parametricCurve = parametricCurve,
            )

        // M3 "Zielkurven kombinieren" - see CurveCalculator.combinedCurve.
        private fun combinedCurve(includeSubsonic: Boolean = true): List<TargetPoint> =
            CurveCalculator.combinedCurve(curveInputs(), includeSubsonic)

        private fun preEqGainsFor(classicBandGainsDb: Map<Int, Float>): List<Float> =
            CurveCalculator.preEqGains(curveInputs(), capabilities.value, classicBandGainsDb)

        /** Switch between the classic 5-band equalizer and the DynamicsProcessing pre-EQ (experimental). */
        fun setHighResolutionEq(enabled: Boolean) {
            applySettings(_processingSettings.value.copy(highResolutionEq = enabled))
            // The band layout is fixed when the effect is created, so it has to be rebuilt.
            scope.launch { audioEngine.retry() }
        }

        /**
         * Set the pre-EQ response mode: SMOOTH (gentle), BALANCED (default), or PRECISE (aggressive).
         * Controls how manual band adjustments spread and whether Simpson integration is used.
         * More precise = punchier bass, tighter control, but potentially more artifacts.
         */
        fun setPreEqResponseMode(mode: com.auracurve.app.dsp.PreEqResponseMode) {
            applySettings(_processingSettings.value.copy(preEqResponseMode = mode.name))
            recalculateBandGains()
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
            // Chat feature: "quality changes for headphones" - gentler dynamics was
            // the option picked over loudness compensation or real crossfeed.
            // Recomputed from the canonical activePreset value (not the current
            // processingSettings, which may already be headphone-eased from a
            // previous call) every time this runs, including on every
            // effectiveHeadphoneAcoustics change via the init collector - so
            // toggling headphone mode without reselecting a preset still applies/
            // removes the easing, and it never compounds across repeated calls.
            val preset = _activePreset.value
            val easeDynamics = effectiveHeadphoneAcoustics.value && _headphonePower.value.easeDynamics
            // An active context mode only ever tightens the genre's dynamics (lower
            // threshold, higher ratio) and switches the compressor on; the headphone
            // easing is applied on top of that.
            val context = _activeContext.value?.let { BuiltInContextPresets.defaultFor(it) }
            val baseMbcThresholdDb = context?.let { minOf(preset.mbcThresholdDb, it.mbcThresholdDb) } ?: preset.mbcThresholdDb
            val baseMbcRatio = context?.let { maxOf(preset.mbcRatio, it.mbcRatio) } ?: preset.mbcRatio
            val mbcThresholdDb =
                if (easeDynamics) {
                    HeadphoneDynamicsEasing.easedThresholdDb(baseMbcThresholdDb)
                } else {
                    baseMbcThresholdDb
                }
            val mbcRatio =
                if (easeDynamics) {
                    HeadphoneDynamicsEasing.easedRatio(baseMbcRatio)
                } else {
                    baseMbcRatio
                }
            val newSettings =
                _processingSettings.value.copy(
                    bandGainsDb = calculatedGains,
                    inputGainDb = safetyScaledInputGainDb(headroom.maxPositiveGainDb),
                    mbcEnabled = preset.mbcEnabled || context != null,
                    mbcThresholdDb = mbcThresholdDb,
                    mbcRatio = mbcRatio,
                    preEqGainsDb = preEqGainsFor(calculatedGains),
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
            ).withContextFeatures(preset)

        // The preset's own context features, merged with what the active context mode
        // adds (the stronger of the two each, and a limiter at least as tight). With
        // the mode off this is just the preset's values - so switching it off restores
        // them, and switching genres keeps the mode's protection.
        private fun ProcessingSettings.withContextFeatures(basePreset: Preset): ProcessingSettings {
            val context = _activeContext.value?.let { BuiltInContextPresets.defaultFor(it) }
            val baseLimiterThresholdDb = if (basePreset.limiter.enabled) basePreset.limiter.thresholdDb else 0f
            return copy(
                limiterEnabled = basePreset.limiter.enabled || context != null,
                limiterThresholdDb =
                    if (context == null) basePreset.limiter.thresholdDb else minOf(baseLimiterThresholdDb, context.limiter.thresholdDb),
                loudnessMaxBoostDb = maxOf(basePreset.loudnessMaxBoostDb, context?.loudnessMaxBoostDb ?: 0f),
                subsonicCutoffHz = maxOf(basePreset.subsonicCutoffHz, context?.subsonicCutoffHz ?: 0f),
                virtualBassMix = maxOf(basePreset.virtualBassMix, context?.virtualBassMix ?: 0f),
            )
        }

        private fun automaticInputGainDb(bandGainsDb: Map<Int, Float>): Float {
            val peakBoostDb = bandGainsDb.values.maxOrNull()?.coerceAtLeast(0f) ?: 0f
            return safetyScaledInputGainDb(peakBoostDb)
        }

        // Guard against returning -0.0f: boxed Float.equals() (used by assertEquals in
        // tests, and by anything else comparing boxed Floats) treats -0.0f and 0.0f as
        // unequal even though == says they're the same.
        // Small speaker drivers distort mechanically before anything clips digitally, and
        // the limiter does not protect the membrane - so the speaker mode pulls the input
        // gain down much further than the default.
        private fun inputGainSafetyRatio(): Float =
            when (_activeContext.value) {
                SoundContext.BLUETOOTH_SPEAKER -> SPEAKER_INPUT_GAIN_SAFETY_RATIO
                else -> INPUT_GAIN_SAFETY_RATIO
            }

        private fun safetyScaledInputGainDb(boostDb: Float): Float {
            if (boostDb == 0f) return 0f
            return -(boostDb * inputGainSafetyRatio())
        }

        private fun applySettings(settings: ProcessingSettings) {
            _processingSettings.value = settings
            pushToEngine(settings)
            persistLiveSettings()
        }

        // What the engine really gets: the live settings, or - while the Original/EQ comparison
        // runs - the loudness-matched version of the chosen side (see LoudnessMatch).

        /** Module 11, F6: the speed regulator's extra gain (0 … 6 dB). Call on the main thread. */
        fun setSpeedGainDb(db: Float) {
            val clamped = db.coerceIn(0f, MAX_SPEED_GAIN_DB)
            if (clamped == _speedGainDb.value) return
            _speedGainDb.value = clamped
            pushToEngine(_processingSettings.value)
        }

        private fun pushToEngine(settings: ProcessingSettings) {
            val compare = _compare.value
            val forEngine =
                if (compare == null) {
                    settings
                } else {
                    val bands = capabilities.value.bands
                    val plan = LoudnessMatch.plan(CurveCalculator.eqLevelDeltaDb(settings, bands))
                    when (compare.side) {
                        CompareSide.EQ -> settings.copy(inputGainDb = settings.inputGainDb + plan.eqExtraGainDb)
                        CompareSide.ORIGINAL ->
                            settings.copy(
                                bypass = false,
                                bandGainsDb = bands.associate { it.index to 0f },
                                preEqGainsDb = settings.preEqGainsDb.map { 0f },
                                inputGainDb = plan.originalGainDb,
                                limiterEnabled = false,
                                mbcEnabled = false,
                            )
                    }
                }
            // Only the dynamics (MBC, limiter) stay on the system path for the own player.
            val forSystem =
                if (pcmEqualizerOwnsTheCurve(settings)) {
                    forEngine.copy(
                        bandGainsDb = forEngine.bandGainsDb.mapValues { 0f },
                        preEqGainsDb = forEngine.preEqGainsDb.map { 0f },
                        inputGainDb = 0f,
                    )
                } else {
                    forEngine
                }
            // F6: on top of the headroom the EQ reserved, caught by the limiter (forced on). The base
            // stays capped at 0 dB as before, only the speed gain may lift the input above it.
            val speed = _speedGainDb.value
            val withSpeed =
                forSystem.copy(
                    inputGainDb = forSystem.inputGainDb.coerceAtMost(0f) + speed,
                    limiterEnabled = forSystem.limiterEnabled || speed > 0f,
                )
            scope.launch {
                audioEngine.apply(withSpeed)
            }
            val forPlayer = if (compare?.side == CompareSide.ORIGINAL) settings.copy(bypass = true) else settings
            pushVirtualBass(forPlayer)
            pushPlayerCurve(forPlayer)
        }

        // The player's own PCM equalizer follows the exact curve; the subsonic cut is a real
        // high-pass there, so it travels separately and the curve must not contain it twice.
        // The macros are not part of the curve either, so they are sampled into it.
        private fun pushPlayerCurve(settings: ProcessingSettings) {
            val active = settings.masterEnabled && !settings.bypass
            if (!active) {
                playerBridge.setPlayerCurve(emptyList(), 0f)
                return
            }
            val curve = combinedCurve(includeSubsonic = false).sortedBy { it.frequencyHz }
            val sampled =
                generateSequence(PLAYER_CURVE_MIN_HZ) { it * PLAYER_CURVE_STEP }
                    .takeWhile { it <= PLAYER_CURVE_MAX_HZ }
                    .map { hz ->
                        TargetPoint(
                            hz,
                            EqualizerInterpolator.resolveGainAtFrequency(
                                hz,
                                curve,
                                settings.macroBassDb,
                                settings.macroPunchDb,
                                settings.macroHaerteDb,
                            ),
                        )
                    }.toList()
            playerBridge.setPlayerCurve(sampled, settings.subsonicCutoffHz)
        }

        // True while the EQ is bound to this app's own player: its PCM equalizer already
        // applies the curve, so the system equalizer must not apply it a second time.
        private fun pcmEqualizerOwnsTheCurve(settings: ProcessingSettings): Boolean =
            settings.masterEnabled && !settings.bypass && sessionRepository.activeSession.value?.packageName == BuildConfig.APPLICATION_ID

        fun startCompare() = setCompareSide(CompareSide.EQ)

        fun setCompareSide(side: CompareSide) {
            val settings = _processingSettings.value
            val delta = CurveCalculator.eqLevelDeltaDb(settings, capabilities.value.bands)
            _compare.value = CompareState(side = side, eqLevelDeltaDb = delta)
            pushToEngine(settings)
        }

        fun endCompare() {
            if (_compare.value == null) return
            _compare.value = null
            pushToEngine(_processingSettings.value)
        }

        // Virtual bass is not part of the system effect chain (AudioEngine) - it runs
        // in the built-in player's own audio pipeline, so it goes through the player
        // bridge instead. Follows master/bypass like the rest of the processing.
        private fun pushVirtualBass(settings: ProcessingSettings) {
            playerBridge.setVirtualBassMix(if (settings.masterEnabled && !settings.bypass) settings.virtualBassMix else 0f)
        }

        // Individual switches for the three context features, for A/B listening
        // and for enabling them on a preset that does not use them. Switching on
        // restores the active preset's own value, or the car preset's as a sane
        // default if that preset has none. Counts as a manual edit (isDirty).
        fun setLoudnessCompensationEnabled(enabled: Boolean) {
            val onValue = _activePreset.value.loudnessMaxBoostDb.takeIf { it > 0f } ?: BuiltInContextPresets.Car.loudnessMaxBoostDb
            updateContextFeature { it.copy(loudnessMaxBoostDb = if (enabled) onValue else 0f) }
        }

        fun setSubsonicFilterEnabled(enabled: Boolean) {
            val onValue = _activePreset.value.subsonicCutoffHz.takeIf { it > 0f } ?: BuiltInContextPresets.Car.subsonicCutoffHz
            updateContextFeature { it.copy(subsonicCutoffHz = if (enabled) onValue else 0f) }
        }

        fun setVirtualBassEnabled(enabled: Boolean) {
            val onValue = _activePreset.value.virtualBassMix.takeIf { it > 0f } ?: BuiltInContextPresets.Car.virtualBassMix
            updateContextFeature { it.copy(virtualBassMix = if (enabled) onValue else 0f) }
        }

        private fun updateContextFeature(transform: (ProcessingSettings) -> ProcessingSettings) {
            _processingSettings.value = transform(_processingSettings.value)
            _isDirty.value = true
            recalculateBandGains()
        }
    }
