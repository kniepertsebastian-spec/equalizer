package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreEqMapperTest {
    @Test
    fun `34-band layout has exactly 34 strictly increasing cutoffs without sub-bin crowding`() {
        val cutoffs = PreEqLayout.CUTOFFS_HZ
        assertEquals(34, cutoffs.size)

        var previous = PreEqLayout.LOWEST_HZ
        for (cutoff in cutoffs) {
            assertTrue("Expected $cutoff > $previous", cutoff > previous)
            // Neighbouring edges at least ~18 % apart: closer bands share one STFT bin of
            // DynamicsProcessing (about 23 Hz at a 20 ms frame) and smear the bass curve.
            val ratio = cutoff / previous
            assertTrue(
                "Band edge $cutoff Hz is only ${(ratio - 1f) * 100f} % above $previous Hz",
                ratio >= PreEqLayout.MIN_EDGE_RATIO,
            )
            previous = cutoff
        }
    }

    @Test
    fun `layout has no 5 Hz steps in the bass`() {
        val bassEdges = PreEqLayout.CUTOFFS_HZ.filter { it <= 100f }
        bassEdges.zipWithNext().forEach { (a, b) ->
            assertTrue("Edges $a and $b Hz are less than 6 Hz apart", b - a >= 6f)
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
