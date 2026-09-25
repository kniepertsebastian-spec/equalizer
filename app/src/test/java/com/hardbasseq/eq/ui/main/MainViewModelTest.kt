package com.hardbasseq.eq.ui.main

import com.hardbasseq.eq.audio.AudioCapabilities
import com.hardbasseq.eq.audio.AudioEffectDescriptor
import com.hardbasseq.eq.audio.AudioEffectRepository
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.audio.AudioRouteRepository
import com.hardbasseq.eq.audio.AudioSession
import com.hardbasseq.eq.audio.EffectConnectMode
import com.hardbasseq.eq.audio.FakeAudioEngine
import com.hardbasseq.eq.audio.KnownEffectTypeIds
import com.hardbasseq.eq.audio.ProcessingSettings
import com.hardbasseq.eq.correction.BuiltInCorrectionProfiles
import com.hardbasseq.eq.correction.CorrectionProfile
import com.hardbasseq.eq.correction.CorrectionProfileRepository
import com.hardbasseq.eq.data.profile.DeviceProfileEntity
import com.hardbasseq.eq.diagnostics.InMemoryDiagnosticsRecorder
import com.hardbasseq.eq.dsp.CurveComposer
import com.hardbasseq.eq.dsp.HeadroomCalculator
import com.hardbasseq.eq.integration.PlayerBridge
import com.hardbasseq.eq.integration.PlayerSource
import com.hardbasseq.eq.preset.BuiltInPresets
import com.hardbasseq.eq.preset.Preset
import com.hardbasseq.eq.preset.PresetRepository
import com.hardbasseq.eq.profile.DeviceProfileRepository
import com.hardbasseq.eq.settings.AppSettingsRepository
import com.hardbasseq.eq.settings.LiveSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val fakeEngine = FakeAudioEngine()
    private val fakeRouteRepo = FakeAudioRouteRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggling on queries the repository and exposes descriptors`() =
        runTest {
            val descriptor = fakeDescriptor("Fake Equalizer")
            val viewModel = createViewModel(listOf(descriptor))

            assertFalse(viewModel.showDebugEffects.value)
            assertTrue(viewModel.effectDescriptors.value.isEmpty())

            viewModel.toggleDebugEffects()
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.showDebugEffects.value)
            assertEquals(listOf(descriptor), viewModel.effectDescriptors.value)
        }

    @Test
    fun `toggling off hides the list without discarding loaded descriptors`() =
        runTest {
            val descriptor = fakeDescriptor("Fake Equalizer")
            val viewModel = createViewModel(listOf(descriptor))

            viewModel.toggleDebugEffects()
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.toggleDebugEffects()

            assertFalse(viewModel.showDebugEffects.value)
            assertEquals(listOf(descriptor), viewModel.effectDescriptors.value)
        }

    @Test
    fun `selectPreset updates active preset and interpolates gains`() =
        runTest {
            val viewModel = createViewModel(emptyList())

            viewModel.selectPreset(BuiltInPresets.DeepRumble)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(BuiltInPresets.DeepRumble.id, viewModel.activePreset.value.id)
            assertEquals(BuiltInPresets.DeepRumble.macroBassDb, viewModel.processingSettings.value.macroBassDb)
            assertEquals(BuiltInPresets.DeepRumble.macroPunchDb, viewModel.processingSettings.value.macroPunchDb)
            assertEquals(BuiltInPresets.DeepRumble.macroHaerteDb, viewModel.processingSettings.value.macroHaerteDb)
            // M4: inputGainDb now comes from HeadroomCalculator sampling the combined
            // (correction + voicing) curve, not from the highest of the discrete
            // post-band-mapping gains - recompute it the same way MainViewModel does
            // (no correction profile selected here, so this is BuiltInPresets.DeepRumble's
            // own curve unchanged) rather than asserting a stale, pre-M3/M4 formula.
            val combinedCurve = CurveComposer.combine(BuiltInCorrectionProfiles.None.curve, BuiltInPresets.DeepRumble.targetCurve)
            val headroom =
                HeadroomCalculator.fromCombinedCurve(
                    combinedCurve = combinedCurve,
                    macroBassDb = BuiltInPresets.DeepRumble.macroBassDb,
                    macroPunchDb = BuiltInPresets.DeepRumble.macroPunchDb,
                    macroHaerteDb = BuiltInPresets.DeepRumble.macroHaerteDb,
                )
            // Input gain only pre-cancels 30% of the peak boost (INPUT_GAIN_SAFETY_RATIO
            // in MainViewModel) - the rest stays audible, with the Limiter as the real
            // safety net against clipping. See roadmap.md Session 16/17.
            val expectedInputGainDb = -(headroom.maxPositiveGainDb * 0.3f)
            assertEquals(expectedInputGainDb, viewModel.processingSettings.value.inputGainDb, 0.01f)
            assertEquals(BuiltInPresets.DeepRumble.mbcThresholdDb, viewModel.processingSettings.value.mbcThresholdDb)
            assertEquals(BuiltInPresets.DeepRumble.mbcRatio, viewModel.processingSettings.value.mbcRatio)
            assertTrue(viewModel.processingSettings.value.mbcEnabled)
            assertTrue(
                viewModel.processingSettings.value.bandGainsDb
                    .isNotEmpty(),
            )
        }

    @Test
    fun `manual boost automatically reserves 30 percent as headroom`() =
        runTest {
            val viewModel = createViewModel(emptyList())
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.setBandGain(bandIndex = 0, gainDb = 8f)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(-2.4f, viewModel.processingSettings.value.inputGainDb)
            assertEquals(8f, viewModel.processingSettings.value.bandGainsDb[0])
        }

    @Test
    fun `flat preset disables dynamics and resets controls`() =
        runTest {
            val viewModel = createViewModel(emptyList())

            viewModel.selectPreset(BuiltInPresets.DeepRumble)
            viewModel.selectPreset(BuiltInPresets.Flat)
            dispatcher.scheduler.advanceUntilIdle()

            val settings = viewModel.processingSettings.value
            assertEquals(0f, settings.macroBassDb)
            assertEquals(0f, settings.macroPunchDb)
            assertEquals(0f, settings.macroHaerteDb)
            assertEquals(0f, settings.inputGainDb)
            assertFalse(settings.mbcEnabled)
            assertFalse(settings.limiterEnabled)
            assertTrue(settings.bandGainsDb.values.all { it == 0f })
        }

    @Test
    fun `enabling master with no session shows the source picker`() =
        runTest {
            val playerBridge = FakePlayerBridge()
            val viewModel = createViewModel(emptyList(), playerBridge)

            viewModel.setMasterEnabled(true)

            assertTrue(viewModel.showSourcePicker.value)
            assertEquals(0, playerBridge.launchCount)
        }

    @Test
    fun `enabling master while Listening (no session) also shows the source picker`() =
        runTest {
            val playerBridge = FakePlayerBridge()
            val viewModel = createViewModel(emptyList(), playerBridge)
            // The engine spends almost all of its idle time in Listening, not
            // Detached (docs/STATE_MACHINE.md) - the picker must trigger for
            // both, not just the brief cold-start Detached window.
            fakeEngine.attach(AudioSession(sessionId = 1))
            fakeEngine.detach()

            viewModel.setMasterEnabled(true)

            assertTrue(viewModel.showSourcePicker.value)
        }

    @Test
    fun `enabling master with an active session does not show the source picker`() =
        runTest {
            val playerBridge = FakePlayerBridge()
            val viewModel = createViewModel(emptyList(), playerBridge)
            fakeEngine.attach(AudioSession(sessionId = 42))

            viewModel.setMasterEnabled(true)

            assertFalse(viewModel.showSourcePicker.value)
        }

    @Test
    fun `choosing a source launches the player and hides the picker`() =
        runTest {
            val playerBridge = FakePlayerBridge()
            val viewModel = createViewModel(emptyList(), playerBridge)
            viewModel.setMasterEnabled(true)

            viewModel.choosePlayerSource(PlayerSource.SOUNDCLOUD)

            assertFalse(viewModel.showSourcePicker.value)
            assertEquals(1, playerBridge.launchCount)
            assertEquals(PlayerSource.SOUNDCLOUD, playerBridge.lastLaunchedSource)
        }

    @Test
    fun `dismissing the source picker does not launch the player`() =
        runTest {
            val playerBridge = FakePlayerBridge()
            val viewModel = createViewModel(emptyList(), playerBridge)
            viewModel.setMasterEnabled(true)

            viewModel.dismissSourcePicker()

            assertFalse(viewModel.showSourcePicker.value)
            assertEquals(0, playerBridge.launchCount)
        }

    @Test
    fun `disabling master stops the player`() =
        runTest {
            val playerBridge = FakePlayerBridge()
            val viewModel = createViewModel(emptyList(), playerBridge)

            viewModel.setMasterEnabled(false)

            assertEquals(1, playerBridge.stopCount)
        }

    @Test
    fun `retryAttach asks the engine to retry`() =
        runTest {
            val viewModel = createViewModel(emptyList())
            fakeEngine.attach(AudioSession(sessionId = 7))

            viewModel.retryAttach()
            dispatcher.scheduler.advanceUntilIdle()

            // FakeAudioEngine.retry() re-attaches whatever it last attached to -
            // this only proves the ViewModel actually calls through to it.
            assertEquals(AudioEngineState.Active(7), fakeEngine.state.value)
        }

    @Test
    fun `opening the source picker shows it even with an active session`() =
        runTest {
            val playerBridge = FakePlayerBridge()
            val viewModel = createViewModel(emptyList(), playerBridge)
            fakeEngine.attach(AudioSession(sessionId = 42))

            viewModel.openSourcePicker()

            assertTrue(viewModel.showSourcePicker.value)
        }

    @Test
    fun `toggling now playing playback delegates to the player bridge`() =
        runTest {
            val playerBridge = FakePlayerBridge()
            val viewModel = createViewModel(emptyList(), playerBridge)

            viewModel.toggleNowPlayingPlayback()

            assertEquals(1, playerBridge.toggleCount)
        }

    @Test
    fun `reopening the player launches SoundCloud`() =
        runTest {
            val playerBridge = FakePlayerBridge()
            val viewModel = createViewModel(emptyList(), playerBridge)

            viewModel.reopenPlayer()

            assertEquals(1, playerBridge.launchCount)
            assertEquals(PlayerSource.SOUNDCLOUD, playerBridge.lastLaunchedSource)
        }

    // --- M2: persistence and custom-preset workflow ---

    @Test
    fun `on init, saved settings referencing a built-in preset are restored`() =
        runTest {
            val saved =
                LiveSettings(
                    activePresetId = BuiltInPresets.DeepRumble.id,
                    isDirty = false,
                    processingSettings = ProcessingSettings().copy(masterEnabled = false, macroBassDb = 3f),
                )
            val viewModel = createViewModel(emptyList(), appSettingsRepository = FakeAppSettingsRepository(saved))
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(BuiltInPresets.DeepRumble.id, viewModel.activePreset.value.id)
            assertFalse(viewModel.processingSettings.value.masterEnabled)
            assertEquals(3f, viewModel.processingSettings.value.macroBassDb)
            assertFalse(viewModel.isDirty.value)
        }

    @Test
    fun `on init, saved settings referencing a custom preset are restored once it loads`() =
        runTest {
            val custom = customPreset(name = "My Sound")
            val saved = LiveSettings(activePresetId = custom.id, isDirty = true, processingSettings = ProcessingSettings())
            val presetRepository = FakePresetRepository(initial = listOf(custom))
            val viewModel =
                createViewModel(
                    emptyList(),
                    presetRepository = presetRepository,
                    appSettingsRepository = FakeAppSettingsRepository(saved),
                )
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(custom.id, viewModel.activePreset.value.id)
            assertTrue(viewModel.isDirty.value)
            assertTrue(viewModel.allPresets.value.any { it.id == custom.id })
        }

    @Test
    fun `a saved preset id that no longer resolves keeps the default instead of crashing`() =
        runTest {
            val saved = LiveSettings(activePresetId = "no-such-preset", isDirty = true, processingSettings = ProcessingSettings())
            val viewModel = createViewModel(emptyList(), appSettingsRepository = FakeAppSettingsRepository(saved))
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(BuiltInPresets.CleanPunch.id, viewModel.activePreset.value.id)
        }

    @Test
    fun `manual band edits mark the active preset dirty`() =
        runTest {
            val viewModel = createViewModel(emptyList())
            dispatcher.scheduler.advanceUntilIdle()
            assertFalse(viewModel.isDirty.value)

            viewModel.setBandGain(bandIndex = 0, gainDb = 4f)

            assertTrue(viewModel.isDirty.value)
        }

    @Test
    fun `manual macro edits mark the active preset dirty`() =
        runTest {
            val viewModel = createViewModel(emptyList())
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.setMacroBass(2f)

            assertTrue(viewModel.isDirty.value)
        }

    @Test
    fun `selecting a preset clears the dirty flag`() =
        runTest {
            val viewModel = createViewModel(emptyList())
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.setBandGain(bandIndex = 0, gainDb = 4f)
            assertTrue(viewModel.isDirty.value)

            viewModel.selectPreset(BuiltInPresets.DeepRumble)

            assertFalse(viewModel.isDirty.value)
        }

    @Test
    fun `resetToActivePreset discards manual edits`() =
        runTest {
            val viewModel = createViewModel(emptyList())
            dispatcher.scheduler.advanceUntilIdle()
            val originalGains = viewModel.processingSettings.value.bandGainsDb
            viewModel.setBandGain(bandIndex = 0, gainDb = 12f)
            assertTrue(viewModel.isDirty.value)

            viewModel.resetToActivePreset()

            assertFalse(viewModel.isDirty.value)
            assertEquals(originalGains, viewModel.processingSettings.value.bandGainsDb)
        }

    @Test
    fun `saveAsNewPreset persists the current state and activates it`() =
        runTest {
            val presetRepository = FakePresetRepository()
            val viewModel = createViewModel(emptyList(), presetRepository = presetRepository)
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.setBandGain(bandIndex = 0, gainDb = 6f)

            viewModel.saveAsNewPreset("My Custom")
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, presetRepository.savedPresets.size)
            val saved = presetRepository.savedPresets.single()
            assertEquals("My Custom", saved.name)
            assertFalse(saved.metadata.builtIn)
            assertEquals(viewModel.activePreset.value.id, saved.id)
            assertFalse(viewModel.isDirty.value)
        }

    @Test
    fun `saveAsNewPreset falls back to the active preset's curve when no bands are known yet`() =
        runTest {
            // PresetJsonSerializer.importFromJson rejects an empty targetCurve - a
            // save made before any session has attached (bands only populate once
            // one does) must not silently produce a preset that then disappears the
            // next time it's read back.
            val emptyCapsEngine = FakeAudioEngine(initialCapabilities = AudioCapabilities())
            val presetRepository = FakePresetRepository()
            val viewModel =
                MainViewModel(
                    repository = FakeAudioEffectRepository(emptyList()),
                    audioEngine = emptyCapsEngine,
                    routeRepository = fakeRouteRepo,
                    diagnosticsRecorder = InMemoryDiagnosticsRecorder(),
                    playerBridge = FakePlayerBridge(),
                    presetRepository = presetRepository,
                    appSettingsRepository = FakeAppSettingsRepository(),
                    correctionProfileRepository = FakeCorrectionProfileRepository(),
                    deviceProfileRepository = FakeDeviceProfileRepository(),
                    backgroundDispatcher = dispatcher,
                )
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.setMacroBass(2f)

            viewModel.saveAsNewPreset("No Bands Yet")
            dispatcher.scheduler.advanceUntilIdle()

            val saved = presetRepository.savedPresets.single()
            assertTrue(saved.targetCurve.isNotEmpty())
            assertEquals(BuiltInPresets.CleanPunch.targetCurve, saved.targetCurve)
        }

    @Test
    fun `duplicatePreset saves a copy with a new id and selects it`() =
        runTest {
            val presetRepository = FakePresetRepository()
            val viewModel = createViewModel(emptyList(), presetRepository = presetRepository)
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.duplicatePreset(BuiltInPresets.DeepRumble)
            dispatcher.scheduler.advanceUntilIdle()

            val copy = presetRepository.savedPresets.single()
            assertFalse(copy.id == BuiltInPresets.DeepRumble.id)
            assertFalse(copy.metadata.builtIn)
            assertEquals(copy.id, viewModel.activePreset.value.id)
        }

    @Test
    fun `renamePreset ignores built-in presets`() =
        runTest {
            val presetRepository = FakePresetRepository()
            val viewModel = createViewModel(emptyList(), presetRepository = presetRepository)
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.renamePreset(BuiltInPresets.DeepRumble, "Hacked Name")
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(presetRepository.savedPresets.isEmpty())
        }

    @Test
    fun `renamePreset updates the active preset when it is the one being renamed`() =
        runTest {
            val custom = customPreset(name = "Old Name")
            val presetRepository = FakePresetRepository(initial = listOf(custom))
            val viewModel = createViewModel(emptyList(), presetRepository = presetRepository)
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.selectPreset(custom)

            viewModel.renamePreset(custom, "New Name")
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals("New Name", viewModel.activePreset.value.name)
        }

    @Test
    fun `requestDeletePreset ignores built-ins and does not set a pending preset`() =
        runTest {
            val viewModel = createViewModel(emptyList())

            viewModel.requestDeletePreset(BuiltInPresets.DeepRumble)

            assertNull(viewModel.pendingDeletePreset.value)
        }

    @Test
    fun `requestDeletePreset then confirmDeletePreset removes a custom preset`() =
        runTest {
            val custom = customPreset(name = "Doomed")
            val presetRepository = FakePresetRepository(initial = listOf(custom))
            val viewModel = createViewModel(emptyList(), presetRepository = presetRepository)
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.requestDeletePreset(custom)
            assertEquals(custom.id, viewModel.pendingDeletePreset.value?.id)

            viewModel.confirmDeletePreset()
            dispatcher.scheduler.advanceUntilIdle()

            assertNull(viewModel.pendingDeletePreset.value)
            assertEquals(listOf(custom.id), presetRepository.deletedIds)
        }

    @Test
    fun `deleting the active custom preset falls back to CleanPunch`() =
        runTest {
            val custom = customPreset(name = "Doomed")
            val presetRepository = FakePresetRepository(initial = listOf(custom))
            val viewModel = createViewModel(emptyList(), presetRepository = presetRepository)
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.selectPreset(custom)

            viewModel.requestDeletePreset(custom)
            viewModel.confirmDeletePreset()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(BuiltInPresets.CleanPunch.id, viewModel.activePreset.value.id)
        }

    @Test
    fun `cancelDeletePreset clears the pending preset without deleting anything`() =
        runTest {
            val custom = customPreset(name = "Safe")
            val presetRepository = FakePresetRepository(initial = listOf(custom))
            val viewModel = createViewModel(emptyList(), presetRepository = presetRepository)
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.requestDeletePreset(custom)

            viewModel.cancelDeletePreset()
            dispatcher.scheduler.advanceUntilIdle()

            assertNull(viewModel.pendingDeletePreset.value)
            assertTrue(presetRepository.deletedIds.isEmpty())
        }

    @Test
    fun `allPresets combines built-ins with custom presets`() =
        runTest {
            val custom = customPreset(name = "Extra")
            val viewModel =
                createViewModel(emptyList(), presetRepository = FakePresetRepository(initial = listOf(custom)))
            dispatcher.scheduler.advanceUntilIdle()

            val ids = viewModel.allPresets.value.map { it.id }
            assertTrue(ids.containsAll(BuiltInPresets.all.map { it.id }))
            assertTrue(ids.contains(custom.id))
        }

    private fun customPreset(name: String): Preset =
        BuiltInPresets.CleanPunch.copy(
            id = UUID.randomUUID().toString(),
            name = name,
            metadata = BuiltInPresets.CleanPunch.metadata.copy(builtIn = false),
        )

    private fun createViewModel(
        descriptors: List<AudioEffectDescriptor>,
        playerBridge: PlayerBridge = FakePlayerBridge(),
        presetRepository: PresetRepository = FakePresetRepository(),
        appSettingsRepository: AppSettingsRepository = FakeAppSettingsRepository(),
        correctionProfileRepository: CorrectionProfileRepository = FakeCorrectionProfileRepository(),
        deviceProfileRepository: DeviceProfileRepository = FakeDeviceProfileRepository(),
    ): MainViewModel =
        MainViewModel(
            repository = FakeAudioEffectRepository(descriptors),
            audioEngine = fakeEngine,
            routeRepository = fakeRouteRepo,
            diagnosticsRecorder = InMemoryDiagnosticsRecorder(),
            playerBridge = playerBridge,
            presetRepository = presetRepository,
            appSettingsRepository = appSettingsRepository,
            correctionProfileRepository = correctionProfileRepository,
            deviceProfileRepository = deviceProfileRepository,
            backgroundDispatcher = dispatcher,
        )

    private fun fakeDescriptor(name: String) =
        AudioEffectDescriptor(
            typeUuid = UUID.randomUUID(),
            effectUuid = UUID.randomUUID(),
            name = name,
            implementor = "Test",
            connectMode = EffectConnectMode.INSERT,
        )

    private class FakeAudioEffectRepository(
        private val descriptors: List<AudioEffectDescriptor>,
    ) : AudioEffectRepository {
        override val knownEffectTypeIds =
            KnownEffectTypeIds(
                equalizer = UUID.randomUUID(),
                dynamicsProcessing = UUID.randomUUID(),
                bassBoost = UUID.randomUUID(),
                loudnessEnhancer = UUID.randomUUID(),
            )

        override fun queryAvailableEffects(): List<AudioEffectDescriptor> = descriptors
    }

    private class FakeAudioRouteRepository : AudioRouteRepository {
        private val _activeRoute = MutableStateFlow(AudioRoute())
        override val activeRoute: StateFlow<AudioRoute> = _activeRoute.asStateFlow()

        override fun startMonitoring() {}

        override fun stopMonitoring() {}
    }

    private class FakePlayerBridge : PlayerBridge {
        var launchCount = 0
            private set
        var stopCount = 0
            private set
        var toggleCount = 0
            private set
        var lastLaunchedSource: PlayerSource? = null
            private set

        override fun launchPlayer(source: PlayerSource) {
            launchCount++
            lastLaunchedSource = source
        }

        override fun stopPlayer() {
            stopCount++
        }

        override fun togglePlayback() {
            toggleCount++
        }
    }

    private class FakePresetRepository(
        initial: List<Preset> = emptyList(),
    ) : PresetRepository {
        private val presets = MutableStateFlow(initial)
        override val customPresets: Flow<List<Preset>> = presets

        val savedPresets = mutableListOf<Preset>()
        val deletedIds = mutableListOf<String>()

        override suspend fun save(preset: Preset) {
            savedPresets.add(preset)
            presets.value = presets.value.filterNot { it.id == preset.id } + preset
        }

        override suspend fun delete(id: String) {
            deletedIds.add(id)
            presets.value = presets.value.filterNot { it.id == id }
        }
    }

    private class FakeAppSettingsRepository(
        initial: LiveSettings? = null,
    ) : AppSettingsRepository {
        private val settings = MutableStateFlow(initial)
        override val liveSettings: Flow<LiveSettings?> = settings

        val savedSettings = mutableListOf<LiveSettings>()

        override suspend fun save(liveSettings: LiveSettings) {
            savedSettings.add(liveSettings)
            settings.value = liveSettings
        }
    }

    private class FakeCorrectionProfileRepository(
        initial: List<CorrectionProfile> = emptyList(),
    ) : CorrectionProfileRepository {
        private val profiles = MutableStateFlow(initial)
        override val customProfiles: Flow<List<CorrectionProfile>> = profiles

        val savedProfiles = mutableListOf<CorrectionProfile>()

        override suspend fun save(profile: CorrectionProfile) {
            savedProfiles.add(profile)
            profiles.value = profiles.value.filterNot { it.id == profile.id } + profile
        }

        override suspend fun delete(id: String) {
            profiles.value = profiles.value.filterNot { it.id == id }
        }
    }

    private class FakeDeviceProfileRepository(
        initial: Map<String, DeviceProfileEntity> = emptyMap(),
    ) : DeviceProfileRepository {
        private val byRoute = initial.toMutableMap()
        private val allFlow = MutableStateFlow(initial.values.toList())
        override val allProfiles: Flow<List<DeviceProfileEntity>> = allFlow

        val savedBindings = mutableListOf<DeviceProfileEntity>()

        override suspend fun getProfileForRoute(routeId: String): DeviceProfileEntity? = byRoute[routeId]

        override suspend fun saveProfile(
            routeId: String,
            routeType: String,
            displayName: String,
            boundPresetId: String,
            boundCorrectionProfileId: String,
        ) {
            val entity =
                DeviceProfileEntity(
                    routeId = routeId,
                    routeType = routeType,
                    displayName = displayName,
                    boundPresetId = boundPresetId,
                    boundCorrectionProfileId = boundCorrectionProfileId,
                )
            byRoute[routeId] = entity
            savedBindings.add(entity)
            allFlow.value = byRoute.values.toList()
        }
    }
}
