package com.auracurve.app.dsp

import com.auracurve.app.preset.TargetPoint
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Band layout of the DynamicsProcessing pre-EQ: 34 logarithmically spaced bands
 * (about 1/3 octave, 25 Hz to 20 kHz).
 *
 * Why not denser in the bass: Android's DynamicsProcessing works on an STFT whose bin width is
 * roughly 23 Hz at a 20 ms frame (and wider at longer frames). Bands narrower than that - the
 * old 31/36/40/45/50/55 Hz steps of 5 Hz - share a bin, so the bass curve got blurrier than the
 * UI showed. Every band edge here is at least [MIN_EDGE_RATIO] (about 18 %) above the previous
 * one (PreEqMapperTest enforces it), while 14 bands still sit at or below 250 Hz.
 * If a device cannot create this many bands, the engine falls back to the classic equalizer.
 */
object PreEqLayout {
    /** Upper edge of each of the 34 bands; the lower edge of the first band is [LOWEST_HZ]. */
    val CUTOFFS_HZ: List<Float> =
        listOf(
            24f,
            30f,
            37f,
            45f,
            55f,
            67f,
            82f,
            100f,
            122f,
            150f,
            185f,
            225f,
            275f,
            335f,
            410f,
            500f,
            615f,
            750f,
            920f,
            1120f,
            1370f,
            1680f,
            2050f,
            2500f,
            3050f,
            3720f,
            4550f,
            5550f,
            6800f,
            8300f,
            10150f,
            12400f,
            15500f,
            20000f,
        )

    /** The earlier, coarser 16-band layout: tried when a device refuses [CUTOFFS_HZ]. */
    val COARSE_CUTOFFS_HZ: List<Float> =
        listOf(25f, 40f, 55f, 70f, 90f, 115f, 150f, 200f, 280f, 400f, 630f, 1000f, 2000f, 4000f, 8000f, 20000f)

    const val LOWEST_HZ = 18f
    const val MAX_GAIN_DB = 15f

    /** Smallest allowed ratio between neighbouring band edges (about 18 % wider). */
    const val MIN_EDGE_RATIO = 1.18f
}

/** The macro sliders' contribution, kept separate so the mapper stays a pure function. */
data class PreEqMacros(
    val bassDb: Float = 0f,
    val punchDb: Float = 0f,
    val haerteDb: Float = 0f,
)

object PreEqMapper {
    /**
     * One gain per pre-EQ band, with configurable response mode.
     *
     * SMOOTH: Simpson integration (3-point) - smoothest, best anti-aliasing
     * BALANCED: Simpson for curve + tighter manual deltas (1 octave reach)
     * PRECISE: Midpoint sampling - sharpest, most aggressive
     *
     * @param manualDeltas how far the user moved a classic band from the curve, as points at
     *   the band centres; they fade out towards their neighbours instead of staying flat.
     * @param responseMode controls smoothness vs precision (default: BALANCED)
     */
    fun map(
        curve: List<TargetPoint>,
        macros: PreEqMacros,
        cutoffsHz: List<Float>,
        manualDeltas: List<TargetPoint> = emptyList(),
        responseMode: PreEqResponseMode = PreEqResponseMode.DEFAULT,
    ): List<Float> {
        val sortedCurve = curve.sortedBy { it.frequencyHz }
        val sortedDeltas = manualDeltas.sortedBy { it.frequencyHz }

        // The curve, the macros and the manual deltas at one frequency.
        fun gainAt(hz: Float): Float =
            EqualizerInterpolator.resolveGainAtFrequency(hz, sortedCurve, macros.bassDb, macros.punchDb, macros.haerteDb) +
                deltaAt(hz, sortedDeltas, responseMode)
        val gains =
            cutoffsHz.mapIndexed { i, upperHz ->
                val lowerHz = if (i == 0) PreEqLayout.LOWEST_HZ else cutoffsHz[i - 1]
                val midHz = sqrt(lowerHz * upperHz)
                val gain =
                    if (responseMode.useSimpsonIntegration) {
                        // Simpson integration: smooth, 3-point sampling
                        (gainAt(lowerHz) + 4f * gainAt(midHz) + gainAt(upperHz)) / 6f
                    } else {
                        // Midpoint sampling: precise, single-point
                        gainAt(midHz)
                    }
                gain.coerceIn(-PreEqLayout.MAX_GAIN_DB, PreEqLayout.MAX_GAIN_DB)
            }
        // Roadmap 13, P13-3: no steep steps between neighbouring bass bands (STFT pre-ringing).
        return PreEqSlopeLimiter.limit(gains, cutoffsHz)
    }

