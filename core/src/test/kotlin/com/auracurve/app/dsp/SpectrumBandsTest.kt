package com.auracurve.app.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumBandsTest {
    /** An FFT of [n] bytes with one bin set to [value] (real part). */
    private fun fftWith(
        n: Int,
        bin: Int,
        value: Int,
    ): ByteArray = ByteArray(n).also { it[2 * bin] = value.toByte() }

    @Test
    fun `edges are log spaced from 40 Hz to 16 kHz`() {
        val e = SpectrumBands.edges()
        assertEquals(33, e.size)
        assertEquals(40f, e.first(), 0.01f)
        assertEquals(16_000f, e.last(), 1f)
        val ratios = e.toList().zipWithNext { a, b -> b / a }
        assertTrue(ratios.all { kotlin.math.abs(it - ratios.first()) < 0.001f })
    }

    @Test
    fun `a tone lights the bar it falls into and no other`() {
        // 1024-byte capture at 48 kHz: 46.875 Hz per bin; bin 21 is about 984 Hz.
        val levels = SpectrumBands.fromFft(fftWith(1024, 21, 127), 48_000)
        val loudest = levels.indices.maxBy { levels[it] }
        val e = SpectrumBands.edges()
        assertTrue("984 Hz in bar $loudest (${e[loudest]}-${e[loudest + 1]})", 984f >= e[loudest] && 984f <= e[loudest + 1])
        assertTrue(levels[loudest] > 95)
        assertEquals(1, levels.count { it > 0 })
    }

    @Test
    fun `silence gives empty bars, odd input gives zeros instead of a crash`() {
        assertTrue(SpectrumBands.fromFft(ByteArray(1024), 48_000).all { it == 0 })
        assertEquals(32, SpectrumBands.fromFft(ByteArray(2), 48_000).size)
        assertTrue(SpectrumBands.fromFft(ByteArray(1024), 0).all { it == 0 })
    }

    @Test
    fun `levels follow a 60 dB scale`() {
        assertEquals(100, SpectrumBands.level(128f))
        assertEquals(50, SpectrumBands.level(128f / 1000f.let { kotlin.math.sqrt(it) }))
        assertEquals(0, SpectrumBands.level(0.1f))
    }
}
