package com.hardbasseq.eq.audio.spike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SineWaveGeneratorTest {

    @Test
    fun `frame count matches sample rate times duration`() {
        val samples = SineWaveGenerator.generateMonoPcm16(
            sampleRateHz = 44_100,
            frequencyHz = 220.0,
            durationSeconds = 1.0,
        )

        assertEquals(44_100, samples.size)
    }

    @Test
    fun `first sample is zero, amplitude stays within requested bound`() {
        val amplitude = 0.2
        val samples = SineWaveGenerator.generateMonoPcm16(
            sampleRateHz = 44_100,
            frequencyHz = 220.0,
            durationSeconds = 1.0,
            amplitude = amplitude,
        )

        assertEquals(0, samples[0].toInt())
        val maxAllowed = (amplitude * Short.MAX_VALUE).toInt() + 1
        samples.forEach { sample ->
            assertTrue(kotlin.math.abs(sample.toInt()) <= maxAllowed)
        }
    }

    @Test
    fun `rejects non-positive sample rate or frequency`() {
        assertThrowsIllegalArgument { SineWaveGenerator.generateMonoPcm16(0, 220.0, 1.0) }
        assertThrowsIllegalArgument { SineWaveGenerator.generateMonoPcm16(44_100, 0.0, 1.0) }
        assertThrowsIllegalArgument { SineWaveGenerator.generateMonoPcm16(44_100, 220.0, 1.0, amplitude = 1.5) }
    }

    @Test
    fun `interleaveStereo duplicates each mono sample into L and R`() {
        val mono = shortArrayOf(1, 2, 3)

        val stereo = SineWaveGenerator.interleaveStereo(mono)

        assertEquals(6, stereo.size)
        assertEquals(shortArrayOf(1, 1, 2, 2, 3, 3).toList(), stereo.toList())
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
