package com.auracurve.app.dsp

import kotlin.math.abs
import kotlin.math.tanh

// Roadmap 09, P9-3: a gentle saturation stage for the player's output, in front of the
// safety ceiling (LookaheadLimiter).
//
// Below the knee (default 0.75, about -2.5 dBFS) the signal passes 100 % linear, so the
// music's dynamics stay untouched. Above it the excess is bent into a tanh curve that runs
// toward the ceiling (default 0.98) but never reaches it:
//
//   |y| = knee + (ceiling - knee) * tanh((|x| - knee) / (ceiling - knee))
//
// tanh starts with slope 1, so the curve leaves the straight line without a kink (value and
// slope are continuous at the knee). A kick that would hit the limiter at full force instead
// gets its tip rounded off - dense and warm instead of a hard edge, and the limiter behind it
// only has inter-sample overs left to catch.
//
// Allocation-free and stateless: safe on the audio thread, one instance per chain is enough.
class SoftClipper(
    knee: Double = DEFAULT_KNEE,
    ceiling: Double = DEFAULT_CEILING,
) {
    val knee: Double = knee.coerceIn(MIN_KNEE, MAX_CEILING - MIN_SPAN)
    val ceiling: Double = ceiling.coerceIn(this.knee + MIN_SPAN, MAX_CEILING)
    private val span = this.ceiling - this.knee
    private val inverseSpan = 1.0 / span

    fun process(sample: Double): Double {
        // NaN would otherwise run through tanh and poison everything behind it.
        if (sample.isNaN()) return 0.0
        val magnitude = abs(sample)
        if (magnitude <= knee) return sample
        val shaped = knee + span * tanh((magnitude - knee) * inverseSpan)
        return if (sample < 0.0) -shaped else shaped
    }

    /** The first [frames] samples of [samples], in place. */
    fun processBlock(
        samples: DoubleArray,
        frames: Int,
    ) {
        val knee = knee
        val span = span
        val inverseSpan = inverseSpan
        for (n in 0 until frames) {
            val sample = samples[n]
            val magnitude = abs(sample)
            // The common case (below the knee) costs one comparison; NaN fails it too.
            if (magnitude <= knee) continue
            samples[n] =
                if (sample.isNaN()) {
                    0.0
                } else {
                    val shaped = knee + span * tanh((magnitude - knee) * inverseSpan)
                    if (sample < 0.0) -shaped else shaped
                }
        }
    }

    companion object {
        /** -2.5 dBFS. */
        const val DEFAULT_KNEE = 0.75
        const val DEFAULT_CEILING = 0.98
        private const val MIN_KNEE = 0.1
        private const val MAX_CEILING = 1.0
        private const val MIN_SPAN = 0.01
    }
}