    /**
     * The manual band deltas at [freqHz]: piecewise linear in log frequency between the delta
     * points, falling to zero based on the response mode's reach distance. [deltas] sorted by
     * frequency. Also used for the own player's curve, so manual sliders sound the same there.
     */
    fun deltaAt(
        freqHz: Float,
        deltas: List<TargetPoint>,
        responseMode: PreEqResponseMode = PreEqResponseMode.DEFAULT,
    ): Float {
        if (deltas.isEmpty()) return 0f
        val first = deltas.first()
        val last = deltas.last()

        // Convert octaves to frequency ratio: 2^octaves
        // e.g., 1 octave = 2x, 0.5 octave = 1.414x
        val reachRatio = 2f.pow(responseMode.manualDeltaReachOctaves)

        val lowReach = if (deltas.size > 1) first.frequencyHz / deltas[1].frequencyHz else 1f / reachRatio
        val highReach = if (deltas.size > 1) last.frequencyHz / deltas[deltas.size - 2].frequencyHz else reachRatio
        val anchors =
            listOf(TargetPoint(first.frequencyHz * lowReach, 0f)) + deltas + TargetPoint(last.frequencyHz * highReach, 0f)
        if (freqHz <= anchors.first().frequencyHz || freqHz >= anchors.last().frequencyHz) return 0f
        for (i in 0 until anchors.size - 1) {
            val a = anchors[i]
            val b = anchors[i + 1]
            if (freqHz in a.frequencyHz..b.frequencyHz) {
                val fraction = ln(freqHz / a.frequencyHz) / ln(b.frequencyHz / a.frequencyHz)
                return a.gainDb + fraction * (b.gainDb - a.gainDb)
            }
        }
        return 0f
    }
}

/**
 * Roadmap 13, P13-3: STFT pre-ringing guard of the pre-EQ's bass.
 *
 * DynamicsProcessing applies the pre-EQ per FFT bin of a 20 ms frame. A large gain step between two
 * neighbouring bass bands is a very steep filter edge in that domain, and its impulse response rings
 * across the whole frame - before the kick as well as after it, heard as a hollow pre-echo. Limiting
 * the step between neighbouring bands below [LIMIT_BELOW_HZ] to [MAX_STEP_DB] (a band there is about a
 * third of an octave) keeps every edge gentle enough: a 2nd-order subsonic high-pass (12 dB/octave,
 * about 3.5 dB per band) passes untouched, a hard manual spike of +12 dB on one band does not.
 */
object PreEqSlopeLimiter {
    const val MAX_STEP_DB = 5f
    const val LIMIT_BELOW_HZ = 250f
    private const val ITERATIONS = 64

    /**
     * [gainsDb] with no step larger than [maxStepDb] between neighbouring bands whose lower band ends
     * at or below [limitBelowHz]. Both sides of a too-steep step move towards each other by the same
     * amount, so a boost is neither only cut down nor only smeared upwards; bands above the limit
     * that are not next to it keep their gain.
     */
    fun limit(
        gainsDb: List<Float>,
        cutoffsHz: List<Float>,
        maxStepDb: Float = MAX_STEP_DB,
        limitBelowHz: Float = LIMIT_BELOW_HZ,
    ): List<Float> {
        require(gainsDb.size == cutoffsHz.size) { "one gain per band" }
        val g = gainsDb.toFloatArray()
        // Pair (i, i + 1) is limited when band i ends in the bass.
        val pairs = (0 until g.size - 1).filter { cutoffsHz[it] <= limitBelowHz }
        if (pairs.isEmpty()) return gainsDb
        // Symmetric relaxation: each too-steep pair gives up half of its excess on either side.
        repeat(ITERATIONS) {
            var changed = false
            for (i in pairs) {
                val excess = abs(g[i + 1] - g[i]) - maxStepDb
                if (excess > 1e-4f) {
                    val half = excess / 2f * sign(g[i + 1] - g[i])
                    g[i] += half
                    g[i + 1] -= half
                    changed = true
                }
            }
            if (!changed) return g.toList()
        }
        // Not settled within the iterations (only for absurd input): a final pass from the top down
        // guarantees the limit, each band clamped against the already final one above it.
        for (i in pairs.reversed()) g[i] = g[i].coerceIn(g[i + 1] - maxStepDb, g[i + 1] + maxStepDb)
        return g.toList()
    }
}

