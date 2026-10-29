package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreEqMapperTest {
    @Test
    fun `34-band layout has exactly 34 strictly increasing cutoffs`() {
        val cutoffs = PreEqLayout.CUTOFFS_HZ
        assertEquals(34, cutoffs.size)

        var previous = PreEqLayout.LOWEST_HZ
        for (cutoff in cutoffs) {
            assertTrue("Expected $cutoff > $previous", cutoff > previous)
            previous = cutoff
        }
    }

    @Test
    fun `flat curve with zero macros produces strictly zero gain across all 34 bands`() {
        val gains =
            PreEqMapper.map(
                curve = emptyList(),
                macros = PreEqMacros(0f, 0f, 0f),
                cutoffsHz = PreEqLayout.CUTOFFS_HZ,
            )

        assertEquals(34, gains.size)
        gains.forEachIndexed { index, gainDb ->
            assertEquals("Band $index should be 0 dB", 0f, gainDb, 0.0001f)
        }
    }

    @Test
    fun `manual delta interpolates smoothly and decays to zero outside neighbor reach`() {
        val deltas =
            listOf(
                TargetPoint(60f, 0f),
                TargetPoint(1000f, 6f),
                TargetPoint(14000f, 0f),
            )
        val gains =
            PreEqMapper.map(
                curve = emptyList(),
                macros = PreEqMacros(),
                cutoffsHz = PreEqLayout.CUTOFFS_HZ,
                manualDeltas = deltas,
            )

        // Deep sub-bass (below 60 Hz) must stay untouched (0 dB)
        assertEquals(0f, gains.first(), 0.01f)
        // Band around 1 kHz must reflect the positive delta
        val midBandIndex = PreEqLayout.CUTOFFS_HZ.indexOfFirst { it >= 1000f }
        assertTrue(gains[midBandIndex] > 4.5f)
    }
}
