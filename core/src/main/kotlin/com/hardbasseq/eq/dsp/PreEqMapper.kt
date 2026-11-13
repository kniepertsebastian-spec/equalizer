package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint
import kotlin.math.ln
import kotlin.math.pow
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
        return cutoffsHz.mapIndexed { i, upperHz ->
            val lowerHz = if (i == 0) PreEqLayout.LOWEST_HZ else cutoffsHz[i - 1]
            val midHz = sqrt(lowerHz * upperHz)

            val gain =
                if (responseMode.useSimpsonIntegration) {
                    // Simpson integration: smooth, 3-point sampling
                    val lowGain =
                        EqualizerInterpolator.resolveGainAtFrequency(lowerHz, sortedCurve, macros.bassDb, macros.punchDb, macros.haerteDb) +
                            deltaAt(lowerHz, sortedDeltas, responseMode)
                    val midGain =
                        EqualizerInterpolator.resolveGainAtFrequency(midHz, sortedCurve, macros.bassDb, macros.punchDb, macros.haerteDb) +
                            deltaAt(midHz, sortedDeltas, responseMode)
                    val highGain =
                        EqualizerInterpolator.resolveGainAtFrequency(upperHz, sortedCurve, macros.bassDb, macros.punchDb, macros.haerteDb) +
                            deltaAt(upperHz, sortedDeltas, responseMode)
                    (lowGain + 4f * midGain + highGain) / 6f
                } else {
                    // Midpoint sampling: precise, single-point
                    EqualizerInterpolator.resolveGainAtFrequency(midHz, sortedCurve, macros.bassDb, macros.punchDb, macros.haerteDb) +
                        deltaAt(midHz, sortedDeltas, responseMode)
                }

            gain.coerceIn(-PreEqLayout.MAX_GAIN_DB, PreEqLayout.MAX_GAIN_DB)
        }
    }

    // Piecewise linear in log frequency between the delta points, falling to zero
    // based on the response mode's reach distance.
    private fun deltaAt(
        freqHz: Float,
        deltas: List<TargetPoint>,
        responseMode: PreEqResponseMode,
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
