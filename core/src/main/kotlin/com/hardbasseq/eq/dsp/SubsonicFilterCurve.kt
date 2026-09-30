package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint

// Subsonic high-pass as a plain TargetPoint curve: a 2nd-order Butterworth
// high-pass at cutoffHz, sampled from the exact biquad response
// (BiquadFilterDesigner.magnitudeResponseDb) rather than an ad-hoc slope.
// Small drivers (car doors, Bluetooth speakers) cannot reproduce the deepest
// octave, but still spend amplifier headroom and excursion on it - taking it
// out leaves more headroom and a tighter-sounding bass.
//
// Being a normal curve it composes through CurveComposer like every other
// source. The caveat of that path: the curve is mapped onto whatever bands the
// system equalizer offers, so the real attenuation is only as steep as the
// lowest band allows - a graphic approximation of the filter, not a true
// high-pass (which would need an owned DSP path, see Media3DspPipeline).
object SubsonicFilterCurve {
    const val MIN_CUTOFF_HZ = 20f
    const val MAX_CUTOFF_HZ = 80f

    private const val REFERENCE_SAMPLE_RATE_HZ = 48000f
    private const val BUTTERWORTH_Q = 0.70710678f
    private const val MIN_POINT_HZ = 10f
    private val cutoffMultiples = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)

    // cutoffHz <= 0 means "off" and yields no curve at all.
    fun forCutoff(cutoffHz: Float): List<TargetPoint> {
        if (cutoffHz <= 0f) return emptyList()
        val cutoff = cutoffHz.coerceIn(MIN_CUTOFF_HZ, MAX_CUTOFF_HZ)
        val coefficients =
            BiquadFilterDesigner.design(
                ParametricFilter(ParametricFilterType.HIGH_PASS, cutoff, 0f, BUTTERWORTH_Q),
                REFERENCE_SAMPLE_RATE_HZ,
            )
        return cutoffMultiples
            .map { multiple -> (cutoff * multiple).coerceAtLeast(MIN_POINT_HZ) }
            .distinct()
            .map { freqHz ->
                TargetPoint(
                    frequencyHz = freqHz,
                    gainDb = BiquadFilterDesigner.magnitudeResponseDb(coefficients, freqHz, REFERENCE_SAMPLE_RATE_HZ).coerceIn(-24f, 0f),
                )
            }
    }
}
