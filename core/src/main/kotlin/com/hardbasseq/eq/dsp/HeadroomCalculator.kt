package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.audio.EqualizerBandCapabilities
import com.hardbasseq.eq.preset.TargetPoint
import kotlin.math.exp
import kotlin.math.ln

// roadmap-2026.md M4: headroom must come from the combined resulting frequency
// response, not just the highest single hardware-band value after downsampling -
// the true peak of a continuous curve can fall between two hardware band centers
// and get missed if only the (post-mapping) band gains are inspected. Samples the
// curve at a fixed log-spaced resolution across the audible range instead, using
// the exact same resolveGainAtFrequency() the engine uses to derive per-band
// gains, so headroom always reflects what will actually be applied.
object HeadroomCalculator {
    private const val MIN_FREQ_HZ = 20f
    private const val MAX_FREQ_HZ = 20000f
    private const val SAMPLE_COUNT = 120

    fun fromCombinedCurve(
        combinedCurve: List<TargetPoint>,
        macroBassDb: Float = 0f,
        macroPunchDb: Float = 0f,
        macroHaerteDb: Float = 0f,
    ): HeadroomInfo {
        if (combinedCurve.isEmpty() && macroBassDb == 0f && macroPunchDb == 0f && macroHaerteDb == 0f) {
            return HeadroomInfo(maxPositiveGainDb = 0f, recommendedInputGainDb = 0f, isClippingRisk = false)
        }

        val sortedCurve = combinedCurve.sortedBy { it.frequencyHz }
        val logMin = ln(MIN_FREQ_HZ)
        val logMax = ln(MAX_FREQ_HZ)
        val logStep = (logMax - logMin) / (SAMPLE_COUNT - 1)

        var maxGainDb = Float.NEGATIVE_INFINITY
        for (i in 0 until SAMPLE_COUNT) {
            val freqHz = exp(logMin + logStep * i)
            val gain = EqualizerInterpolator.resolveGainAtFrequency(freqHz, sortedCurve, macroBassDb, macroPunchDb, macroHaerteDb)
            if (gain > maxGainDb) maxGainDb = gain
        }

        val maxPositiveGainDb = maxGainDb.coerceAtLeast(0f)
        return HeadroomInfo(
            maxPositiveGainDb = maxPositiveGainDb,
            recommendedInputGainDb = -maxPositiveGainDb,
            isClippingRisk = maxPositiveGainDb > 0f,
        )
    }

    // Chat feature ("Presets für Aggressive/Very Aggressive nicht übersteuern
    // lassen"): a plain parametric EQ (Equalizer APO, no downstream limiter)
    // cascades real peaking biquads, and cascaded filters' dB responses ADD -
    // several closely-spaced, heavily-boosted bass bands (e.g. 31/45/63/90/
    // 125/175 Hz at Q=1.4) can combine to a true peak well above any single
    // band's own gain. fromCombinedCurve()/automaticPreampDb()'s naive
    // max-of-discrete-bands undercounts exactly that case, under-compensating
    // the Preamp. This designs the actual per-band PEAK biquads and sums their
    // analytic magnitude responses (dB) across the same log-spaced sweep,
    // so the resulting recommendedInputGainDb reflects the real filter cascade.
    fun fromCascadedPeakingFilters(
        bandGainsDb: Map<Int, Float>,
        bands: List<EqualizerBandCapabilities>,
        q: Float,
        sampleRateHz: Float = 48000f,
    ): HeadroomInfo {
        val filterCoefficients =
            bands.mapNotNull { band ->
                val gainDb = bandGainsDb[band.index] ?: 0f
                if (gainDb == 0f) {
                    null
                } else {
                    BiquadFilterDesigner.design(
                        ParametricFilter(ParametricFilterType.PEAK, band.centerFreqHz.toFloat(), gainDb, q),
                        sampleRateHz,
                    )
                }
            }
        if (filterCoefficients.isEmpty()) {
            return HeadroomInfo(maxPositiveGainDb = 0f, recommendedInputGainDb = 0f, isClippingRisk = false)
        }

        val logMin = ln(MIN_FREQ_HZ)
        val logMax = ln(MAX_FREQ_HZ)
        val logStep = (logMax - logMin) / (SAMPLE_COUNT - 1)

        var maxCombinedDb = Float.NEGATIVE_INFINITY
        for (i in 0 until SAMPLE_COUNT) {
            val freqHz = exp(logMin + logStep * i)
            var combinedDb = 0f
            for (coefficients in filterCoefficients) {
                combinedDb += BiquadFilterDesigner.magnitudeResponseDb(coefficients, freqHz, sampleRateHz)
            }
            if (combinedDb > maxCombinedDb) maxCombinedDb = combinedDb
        }

        val maxPositiveGainDb = maxCombinedDb.coerceAtLeast(0f)
        return HeadroomInfo(
            maxPositiveGainDb = maxPositiveGainDb,
            recommendedInputGainDb = -maxPositiveGainDb,
            isClippingRisk = maxPositiveGainDb > 0f,
        )
    }
}
