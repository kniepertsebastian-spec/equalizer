package com.hardbasseq.eq.audio.spike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogarithmicSweepGeneratorTest {
    @Test
    fun generateLogSweepPcm16_producesExpectedSampleCount() {
        val sampleRate = 44100
        val duration = 1.0
        val pcm =
            LogarithmicSweepGenerator.generateLogSweepPcm16(
                sampleRateHz = sampleRate,
                durationSeconds = duration,
            )

        assertEquals(44100, pcm.size)
        assertTrue(pcm.any { it != 0.toShort() })
    }
}
