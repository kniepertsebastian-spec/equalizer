package com.hardbasseq.eq.audio

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAudioEngineTest {
    @Test
    fun attach_transitionsToActiveState() =
        runTest {
            val engine = FakeAudioEngine()
            val session = AudioSession(sessionId = 42, packageName = "test.player")

            val result = engine.attach(session)

            assertTrue(result)
            assertEquals(AudioEngineState.Active(42), engine.state.value)
        }

    @Test
    fun detach_transitionsToListeningState() =
        runTest {
            val engine = FakeAudioEngine()
            val session = AudioSession(sessionId = 42, packageName = "test.player")
            engine.attach(session)

            engine.detach()

            assertEquals(AudioEngineState.Listening, engine.state.value)
        }

    @Test
    fun apply_updatesCurrentSettings() =
        runTest {
            val engine = FakeAudioEngine()
            val settings = ProcessingSettings(masterEnabled = true, bypass = false, inputGainDb = -2f)

            engine.apply(settings)

            assertEquals(settings, engine.currentSettings.value)
        }
}
