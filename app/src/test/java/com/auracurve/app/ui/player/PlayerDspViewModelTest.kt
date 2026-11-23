package com.auracurve.app.ui.player

import com.auracurve.app.dsp.BassMonoSummerSettings
import com.auracurve.app.player.playback.InMemoryPlayerDspStore
import com.auracurve.app.player.playback.PlayerDspState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

// Roadmap 11 P11-4: the view model works on the store it is given, not on PlayerDspState.
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerDspViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun switchesGoToTheGivenStore_andLeaveTheSharedPlayerStateAlone() {
        val shared = PlayerDspState.settings.value
        val store = InMemoryPlayerDspStore()
        val viewModel = PlayerDspViewModel(store)

        viewModel.setMonoBass(false)
        viewModel.setLimiter(false)
        viewModel.setMonoBassCutoff(10_000f)

        assertFalse(viewModel.settings.value.monoBassEnabled)
        assertFalse(store.currentSettings.limiterEnabled)
        assertEquals(BassMonoSummerSettings.MAX_CUTOFF_HZ, store.currentSettings.monoBassCutoffHz, 0f)
        assertEquals(shared, PlayerDspState.settings.value)
    }
}
