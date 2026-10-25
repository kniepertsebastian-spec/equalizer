package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.BuiltInContextPresets
import com.hardbasseq.eq.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PreEqMapperTest {
    private val cutoffs = PreEqLayout.CUTOFFS_HZ

    private fun map(
        curve: List<TargetPoint>,
        deltas: List<TargetPoint> = emptyList(),
    ) = PreEqMapper.map(curve, PreEqMacros(), cutoffs, deltas)

    // The band that contains `hz`.
    private fun bandIndexOf(hz: Float): Int = cutoffs.indexOfFirst { hz <= it }

    @Test
    fun `a flat curve gives zero in every band`() {
        val gains = map(emptyList())
        assertEquals(cutoffs.size, gains.size)
        gains.forEach { assertEquals(0f, it, 0f) }
    }

    @Test
    fun `context curves land close to the target in the bass and low mids`() {
        listOf(BuiltInContextPresets.Car, BuiltInContextPresets.BluetoothSpeaker).forEach { preset ->
            val gains = map(preset.targetCurve)
            val sorted = preset.targetCurve.sortedBy { it.frequencyHz }
            listOf(75f, 100f, 120f, 200f).forEach { hz ->
                val target = EqualizerInterpolator.interpolateFrequency(hz, sorted)
                // A band is one step over its whole range, so it can only approximate the
                // slope inside it; 1 dB is far finer than the 5-band system equalizer.
                assertEquals("${preset.id} at $hz Hz", target, gains[bandIndexOf(hz)], 1.0f)
            }
        }
    }

    @Test
    fun `gains are clamped to plus minus 15 dB`() {
        val gains = map(listOf(TargetPoint(100f, 40f), TargetPoint(5000f, -40f)))
        assertEquals(PreEqLayout.MAX_GAIN_DB, gains.max(), 0f)
        assertEquals(-PreEqLayout.MAX_GAIN_DB, gains.min(), 0f)
    }

    @Test
    fun `a manual delta at the 60 Hz band lifts the bands from 40 to 90 Hz`() {
        val flat = map(emptyList())
        val lifted = map(emptyList(), listOf(TargetPoint(60f, 3f)))
        listOf(55f, 70f, 90f).forEach { upperHz ->
            val i = cutoffs.indexOf(upperHz)
            assertTrue("band up to $upperHz Hz", lifted[i] > flat[i])
        }
        assertEquals(0f, lifted[cutoffs.indexOf(1000f)], 0f)
        assertTrue(lifted.max() <= 3f + 0.001f)
    }

    @Test
    fun `macros are included in the mapped gains`() {
        val withBass = PreEqMapper.map(emptyList(), PreEqMacros(bassDb = 2f), cutoffs)
        assertEquals(2f, withBass[bandIndexOf(60f)], 0.001f)
        assertTrue(abs(withBass[bandIndexOf(1000f)]) < 0.001f)
    }
}
