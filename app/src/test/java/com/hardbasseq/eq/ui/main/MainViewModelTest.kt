package com.hardbasseq.eq.ui.main

import com.hardbasseq.eq.audio.AudioEffectDescriptor
import com.hardbasseq.eq.audio.AudioEffectRepository
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.audio.AudioRouteRepository
import com.hardbasseq.eq.audio.EffectConnectMode
import com.hardbasseq.eq.audio.FakeAudioEngine
import com.hardbasseq.eq.audio.KnownEffectTypeIds
import com.hardbasseq.eq.diagnostics.InMemoryDiagnosticsRecorder
import com.hardbasseq.eq.preset.BuiltInPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
            val peakBoostDb =
                viewModel.processingSettings.value.bandGainsDb.values
                    .maxOrNull()
                    ?.coerceAtLeast(0f) ?: 0f
            val expectedInputGainDb = -maxOf(BuiltInPresets.DeepRumble.requestedHeadroomDb, peakBoostDb)
            assertEquals(expectedInputGainDb, viewModel.processingSettings.value.inputGainDb)
            assertEquals(BuiltInPresets.DeepRumble.mbcThresholdDb, viewModel.processingSettings.value.mbcThresholdDb)
            assertEquals(BuiltInPresets.DeepRumble.mbcRatio, viewModel.processingSettings.value.mbcRatio)
            assertTrue(viewModel.processingSettings.value.mbcEnabled)
            assertTrue(
                viewModel.processingSettings.value.bandGainsDb
                    .isNotEmpty(),
            )
        }

    @Test
    fun `manual boost automatically reserves matching headroom`() =
        runTest {
            val viewModel = createViewModel(emptyList())
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.setBandGain(bandIndex = 0, gainDb = 8f)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(-8f, viewModel.processingSettings.value.inputGainDb)
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

    private fun createViewModel(descriptors: List<AudioEffectDescriptor>): MainViewModel =
        MainViewModel(
            repository = FakeAudioEffectRepository(descriptors),
            audioEngine = fakeEngine,
            routeRepository = fakeRouteRepo,
            diagnosticsRecorder = InMemoryDiagnosticsRecorder(),
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
}
