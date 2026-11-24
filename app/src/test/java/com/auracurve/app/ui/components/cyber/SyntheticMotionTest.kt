package com.auracurve.app.ui.components.cyber

import com.auracurve.app.preset.TargetPoint
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticMotionTest {
    private val flat = listOf(TargetPoint(60f, 0f), TargetPoint(1000f, 0f), TargetPoint(14_000f, 0f))
    private val bassBoost = listOf(TargetPoint(60f, 6f), TargetPoint(1000f, 0f), TargetPoint(14_000f, 0f))

    @Test
    fun `bars sit on the log axis from 20 Hz to 20 kHz`() {
        val spectrum = SyntheticSpectrum()
        assertEquals(48, spectrum.frequencies.size)
        assertTrue(spectrum.frequencies.first() > 20f)
        assertTrue(spectrum.frequencies.last() < 20_000f)
        for (i in 1 until spectrum.barCount) {
            assertTrue(spectrum.frequencies[i] > spectrum.frequencies[i - 1])
        }
    }

    @Test
    fun `a boost lifts its bars at rest`() {
        val spectrum = SyntheticSpectrum()
        val flatRest = spectrum.rest(flat, 12f)
        val boostedRest = spectrum.rest(bassBoost, 12f)
        val bassBar = spectrum.frequencies.indexOfFirst { it >= 60f }
        assertTrue(boostedRest[bassBar] > flatRest[bassBar])
        val trebleBar = spectrum.barCount - 2
        assertEquals(flatRest[trebleBar], boostedRest[trebleBar], 1e-4f)
    }

    @Test
    fun `bars stay inside 0 to 1 at any time and gain`() {
        val spectrum = SyntheticSpectrum()
        val loud = List(10) { TargetPoint(20f * (it + 1) * (it + 1) * 10f, 12f) }
        val rest = spectrum.rest(loud, 12f)
        val out = FloatArray(spectrum.barCount)
        listOf(0f, 0.1f, 0.4f, 1f, 12.3f, 600f).forEach { t ->
            spectrum.animate(rest, t, out)
            out.forEach { assertTrue(it in 0f..1f) }
        }
    }

    @Test
    fun `the same seed and time give the same frame`() {
        val a = SyntheticSpectrum()
        val b = SyntheticSpectrum()
        val rest = a.rest(bassBoost, 12f)
        val outA = FloatArray(a.barCount)
        val outB = FloatArray(b.barCount)
        a.animate(rest, 3.7f, outA)
        b.animate(rest, 3.7f, outB)
        assertArrayEquals(outA, outB, 0f)
        a.animate(rest, 3.75f, outB)
        assertFalse(outA.contentEquals(outB))
    }

    @Test
    fun `rays are seeded per track and bounded`() {
        val one = FloatArray(96)
        val same = FloatArray(96)
        val other = FloatArray(96)
        SyntheticRays(seed = 42L).lengths(1.2f, one)
        SyntheticRays(seed = 42L).lengths(1.2f, same)
        SyntheticRays(seed = 43L).lengths(1.2f, other)
        assertArrayEquals(one, same, 0f)
        assertFalse(one.contentEquals(other))
        one.forEach { assertTrue(it in 0f..1f) }
    }

    @Test
    fun `waveform is deterministic per track and bounded`() {
        val seed = WaveformShape.seedOf("track-1")
        assertEquals(seed, WaveformShape.seedOf("track-1"))
        val a = WaveformShape.amplitudes(seed, 120)
        assertArrayEquals(a, WaveformShape.amplitudes(seed, 120), 0f)
        assertFalse(a.contentEquals(WaveformShape.amplitudes(WaveformShape.seedOf("track-2"), 120)))
        assertEquals(120, a.size)
        a.forEach { assertTrue(it in 0.08f..1f) }
        // Fades in: the first sample is quieter than the loudest part.
        assertTrue(a.first() < a.max())
        assertEquals(0, WaveformShape.amplitudes(seed, 0).size)
    }
}
