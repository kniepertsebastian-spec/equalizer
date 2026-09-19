package com.hardbasseq.eq.ui.main

import com.hardbasseq.eq.audio.AudioEffectDescriptor
import com.hardbasseq.eq.audio.AudioEffectRepository
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.audio.AudioRouteRepository
import com.hardbasseq.eq.audio.AudioSession
import com.hardbasseq.eq.audio.AudioSessionRepository
import com.hardbasseq.eq.audio.EffectConnectMode
import com.hardbasseq.eq.audio.FakeAudioEngine
import com.hardbasseq.eq.audio.KnownEffectTypeIds
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
    private val fakeSessionRepo = FakeAudioSessionRepository()
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
            assertTrue(
                viewModel.processingSettings.value.bandGainsDb
                    .isNotEmpty(),
            )
        }

    private fun createViewModel(descriptors: List<AudioEffectDescriptor>): MainViewModel =
        MainViewModel(
            repository = FakeAudioEffectRepository(descriptors),
            audioEngine = fakeEngine,
            sessionRepository = fakeSessionRepo,
            routeRepository = fakeRouteRepo,
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

    private class FakeAudioSessionRepository : AudioSessionRepository {
        private val _sessions = MutableStateFlow<List<AudioSession>>(emptyList())
        override val sessions: StateFlow<List<AudioSession>> = _sessions.asStateFlow()

        private val _activeSession = MutableStateFlow<AudioSession?>(null)
        override val activeSession: StateFlow<AudioSession?> = _activeSession.asStateFlow()

        override fun startListening() {}

        override fun stopListening() {}

        override fun setActiveSession(session: AudioSession?) {
            _activeSession.value = session
        }
    }

    private class FakeAudioRouteRepository : AudioRouteRepository {
        private val _activeRoute = MutableStateFlow(AudioRoute())
        override val activeRoute: StateFlow<AudioRoute> = _activeRoute.asStateFlow()

        override fun startMonitoring() {}

        override fun stopMonitoring() {}
    }
}
