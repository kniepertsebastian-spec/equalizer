package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint
import kotlinx.serialization.Serializable
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * One free band of the parametric mode (roadmap 06, P5-3): centre frequency, gain and Q are the
 * user's own, unlike the fixed centres of the graphic bands.
 */
@Serializable
data class ParametricBand(
    val frequencyHz: Float,
    val gainDb: Float = 0f,
    val q: Float = 1f,
    val type: ParametricFilterType = ParametricFilterType.PEAK,
    val enabled: Boolean = true,
) {
    /** Inside [ParametricFilterBounds]. Applied to everything that comes from outside (saved settings, files). */
    fun clamped(): ParametricBand {
        val bounded =
            ParametricFilterBounds.clamp(
                ParametricFilter(type = type, frequencyHz = frequencyHz, gainDb = gainDb, q = q),
            )
        return copy(frequencyHz = bounded.frequencyHz, gainDb = bounded.gainDb, q = bounded.q)
    }
}

/**
 * Turns parametric filters into the target-curve form everything else works with, so the parametric
 * mode needs no path of its own through the equalizer: the curve joins the others in
 * `CurveComposer`, and the system equalizer, the pre-EQ and the built-in player all follow it.
 *
 * Through the system effect the curve is only as fine as its bands (a narrow Q is smeared over
 * neighbouring bands there); the built-in player follows it exactly.
 */
object ParametricCurve {
    /** Few enough that the list stays usable on a phone, and each filter evaluated per point stays cheap. */
    const val MAX_BANDS = 8

    private const val SAMPLE_RATE_HZ = 48_000f
    private const val POINTS_PER_OCTAVE = 6
    private const val START_HZ = 20f
    private const val END_HZ = 20_000f

    /** The frequencies the curve is evaluated at: about six per octave from 20 Hz to 20 kHz. */
    val gridHz: List<Float> by lazy {
        val steps = (ln(END_HZ / START_HZ) / ln(2f) * POINTS_PER_OCTAVE).roundToInt()
        (0..steps).map { START_HZ * 2f.pow(it.toFloat() / POINTS_PER_OCTAVE) }
    }

    /** Empty when no filter is active, which `CurveComposer` treats as "no change". */
    fun toCurve(filters: List<ParametricFilter>): List<TargetPoint> {
        val active = filters.filter { !it.bypassed }.map(ParametricFilterBounds::clamp)
        if (active.isEmpty()) return emptyList()
        val coefficients = active.map { BiquadFilterDesigner.design(it, SAMPLE_RATE_HZ) }
        return gridHz.map { freq ->
            val gain = coefficients.sumOf { BiquadFilterDesigner.magnitudeResponseDb(it, freq, SAMPLE_RATE_HZ).toDouble() }
            TargetPoint(freq, gain.toFloat().coerceIn(-24f, 24f))
        }
    }

    fun bandsToCurve(bands: List<ParametricBand>): List<TargetPoint> =
        toCurve(
            bands.take(MAX_BANDS).map {
                ParametricFilter(type = it.type, frequencyHz = it.frequencyHz, gainDb = it.gainDb, q = it.q, bypassed = !it.enabled)
            },
        )
}
