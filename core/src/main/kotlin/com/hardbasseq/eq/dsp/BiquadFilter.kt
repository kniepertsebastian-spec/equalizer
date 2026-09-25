package com.hardbasseq.eq.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Normalized biquad coefficients (a0 divided out, so only a1/a2 remain).
data class BiquadCoefficients(
    val b0: Float,
    val b1: Float,
    val b2: Float,
    val a1: Float,
    val a2: Float,
)

// roadmap-2026.md M6 Phase 1: "Referenzimplementierung mit Offline-Testsignalen
// gegen erwartete Frequenzantwort prüfen" / "Latenz, CPU, Akkuverbrauch,
// Stabilität und hörbare Artefakte messen" (three execution paths: Android
// DynamicsProcessing/vendor effects, an owned Media3 DSP path, or a graphic
// approximation on device bands). This file only covers the filter-design half -
// the standard "Audio EQ Cookbook" (Robert Bristow-Johnson, public domain)
// biquad formulas, plus an analytic frequency-response evaluator to check a
// design against its expected response without needing to actually run a signal
// through it. Comparing those three real execution paths on real hardware
// (measured latency/CPU/battery/artifacts) could not be done in this sandbox -
// that's the explicit M6 gap this implementation stops short of, left for the
// device test phase.
object BiquadFilterDesigner {
    // Design blows up towards q=0 (alpha = sin(w0)/(2*Q) grows unbounded) -
    // ParametricFilterBounds.clamp() is expected to have already been applied by
    // the caller, but this is cheap insurance against a hard crash either way.
    private const val MIN_SAFE_Q = 0.0001f

    fun design(
        filter: ParametricFilter,
        sampleRateHz: Float,
    ): BiquadCoefficients {
        val q = filter.q.coerceAtLeast(MIN_SAFE_Q)
        val gainFactorA = 10f.pow(filter.gainDb / 40f)
        val w0 = (2.0 * PI * filter.frequencyHz / sampleRateHz).toFloat()
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2f * q)

        return when (filter.type) {
            ParametricFilterType.PEAK -> {
                val b0 = 1f + alpha * gainFactorA
                val b1 = -2f * cosW0
                val b2 = 1f - alpha * gainFactorA
                val a0 = 1f + alpha / gainFactorA
                val a1 = -2f * cosW0
                val a2 = 1f - alpha / gainFactorA
                normalize(b0, b1, b2, a0, a1, a2)
            }

            ParametricFilterType.LOW_SHELF -> {
                val sqrtA = sqrt(gainFactorA)
                val b0 = gainFactorA * ((gainFactorA + 1f) - (gainFactorA - 1f) * cosW0 + 2f * sqrtA * alpha)
                val b1 = 2f * gainFactorA * ((gainFactorA - 1f) - (gainFactorA + 1f) * cosW0)
                val b2 = gainFactorA * ((gainFactorA + 1f) - (gainFactorA - 1f) * cosW0 - 2f * sqrtA * alpha)
                val a0 = (gainFactorA + 1f) + (gainFactorA - 1f) * cosW0 + 2f * sqrtA * alpha
                val a1 = -2f * ((gainFactorA - 1f) + (gainFactorA + 1f) * cosW0)
                val a2 = (gainFactorA + 1f) + (gainFactorA - 1f) * cosW0 - 2f * sqrtA * alpha
                normalize(b0, b1, b2, a0, a1, a2)
            }

            ParametricFilterType.HIGH_SHELF -> {
                val sqrtA = sqrt(gainFactorA)
                val b0 = gainFactorA * ((gainFactorA + 1f) + (gainFactorA - 1f) * cosW0 + 2f * sqrtA * alpha)
                val b1 = -2f * gainFactorA * ((gainFactorA - 1f) + (gainFactorA + 1f) * cosW0)
                val b2 = gainFactorA * ((gainFactorA + 1f) + (gainFactorA - 1f) * cosW0 - 2f * sqrtA * alpha)
                val a0 = (gainFactorA + 1f) - (gainFactorA - 1f) * cosW0 + 2f * sqrtA * alpha
                val a1 = 2f * ((gainFactorA - 1f) - (gainFactorA + 1f) * cosW0)
                val a2 = (gainFactorA + 1f) - (gainFactorA - 1f) * cosW0 - 2f * sqrtA * alpha
                normalize(b0, b1, b2, a0, a1, a2)
            }

            ParametricFilterType.LOW_PASS -> {
                val b0 = (1f - cosW0) / 2f
                val b1 = 1f - cosW0
                val b2 = (1f - cosW0) / 2f
                val a0 = 1f + alpha
                val a1 = -2f * cosW0
                val a2 = 1f - alpha
                normalize(b0, b1, b2, a0, a1, a2)
            }

            ParametricFilterType.HIGH_PASS -> {
                val b0 = (1f + cosW0) / 2f
                val b1 = -(1f + cosW0)
                val b2 = (1f + cosW0) / 2f
                val a0 = 1f + alpha
                val a1 = -2f * cosW0
                val a2 = 1f - alpha
                normalize(b0, b1, b2, a0, a1, a2)
            }
        }
    }

    private fun normalize(
        b0: Float,
        b1: Float,
        b2: Float,
        a0: Float,
        a1: Float,
        a2: Float,
    ): BiquadCoefficients = BiquadCoefficients(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

    // Analytic |H(e^jw)| in dB at freqHz, evaluated directly from the biquad
    // transfer function rather than by actually filtering a test signal - exact
    // for an ideal biquad (no fixed-point/rounding effects a real DSP path would
    // have), which is exactly what "expected frequency response" means to check
    // a filter design against before it ever touches real audio.
    fun magnitudeResponseDb(
        coefficients: BiquadCoefficients,
        freqHz: Float,
        sampleRateHz: Float,
    ): Float {
        val w = (2.0 * PI * freqHz / sampleRateHz).toFloat()
        val cosW = cos(w)
        val sinW = sin(w)
        val cos2W = cos(2f * w)
        val sin2W = sin(2f * w)

        // e^{-jw} = cos(w) - j*sin(w), so summing b0 + b1*e^{-jw} + b2*e^{-j2w}
        // (and the analogous denominator with a1/a2) splits into these real/imag
        // parts directly.
        val numeratorReal = coefficients.b0 + coefficients.b1 * cosW + coefficients.b2 * cos2W
        val numeratorImag = -(coefficients.b1 * sinW + coefficients.b2 * sin2W)
        val denominatorReal = 1f + coefficients.a1 * cosW + coefficients.a2 * cos2W
        val denominatorImag = -(coefficients.a1 * sinW + coefficients.a2 * sin2W)

        val numeratorMagSq = numeratorReal * numeratorReal + numeratorImag * numeratorImag
        val denominatorMagSq = denominatorReal * denominatorReal + denominatorImag * denominatorImag

        val magnitudeSq = if (denominatorMagSq > 0f) numeratorMagSq / denominatorMagSq else 0f
        return 10f * log10(magnitudeSq.coerceAtLeast(1e-12f))
    }
}
