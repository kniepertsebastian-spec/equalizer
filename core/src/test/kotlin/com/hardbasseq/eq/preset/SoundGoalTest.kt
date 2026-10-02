package com.hardbasseq.eq.preset

import com.hardbasseq.eq.audio.EqualizerBandCapabilities
import com.hardbasseq.eq.dsp.EqualizerInterpolator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundGoalTest {
    private fun SoundGoal.gainAt(frequencyHz: Float): Float =
        curve
            .zipWithNext()
            .firstOrNull { (a, b) -> frequencyHz >= a.frequencyHz && frequencyHz <= b.frequencyHz }
            ?.let { (a, b) ->
                val t = (frequencyHz - a.frequencyHz) / (b.frequencyHz - a.frequencyHz)
                a.gainDb + (b.gainDb - a.gainDb) * t
            } ?: 0f

    @Test
    fun balancedChangesNothing() {
        assertTrue(SoundGoal.BALANCED.curve.isEmpty())
    }

    @Test
    fun idsAreUniqueAndRoundTrip() {
        assertEquals(
            SoundGoal.entries.size,
            SoundGoal.entries
                .map { it.id }
                .toSet()
                .size,
        )
        SoundGoal.entries.forEach { assertEquals(it, SoundGoal.fromId(it.id)) }
        assertEquals(SoundGoal.BALANCED, SoundGoal.fromId(null))
        assertEquals(SoundGoal.BALANCED, SoundGoal.fromId("nonsense"))
    }

    @Test
    fun curvesAreSortedAndModest() {
        SoundGoal.entries.forEach { goal ->
            assertEquals(goal.curve.sortedBy { it.frequencyHz }, goal.curve)
            assertTrue(goal.curve.all { it.gainDb in -4f..4f })
        }
    }

    @Test
    fun vocalsForwardLiftsPresenceAndClearsTheMud() {
        assertTrue(SoundGoal.VOCALS_FORWARD.gainAt(3000f) >= 1.5f)
        assertTrue(SoundGoal.VOCALS_FORWARD.gainAt(400f) < 0f)
        // Subtle: nothing that boosts sibilance territory.
        assertTrue(SoundGoal.VOCALS_FORWARD.gainAt(8000f) <= 0.5f)
    }

    @Test
    fun powerAndPunchBoostTheLowEndWhileLessBassCutsIt() {
        assertTrue(SoundGoal.MORE_POWER.gainAt(70f) > 2f)
        assertTrue(SoundGoal.MORE_PUNCH.gainAt(100f) > 2f)
        assertTrue(SoundGoal.LESS_BASS.gainAt(60f) < -2f)
    }

    @Test
    fun lessHarshCutsTheHarshRegion() {
        assertTrue(SoundGoal.LESS_HARSH.gainAt(5000f) < -2f)
    }

    @Test
    fun everyGoalInterpolatesToBandGains() {
        val bands = listOf(60, 230, 910, 3600, 14000).mapIndexed { i, hz -> EqualizerBandCapabilities(i, hz, -15f, 15f) }
        SoundGoal.entries.forEach { goal ->
            assertEquals(bands.size, EqualizerInterpolator.interpolateCurveToBands(goal.curve, bands).size)
        }
    }
}
