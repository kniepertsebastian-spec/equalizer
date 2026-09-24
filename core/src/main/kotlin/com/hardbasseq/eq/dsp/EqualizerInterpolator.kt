package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.audio.EqualizerBandCapabilities
import com.hardbasseq.eq.preset.Preset
import kotlin.math.log10

object EqualizerInterpolator {
    fun interpolatePresetToBands(
        preset: Preset,
        bands: List<EqualizerBandCapabilities>,
        macroBassDb: Float = 0f,
        macroPunchDb: Float = 0f,
        macroHaerteDb: Float = 0f,
    ): Map<Int, Float> {
        if (bands.isEmpty()) return emptyMap()

        val sortedPoints = preset.targetCurve.sortedBy { it.frequencyHz }
        if (sortedPoints.isEmpty()) {
            return bands.associate { it.index to 0f }
        }

        val result = mutableMapOf<Int, Float>()

        for (band in bands) {
            val freqHz = band.centerFreqHz.toFloat()
            var interpolatedGain = interpolateFrequency(freqHz, sortedPoints)

            // Apply Macros
            interpolatedGain +=
                calculateMacroDelta(
                    freqHz = freqHz,
                    macroBassDb = macroBassDb,
                    macroPunchDb = macroPunchDb,
                    macroHaerteDb = macroHaerteDb,
                )

            // Clamp to band capabilities
            val clampedGain = interpolatedGain.coerceIn(band.minGainDb, band.maxGainDb)
            result[band.index] = clampedGain
        }

        return result
    }

    fun interpolateFrequency(
        freqHz: Float,
        sortedPoints: List<com.hardbasseq.eq.preset.TargetPoint>,
    ): Float {
        if (sortedPoints.isEmpty()) return 0f
        if (freqHz <= sortedPoints.first().frequencyHz) return sortedPoints.first().gainDb
        if (freqHz >= sortedPoints.last().frequencyHz) return sortedPoints.last().gainDb

        for (i in 0 until sortedPoints.size - 1) {
            val p1 = sortedPoints[i]
            val p2 = sortedPoints[i + 1]
            if (freqHz >= p1.frequencyHz && freqHz <= p2.frequencyHz) {
                val logF = log10(freqHz)
                val logF1 = log10(p1.frequencyHz)
                val logF2 = log10(p2.frequencyHz)
                if (logF2 == logF1) return p1.gainDb

                val fraction = (logF - logF1) / (logF2 - logF1)
                return p1.gainDb + fraction * (p2.gainDb - p1.gainDb)
            }
        }
        return 0f
    }

    private fun calculateMacroDelta(
        freqHz: Float,
        macroBassDb: Float,
        macroPunchDb: Float,
        macroHaerteDb: Float,
    ): Float {
        var delta = 0f

        // Macro Bass: Low shelf < 120 Hz
        if (freqHz <= 120f) {
            delta += macroBassDb
        } else if (freqHz < 200f) {
            val factor = (200f - freqHz) / 80f
            delta += macroBassDb * factor
        }

        // Macro Punch: peak 70-160 Hz, dip 220-360 Hz. Widened in Session 17 - the
        // original 80-150/250-350 Hz windows missed every band on the real Pixel 10
        // layout (60/230/910/3600/14000 Hz), so the Punch slider had zero audible
        // effect on that device. These wider windows now cover the 230 Hz band too.
        if (freqHz in 70f..160f) {
            delta += macroPunchDb
        } else if (freqHz in 220f..360f) {
            delta -= macroPunchDb * 0.5f
        }

        // Macro Härte: High mid 2.5k - 6k (clamped +2 dB max)
        if (freqHz in 2500f..6000f) {
            delta += macroHaerteDb.coerceAtMost(2.0f)
        }

        return delta
    }

    fun calculateHeadroom(bandGainsDb: Map<Int, Float>): HeadroomInfo {
        val maxPositiveGainDb = bandGainsDb.values.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        val recommendedInputGainDb = -maxPositiveGainDb
        val isClippingRisk = maxPositiveGainDb > 0f
        return HeadroomInfo(
            maxPositiveGainDb = maxPositiveGainDb,
            recommendedInputGainDb = recommendedInputGainDb,
            isClippingRisk = isClippingRisk,
        )
    }
}

data class HeadroomInfo(
    val maxPositiveGainDb: Float,
    val recommendedInputGainDb: Float,
    val isClippingRisk: Boolean,
)
