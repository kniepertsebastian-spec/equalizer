package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.audio.EqualizerBandCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TEST_Q = 1.4f
private const val SAMPLE_RATE_HZ = 48000f

private fun band(
    index: Int,
    centerFreqHz: Int,
): EqualizerBandCapabilities = EqualizerBandCapabilities(index, centerFreqHz, minGainDb = -15f, maxGainDb = 15f)

class HeadroomCalculatorTest {
    @Test
    fun `all-zero band gains produce no headroom cut`() {
        val bands = listOf(band(0, 100), band(1, 1000))
        val result = HeadroomCalculator.fromCascadedPeakingFilters(mapOf(0 to 0f, 1 to 0f), bands, TEST_Q, SAMPLE_RATE_HZ)

        assertEquals(0f, result.maxPositiveGainDb, 0.01f)
        assertEquals(0f, result.recommendedInputGainDb, 0.01f)
        assertFalse(result.isClippingRisk)
    }

    @Test
    fun `only negative band gains produce no headroom cut`() {
        val bands = listOf(band(0, 100), band(1, 1000))
        val result = HeadroomCalculator.fromCascadedPeakingFilters(mapOf(0 to -4f, 1 to -2f), bands, TEST_Q, SAMPLE_RATE_HZ)

        assertEquals(0f, result.maxPositiveGainDb, 0.01f)
        assertFalse(result.isClippingRisk)
    }

    @Test
    fun `a single boosted band's peak matches its own gain at its center frequency`() {
        val bands = listOf(band(0, 1000))
        val result = HeadroomCalculator.fromCascadedPeakingFilters(mapOf(0 to 6f), bands, TEST_Q, SAMPLE_RATE_HZ)

        // An RBJ peaking filter's magnitude response at its own center frequency
        // is exactly its configured gain, regardless of Q.
        assertEquals(6f, result.maxPositiveGainDb, 0.05f)
        assertEquals(-6f, result.recommendedInputGainDb, 0.05f)
        assertTrue(result.isClippingRisk)
    }

    @Test
    fun `widely separated boosted bands barely overlap so the peak stays close to the highest single band`() {
        val bands = listOf(band(0, 100), band(1, 10000))
        val result = HeadroomCalculator.fromCascadedPeakingFilters(mapOf(0 to 5f, 1 to 5f), bands, TEST_Q, SAMPLE_RATE_HZ)

        assertTrue(result.maxPositiveGainDb < 5.5f)
    }

    @Test
    fun `closely-spaced boosted bands combine to a true peak above any single band's own gain`() {
        // 90 Hz and 125 Hz are two of the six densely-packed bass bands used by
        // the desktop VirtualBands grid - exactly the case that a naive
        // per-band max misses.
        val bands = listOf(band(0, 90), band(1, 125))
        val result = HeadroomCalculator.fromCascadedPeakingFilters(mapOf(0 to 6f, 1 to 6f), bands, TEST_Q, SAMPLE_RATE_HZ)

        assertTrue(
            "expected combined peak (${result.maxPositiveGainDb} dB) to exceed the single-band gain of 6 dB",
            result.maxPositiveGainDb > 6f,
        )
    }

    @Test
    fun `a band missing from the gain map is treated as zero gain and does not affect the peak`() {
        val bands = listOf(band(0, 100), band(1, 1000))
        val result = HeadroomCalculator.fromCascadedPeakingFilters(mapOf(0 to 4f), bands, TEST_Q, SAMPLE_RATE_HZ)

        assertEquals(4f, result.maxPositiveGainDb, 0.05f)
    }
}
