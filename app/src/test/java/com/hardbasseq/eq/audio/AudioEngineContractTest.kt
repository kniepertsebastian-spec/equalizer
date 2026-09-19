package com.hardbasseq.eq.audio

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEngineContractTest {

    @Test
    fun contract_initialStateIsDetached() = runTest {
        val engine: AudioEngine = FakeAudioEngine()
        assertEquals(AudioEngineState.Detached, engine.state.value)
    }

    @Test
    fun contract_attachingSessionTransitionsToActive() = runTest {
        val engine: AudioEngine = FakeAudioEngine()
        val session = AudioSession(sessionId = 101, packageName = "org.test.music")

        val success = engine.attach(session)

        assertTrue(success)
        assertEquals(AudioEngineState.Active(101), engine.state.value)
    }

    @Test
    fun contract_detachingActiveEngineReturnsToDetached() = runTest {
        val engine: AudioEngine = FakeAudioEngine()
        engine.attach(AudioSession(sessionId = 101))

        engine.detach()

        assertEquals(AudioEngineState.Detached, engine.state.value)
    }
}
