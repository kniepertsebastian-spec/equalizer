package com.auracurve.app.dsp

import com.auracurve.app.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sqrt

// Roadmap 13: the pre-ringing guard of the pre-EQ's bass (P13-3) and the PreEQ/PostEQ split (P13-2).
class PreEqSlopeAndSplitTest {
    private val cutoffs = PreEqLayout.CUTOFFS_HZ

    // +12 dB bass, a -3 dB mud dip, +3 dB air.
    private val bassMudAndAir =
        cutoffs.map { hz ->
            when {
                hz < 150f -> 12f
                hz > 9000f -> 3f
                hz in 200f..320f -> -3f
                else -> 0f
            }
        }

    private fun maxBassStep(gains: List<Float>): Float =
        (0 until gains.size - 1)
            .filter { cutoffs[it] <= PreEqSlopeLimiter.LIMIT_BELOW_HZ }
            .maxOf { abs(gains[it + 1] - gains[it]) }

    @Test
    fun `a sharp manual bass spike in PRECISE mode stays within the step limit`() {
        // +12 dB on a single classic band at 60 Hz, the steepest the user can make it.
        val gains =
            PreEqMapper.map(
                curve = emptyList(),
                macros = PreEqMacros(),
                cutoffsHz = cutoffs,
                manualDeltas = listOf(TargetPoint(60f, 12f)),
                responseMode = PreEqResponseMode.PRECISE,
            )
        assertTrue("step ${maxBassStep(gains)}", maxBassStep(gains) <= PreEqSlopeLimiter.MAX_STEP_DB + 1e-3f)
        // Still a clear boost where the user asked for it.
        assertTrue(gains.max() > 8f)
    }

    @Test
    fun `a 12 dB per octave subsonic high-pass passes the limiter untouched`() {
        // The asymptote of a 2nd-order high-pass at its highest corner (80 Hz), per band centre.
        val slope =
            cutoffs.mapIndexed { i, upper ->
                val lower = if (i == 0) PreEqLayout.LOWEST_HZ else cutoffs[i - 1]
                val center = sqrt(lower * upper)
                if (center < 80f) (-12.0 * log2(80.0 / center)).toFloat().coerceAtLeast(-PreEqLayout.MAX_GAIN_DB) else 0f
            }
        assertEquals(slope, PreEqSlopeLimiter.limit(slope, cutoffs))
    }

    @Test
    fun `the limiter moves both sides of a step and leaves the midrange alone`() {
        val gains = cutoffs.map { if (it <= 82f) 12f else 0f }
        val limited = PreEqSlopeLimiter.limit(gains, cutoffs)
        assertTrue(maxBassStep(limited) <= PreEqSlopeLimiter.MAX_STEP_DB + 1e-3f)
        val edge = cutoffs.indexOf(82f)
        // The boost side came down a little, the flat side came up a little.
        assertTrue(limited[edge] < 12f && limited[edge + 1] > 0f)
        // Deep bass keeps its full boost, everything from 500 Hz up is untouched.
        assertEquals(12f, limited[0], 1e-3f)
        cutoffs.indices.filter { cutoffs[it] >= 500f }.forEach { assertEquals(0f, limited[it], 1e-6f) }
    }

    @Test
    fun `smooth curves are not changed by the limiter`() {
        val gains = cutoffs.map { if (it < 200f) 4f else 0f }.let { PreEqSlopeLimiter.limit(it, cutoffs) }
        assertEquals(gains, PreEqSlopeLimiter.limit(gains, cutoffs))
        val flat = cutoffs.map { 0f }
        assertEquals(flat, PreEqSlopeLimiter.limit(flat, cutoffs))
    }

    @Test
    fun `pre plus post is exactly the single pre-EQ gain on every band`() {
        val gains = bassMudAndAir
        val split = PostEqSplit.split(gains, cutoffs)
        assertEquals(gains.size, split.pre.size)
        assertEquals(gains.size, split.post.size)
        gains.indices.forEach { assertEquals(gains[it], split.pre[it] + split.post[it], 1e-5f) }
    }

    @Test
    fun `the post-EQ only ever boosts, takes a capped share of the bass and the air`() {
        val gains = bassMudAndAir
        val split = PostEqSplit.split(gains, cutoffs)
        assertTrue(split.post.all { it >= 0f })
        val bassBand = cutoffs.indexOf(82f)
        // 35 % of 12 dB = 4.2 dB, capped at 4 dB: the compressor still sees the larger part of the bass.
        assertEquals(PostEqSplit.MAX_BASS_POST_DB, split.post[bassBand], 1e-4f)
        assertEquals(12f - PostEqSplit.MAX_BASS_POST_DB, split.pre[bassBand], 1e-4f)
        // The air lift sits behind the compressor completely.
        val airBand = cutoffs.indexOf(15500f)
        assertEquals(3f, split.post[airBand], 1e-4f)
        assertEquals(0f, split.pre[airBand], 1e-4f)
        // The mud dip (a cut) and the mids stay in the pre-EQ.
        cutoffs.indices.filter { cutoffs[it] in 225f..5000f }.forEach { assertEquals(0f, split.post[it], 1e-6f) }
    }

    @Test
    fun `a flat or cutting curve leaves the post-EQ flat`() {
        val cuts = cutoffs.map { -2f }
        val split = PostEqSplit.split(cuts, cutoffs)
        assertTrue(split.post.all { it == 0f })
        assertEquals(cuts, split.pre)
    }
}