/** The gains of the two EQ stages around the multiband compressor; [post] is empty without a post-EQ. */
data class EqStageGains(
    val pre: List<Float>,
    val post: List<Float>,
)

/**
 * Roadmap 13, P13-2: the `PreEQ -> MBC -> PostEQ` mastering split.
 *
 * Everything used to run in the pre-EQ, ahead of the multiband compressor: a +12 dB bass boost drove
 * the compressor straight into heavy gain reduction, and a treble "air" lift was flattened by it
 * again. The split moves part of the positive tone shaping behind the compressor:
 *
 * - air: boosts from [AIR_FADE_FROM_HZ] up, fully from [AIR_FULL_FROM_HZ], at most [MAX_AIR_POST_DB];
 * - bass: [BASS_SHARE] of a boost up to [BASS_FULL_UP_TO_HZ], fading out by [BASS_FADE_TO_HZ], at most
 *   [MAX_BASS_POST_DB] - the compressor still sees most of the bass, so it keeps catching the kicks.
 *
 * Cuts (subsonic high-pass, mud dip, headphone correction dips) and the midrange stay in the pre-EQ,
 * where they clean the signal before it is compressed. The post-EQ uses the pre-EQ's own band layout,
 * so for every band `pre + post` is exactly the gain the single pre-EQ had before: the frequency
 * response and the headroom the input gain reserves for it are unchanged, only the order around the
 * compressor differs.
 */
object PostEqSplit {
    const val BASS_SHARE = 0.35f
    const val BASS_FULL_UP_TO_HZ = 150f
    const val BASS_FADE_TO_HZ = 250f
    const val MAX_BASS_POST_DB = 4f
    const val AIR_FADE_FROM_HZ = 6000f
    const val AIR_FULL_FROM_HZ = 10000f
    const val MAX_AIR_POST_DB = 6f

    fun split(
        gainsDb: List<Float>,
        cutoffsHz: List<Float>,
    ): EqStageGains {
        require(gainsDb.size == cutoffsHz.size) { "one gain per band" }
        val post =
            gainsDb.mapIndexed { i, gain ->
                val lowerHz = if (i == 0) PreEqLayout.LOWEST_HZ else cutoffsHz[i - 1]
                val centerHz = sqrt(lowerHz * cutoffsHz[i])
                val boost = gain.coerceAtLeast(0f)
                val bass = (boost * BASS_SHARE * bassWeight(centerHz)).coerceAtMost(MAX_BASS_POST_DB)
                val air = (boost * airWeight(centerHz)).coerceAtMost(MAX_AIR_POST_DB)
                // Never more than the band's own boost (bass and air never overlap anyway).
                (bass + air).coerceAtMost(boost)
            }
        return EqStageGains(pre = gainsDb.zip(post) { gain, p -> gain - p }, post = post)
    }

    private fun bassWeight(hz: Float): Float =
        when {
            hz <= BASS_FULL_UP_TO_HZ -> 1f
            hz >= BASS_FADE_TO_HZ -> 0f
            else -> 1f - ln(hz / BASS_FULL_UP_TO_HZ) / ln(BASS_FADE_TO_HZ / BASS_FULL_UP_TO_HZ)
        }

    private fun airWeight(hz: Float): Float =
        when {
            hz <= AIR_FADE_FROM_HZ -> 0f
            hz >= AIR_FULL_FROM_HZ -> 1f
            else -> ln(hz / AIR_FADE_FROM_HZ) / ln(AIR_FULL_FROM_HZ / AIR_FADE_FROM_HZ)
        }
}
