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

// Everything above only designs coefficients and evaluates the resulting
// transfer function analytically - useful to check a filter design offline,
// but nothing there actually runs a signal through a filter. A biquad is an
// IIR filter: computing sample n needs the previous two input and output
// samples, so applying it correctly across a stream of process() calls (one
// call per sample, as BassExciter does) requires carrying that state between
// calls - a fresh, state-free filter on every call would click/pop at every
// buffer boundary. Direct Form I, per the same RBJ cookbook the coefficients
// above come from. The state and the arithmetic run in double precision: the
// bass filters (31 Hz peak, subsonic high-pass) have poles right next to z = 1,
// where single-precision rounding turns into audible noise and gain error.
class BiquadFilterState {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(
        input: Float,
        coefficients: BiquadCoefficients,
    ): Float {
        val x0 = input.toDouble()
        val output =
            coefficients.b0 * x0 + coefficients.b1 * x1 + coefficients.b2 * x2 -
                coefficients.a1 * y1 - coefficients.a2 * y2
        x2 = x1
        x1 = x0
        y2 = y1
        y1 = output
        return output.toFloat()
    }

    /**
     * The first [count] samples of [samples] through the filter, in place: the same arithmetic as
     * [process], one sample after the other, and the same state - so both can be mixed.
     *
     * This is what the player's audio thread uses. Over a whole block the coefficients and the
     * state stay in local variables and the loop touches one array only; called sample by sample
     * through a chain of lists, the same filter cost several times as much - too much for the slow
     * cores the phone leaves a player on once the screen is locked (the music stuttered).
     */
    fun processBlock(
        samples: DoubleArray,
        count: Int,
        coefficients: BiquadCoefficients,
    ) {
        val b0 = coefficients.b0.toDouble()
        val b1 = coefficients.b1.toDouble()
        val b2 = coefficients.b2.toDouble()
        val a1 = coefficients.a1.toDouble()
        val a2 = coefficients.a2.toDouble()
        var x1 = this.x1
        var x2 = this.x2
        var y1 = this.y1
        var y2 = this.y2
        for (n in 0 until count) {
            val x0 = samples[n]
            val output = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = output
            samples[n] = output
        }
        this.x1 = x1
        this.x2 = x2
        this.y1 = y1
        this.y2 = y2
    }

    fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }

    companion object {
        /**
         * Two channels through the same filter in one pass: [first] filters [firstSamples],
         * [second] filters [secondSamples], each exactly as [processBlock] would on its own.
         *
         * A recursive filter has to wait for its own previous output, so one channel alone
         * leaves the processor idle part of the time; the two channels do not depend on each
         * other and fill those gaps.
         */
        fun processBlockPair(
            first: BiquadFilterState,
            firstSamples: DoubleArray,
            second: BiquadFilterState,
            secondSamples: DoubleArray,
            count: Int,
            coefficients: BiquadCoefficients,
        ) {
            val b0 = coefficients.b0.toDouble()
            val b1 = coefficients.b1.toDouble()
            val b2 = coefficients.b2.toDouble()
            val a1 = coefficients.a1.toDouble()
            val a2 = coefficients.a2.toDouble()
            var firstX1 = first.x1
            var firstX2 = first.x2
            var firstY1 = first.y1
            var firstY2 = first.y2
            var secondX1 = second.x1
            var secondX2 = second.x2
            var secondY1 = second.y1
            var secondY2 = second.y2
            for (n in 0 until count) {
                val firstX0 = firstSamples[n]
                val secondX0 = secondSamples[n]
                val firstOutput = b0 * firstX0 + b1 * firstX1 + b2 * firstX2 - a1 * firstY1 - a2 * firstY2
                val secondOutput = b0 * secondX0 + b1 * secondX1 + b2 * secondX2 - a1 * secondY1 - a2 * secondY2
                firstX2 = firstX1
                firstX1 = firstX0
                firstY2 = firstY1
                firstY1 = firstOutput
                secondX2 = secondX1
                secondX1 = secondX0
                secondY2 = secondY1
                secondY1 = secondOutput
                firstSamples[n] = firstOutput
                secondSamples[n] = secondOutput
            }
            first.x1 = firstX1
            first.x2 = firstX2
            first.y1 = firstY1
            first.y2 = firstY2
            second.x1 = secondX1
            second.x2 = secondX2
            second.y1 = secondY1
            second.y2 = secondY2
        }
    }
}
