package com.hardbasseq.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TransientShaperTest {
    private val sampleRateHz = 48000f

    @Test
    fun disabled_isBypass() {
        val shaper = TransientShaper(TransientShaperSettings(enabled = false))

        val output = shaper.process(0.5f, sampleRateHz)

        assertEquals(0.5f, output, 0.0001f)
    }

    @Test
    fun zeroPunchAmount_isBypass() {
        val shaper = TransientShaper(TransientShaperSettings(enabled = true, punchAmount = 0f))

        val output = shaper.process(0.5f, sampleRateHz)

        assertEquals(0.5f, output, 0.0001f)
    }

    @Test
    fun sustainedConstantLevel_isNotBoostedOnceBothEnvelopesHaveConverged() {
        // The transition from silence to `level` below IS itself an attack
        // (correctly so - going from silence to a level is a real transient)
        // and gets boosted, same as aSuddenAttack_... below. The point here
        // is what happens *after* both envelopes have caught up to a
        // constant level with no further change: a transient shaper (unlike
        // a plain "boost loud things" effect) must leave already-steady
        // content essentially untouched.
        val shaper = TransientShaper(TransientShaperSettings(enabled = true, punchAmount = 1f))
        val level = 0.4f

        // Let both the fast and slow envelope fully converge on `level` first
        // - the slow envelope's attack time constant is 30ms (1440 samples at
        // 48 kHz), so this needs several thousand samples, not just a few
        // hundred, before the two are actually equal rather than still
        // converging.
        repeat(30000) { shaper.process(level, sampleRateHz) }

        var maxRatio = 0f
        repeat(2000) {
            val output = shaper.process(level, sampleRateHz)
            maxRatio = maxOf(maxRatio, output / level)
        }

        assertTrue("expected no boost once both envelopes have converged, maxRatio=$maxRatio", maxRatio < 1.05f)
    }

    @Test
    fun aSuddenAttack_getsBoostedRightAfterTheStepThenRelaxes() {
        val shaper = TransientShaper(TransientShaperSettings(enabled = true, punchAmount = 1f))
        val sustainedLevel = 0.5f

        // Silence first, so both envelopes start from the same place.
        repeat(1000) { shaper.process(0f, sampleRateHz) }

        // A window after the step wide enough for the fast envelope (1ms
        // attack, ~48-sample time constant) to have risen substantially,
        // but well short of the slow envelope's much longer 30ms/~1440-sample
        // time constant catching up - exactly where the two should diverge
        // the most.
        var maxGainRatio = 0f
        repeat(300) {
            val output = shaper.process(sustainedLevel, sampleRateHz)
            maxGainRatio = maxOf(maxGainRatio, output / sustainedLevel)
        }
        assertTrue("expected a gain boost right after the attack, maxGainRatio=$maxGainRatio", maxGainRatio > 1.05f)

        // Long after the step (well past the slow envelope's ~1440-sample
        // attack time constant), both envelopes have converged on the same
        // sustained level - the transient has "passed", gain should relax
        // back toward unity.
        var settledOutput = 0f
        repeat(30000) { settledOutput = shaper.process(sustainedLevel, sampleRateHz) }
        assertEquals(sustainedLevel, settledOutput, sustainedLevel * 0.05f)
    }

    @Test
    fun negativePunchAmount_softensTheAttackInsteadOfBoostingIt() {
        val sustainedLevel = 0.5f

        val boostingShaper = TransientShaper(TransientShaperSettings(enabled = true, punchAmount = 1f))
        val softeningShaper = TransientShaper(TransientShaperSettings(enabled = true, punchAmount = -1f))
        repeat(1000) {
            boostingShaper.process(0f, sampleRateHz)
            softeningShaper.process(0f, sampleRateHz)
        }

        // Same window as aSuddenAttack_...: far enough into the fast
        // envelope's rise to have actually diverged from the still-lagging
        // slow envelope (a handful of samples isn't enough - the fast
        // envelope itself needs time to move off the silence floor).
        var boostedOutput = 0f
        var softenedOutput = 0f
        repeat(300) {
            boostedOutput = boostingShaper.process(sustainedLevel, sampleRateHz)
            softenedOutput = softeningShaper.process(sustainedLevel, sampleRateHz)
        }

        assertTrue(
            "expected the softening shaper's output ($softenedOutput) to stay below the boosting one's ($boostedOutput)",
            softenedOutput < boostedOutput,
        )
    }

    @Test
    fun output_neverExceedsFullScale() {
        val shaper = TransientShaper(TransientShaperSettings(enabled = true, punchAmount = 1f))
        repeat(1000) { shaper.process(0f, sampleRateHz) }

        repeat(200) {
            val output = shaper.process(1f, sampleRateHz)
            assertTrue(abs(output) <= 1f)
        }
    }

    @Test
    fun settings_clampPunchAmountToSafeRange() {
        val tooHigh = TransientShaperSettings(punchAmount = 5f).clamped()
        assertEquals(1f, tooHigh.punchAmount, 0.001f)

        val tooLow = TransientShaperSettings(punchAmount = -5f).clamped()
        assertEquals(-1f, tooLow.punchAmount, 0.001f)
    }
}
