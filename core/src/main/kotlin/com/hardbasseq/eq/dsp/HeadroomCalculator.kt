package com.hardbasseq.eq.dsp

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
}
