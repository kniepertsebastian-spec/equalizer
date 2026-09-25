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
    fun markDetached_transitionsToDetachedState() =
        runTest {
            val engine = FakeAudioEngine()
            engine.attach(AudioSession(sessionId = 42))

            engine.markDetached()

            assertEquals(AudioEngineState.Detached, engine.state.value)
        }

    @Test
    fun retry_withNoPriorSession_returnsFalse() =
        runTest {
            val engine = FakeAudioEngine()

            assertEquals(false, engine.retry())
        }

    @Test
    fun retry_reattachesLastAttachedSession() =
        runTest {
            val engine = FakeAudioEngine()
            engine.attach(AudioSession(sessionId = 42))

            val result = engine.retry()

            assertTrue(result)
            assertEquals(AudioEngineState.Active(42), engine.state.value)
        }

    @Test
    fun retry_afterCleanDetach_returnsFalse() =
        runTest {
            val engine = FakeAudioEngine()
            engine.attach(AudioSession(sessionId = 42))
            engine.detach()

            // A clean detach means the session ended normally - nothing to retry,
            // unlike reaching Error via a failed Retrying cycle.
            assertEquals(false, engine.retry())
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
