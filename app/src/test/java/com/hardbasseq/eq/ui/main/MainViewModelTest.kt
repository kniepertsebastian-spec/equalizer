package com.hardbasseq.eq.ui.main

import com.hardbasseq.eq.audio.AudioEffectDescriptor
import com.hardbasseq.eq.audio.AudioEffectRepository
import com.hardbasseq.eq.audio.EffectConnectMode
import com.hardbasseq.eq.audio.KnownEffectTypeIds
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggling on queries the repository and exposes descriptors`() = runTest {
        val descriptor = fakeDescriptor("Fake Equalizer")
        val viewModel = MainViewModel(FakeAudioEffectRepository(listOf(descriptor)), dispatcher)

        assertFalse(viewModel.showDebugEffects.value)
        assertTrue(viewModel.effectDescriptors.value.isEmpty())

        viewModel.toggleDebugEffects()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.showDebugEffects.value)
        assertEquals(listOf(descriptor), viewModel.effectDescriptors.value)
    }

    @Test
    fun `toggling off hides the list without discarding loaded descriptors`() = runTest {
        val descriptor = fakeDescriptor("Fake Equalizer")
        val viewModel = MainViewModel(FakeAudioEffectRepository(listOf(descriptor)), dispatcher)

        viewModel.toggleDebugEffects()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleDebugEffects()

        assertFalse(viewModel.showDebugEffects.value)
        assertEquals(listOf(descriptor), viewModel.effectDescriptors.value)
    }

    private fun fakeDescriptor(name: String) = AudioEffectDescriptor(
        typeUuid = UUID.randomUUID(),
        effectUuid = UUID.randomUUID(),
        name = name,
        implementor = "Test",
        connectMode = EffectConnectMode.INSERT,
    )

    private class FakeAudioEffectRepository(
        private val descriptors: List<AudioEffectDescriptor>,
    ) : AudioEffectRepository {
        override val knownEffectTypeIds = KnownEffectTypeIds(
            equalizer = UUID.randomUUID(),
            dynamicsProcessing = UUID.randomUUID(),
            bassBoost = UUID.randomUUID(),
            loudnessEnhancer = UUID.randomUUID(),
        )

        override fun queryAvailableEffects(): List<AudioEffectDescriptor> = descriptors
    }
}
