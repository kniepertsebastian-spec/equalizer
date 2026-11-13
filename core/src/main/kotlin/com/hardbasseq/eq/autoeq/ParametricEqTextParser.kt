package com.hardbasseq.eq.autoeq

import com.hardbasseq.eq.dsp.BiquadFilterDesigner
import com.hardbasseq.eq.dsp.ParametricFilter
import com.hardbasseq.eq.dsp.ParametricFilterBounds
import com.hardbasseq.eq.dsp.ParametricFilterType
import com.hardbasseq.eq.preset.TargetPoint
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * AutoEq's `ParametricEQ.txt` / Equalizer APO style lists, e.g.
 *
 * ```
 * Preamp: -6.2 dB
 * Filter 1: ON PK Fc 31 Hz Gain 6.3 dB Q 1.10
 * Filter 2: ON LSC Fc 105 Hz Gain -2.0 dB Q 0.70
 * ```
 *
 * The filters are evaluated analytically into a plain frequency/gain curve, because that is what a
 * correction profile stores. The `Preamp` line is dropped on purpose: the app derives its own
 * headroom from the resulting curve (see HeadroomCalculator), and applying both would count the
 * same safety margin twice.
 */
object ParametricEqTextParser {
    private const val SAMPLE_RATE_HZ = 48_000f
    private const val POINTS_PER_OCTAVE = 6
    private const val START_HZ = 20f
    private const val END_HZ = 20_000f

    private val filterLine =
        Regex(
            """^\s*Filter\s*\d*\s*:\s*(ON|OFF)\s+(\w+)\s+Fc\s+([-+0-9.eE]+)\s*(?:Hz)?(?:\s+Gain\s+([-+0-9.eE]+)\s*(?:dB)?)?(?:\s+Q\s+([-+0-9.eE]+))?""",
            RegexOption.IGNORE_CASE,
        )

    fun looksParametric(text: String): Boolean = text.lineSequence().any { filterLine.containsMatchIn(it) }

    /** Null if the text has no usable filter line. */
    fun parse(text: String): List<TargetPoint>? {
        val filters =
            text
                .lineSequence()
                .mapNotNull(::parseFilter)
                .toList()
        if (filters.isEmpty()) return null

        val octaves = kotlin.math.ln(END_HZ / START_HZ) / kotlin.math.ln(2f)
        val steps = (octaves * POINTS_PER_OCTAVE).roundToInt()
        val coefficients = filters.map { BiquadFilterDesigner.design(it, SAMPLE_RATE_HZ) }
        return (0..steps).map { step ->
            val freq = START_HZ * 2f.pow(step.toFloat() / POINTS_PER_OCTAVE)
            val gain = coefficients.sumOf { BiquadFilterDesigner.magnitudeResponseDb(it, freq, SAMPLE_RATE_HZ).toDouble() }
            TargetPoint(freq, gain.toFloat().coerceIn(-24f, 24f))
        }
    }

    private fun parseFilter(line: String): ParametricFilter? {
        val match = filterLine.find(line) ?: return null
        val (state, kind, fc, gain, q) = match.destructured
        if (state.equals("OFF", ignoreCase = true)) return null
        val type =
            when (kind.uppercase()) {
                "PK", "PEQ" -> ParametricFilterType.PEAK
                "LSC", "LS" -> ParametricFilterType.LOW_SHELF
                "HSC", "HS" -> ParametricFilterType.HIGH_SHELF
                "LP", "LPQ" -> ParametricFilterType.LOW_PASS
                "HP", "HPQ" -> ParametricFilterType.HIGH_PASS
                else -> return null
            }
        val frequency = fc.toFloatOrNull() ?: return null
        if (frequency <= 0f) return null
        return ParametricFilterBounds.clamp(
            ParametricFilter(
                type = type,
                frequencyHz = frequency,
                gainDb = gain.toFloatOrNull() ?: 0f,
                // Equalizer APO's default when the Q is left out.
                q = q.toFloatOrNull() ?: 0.707f,
            ),
        )
    }
}
