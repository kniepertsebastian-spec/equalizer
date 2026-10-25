package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Band layout of the DynamicsProcessing pre-EQ: dense in the bass, where kick, cabin
 * resonance, subsonic cut and the virtual-bass approximation live, coarse above.
 */
object PreEqLayout {
    /** Upper edge of each band; the lower edge of the first band is [LOWEST_HZ]. */
    val CUTOFFS_HZ: List<Float> =
        listOf(25f, 40f, 55f, 70f, 90f, 115f, 150f, 200f, 280f, 400f, 630f, 1000f, 2000f, 4000f, 8000f, 20000f)

    const val LOWEST_HZ = 15f
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
