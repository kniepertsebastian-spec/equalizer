package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint
import kotlin.math.sqrt

/**
 * Band layout of the DynamicsProcessing pre-EQ: 34 logarithmically balanced bands
 * (~1/3 octave spacing from 25 Hz to 20 kHz).
 *
 * Acoustic improvement (Priority 2):
 * Previously, 18 of the 34 bands were crammed below 200 Hz (with 5 Hz spacing like
 * 31/36/40/45/50/55 Hz), which is narrower than the physical FFT bin width of Android's
 * DynamicsProcessing STFT engine (~46-93 Hz at 48 kHz), causing phase smearing in the
 * sub-bass while leaving mids/presence (1-12 kHz) under-resolved. The revised 34-band
 * distribution preserves high bass resolution (14 bands <= 250 Hz) without sub-bin
 * crowding, while providing 20 smooth ~1/3-octave bands across mids, presence, and air.
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
}

/** The macro sliders' contribution, kept separate so the mapper stays a pure function. */
data class PreEqMacros(
    val bassDb: Float = 0f,
    val punchDb: Float = 0f,
    val haerteDb: Float = 0f,
)

object PreEqMapper {
    // How far the influence of a lone manual delta reaches, as a frequency ratio (two octaves).
    private const val LONE_DELTA_REACH = 4f

    /**
     * One gain per pre-EQ band: the combined curve (including macros) at the band's
     * logarithmic midpoint, plus the manual band edits, clamped to +-[PreEqLayout.MAX_GAIN_DB].
     *
     * @param manualDeltas how far the user moved a classic band from the curve, as points at
     *   the band centres; they fade out towards their neighbours instead of staying flat.
     */
    fun map(
        curve: List<TargetPoint>,
        macros: PreEqMacros,
        cutoffsHz: List<Float>,
        manualDeltas: List<TargetPoint> = emptyList(),
    ): List<Float> {
        val sortedCurve = curve.sortedBy { it.frequencyHz }
        val sortedDeltas = manualDeltas.sortedBy { it.frequencyHz }
        return cutoffsHz.mapIndexed { i, upperHz ->
            val lowerHz = if (i == 0) PreEqLayout.LOWEST_HZ else cutoffsHz[i - 1]
            val midHz = sqrt(lowerHz * upperHz)
            val gain =
                EqualizerInterpolator.resolveGainAtFrequency(midHz, sortedCurve, macros.bassDb, macros.punchDb, macros.haerteDb) +
                    deltaAt(midHz, sortedDeltas)
            gain.coerceIn(-PreEqLayout.MAX_GAIN_DB, PreEqLayout.MAX_GAIN_DB)
        }
    }

    // Piecewise linear in log frequency between the delta points, falling to zero one
    // neighbour-distance beyond the outermost points.
    private fun deltaAt(
        freqHz: Float,
        deltas: List<TargetPoint>,
    ): Float {
        if (deltas.isEmpty()) return 0f
        val first = deltas.first()
        val last = deltas.last()
        val lowReach = if (deltas.size > 1) first.frequencyHz / deltas[1].frequencyHz else 1f / LONE_DELTA_REACH
        val highReach = if (deltas.size > 1) last.frequencyHz / deltas[deltas.size - 2].frequencyHz else LONE_DELTA_REACH
        val anchors =
            buildList {
                add(TargetPoint(first.frequencyHz * lowReach, 0f))
                addAll(deltas)
                add(TargetPoint(last.frequencyHz * highReach, 0f))
            }
        if (freqHz <= anchors.first().frequencyHz || freqHz >= anchors.last().frequencyHz) return 0f
        return EqualizerInterpolator.interpolateFrequency(freqHz, anchors)
    }
}
