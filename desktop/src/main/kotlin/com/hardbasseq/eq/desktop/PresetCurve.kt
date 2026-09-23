package com.hardbasseq.eq.desktop

import com.hardbasseq.eq.dsp.EqualizerInterpolator
import com.hardbasseq.eq.preset.Preset

/** Q factor used for every generated parametric band. Wide enough that 15
 * log-spaced bells blend into a smooth curve instead of visible ripples. */
const val DESKTOP_FILTER_Q = 1.4f

/**
 * Resolves a preset (with optional macro overrides, defaulting to the
 * preset's own macro values) to concrete band gains on [VirtualBands],
 * reusing the same interpolation the Android app uses for real hardware.
 */
fun resolveBandGains(
    preset: Preset,
    macroBassDb: Float = preset.macroBassDb,
    macroPunchDb: Float = preset.macroPunchDb,
    macroHaerteDb: Float = preset.macroHaerteDb,
): Map<Int, Float> =
    EqualizerInterpolator.interpolatePresetToBands(
        preset = preset,
        bands = VirtualBands.bands,
        macroBassDb = macroBassDb,
        macroPunchDb = macroPunchDb,
        macroHaerteDb = macroHaerteDb,
    )

/**
 * Desktop exporters have no compressor/limiter downstream by default (unlike
 * the Android engine's MBC+Limiter chain), so instead of the partial,
 * limiter-assisted pre-cancellation used there, this fully cancels the peak
 * positive band gain - the conservative choice for a plain parametric EQ.
 */
fun automaticPreampDb(bandGainsDb: Map<Int, Float>): Float {
    val peakBoostDb = bandGainsDb.values.maxOrNull()?.coerceAtLeast(0f) ?: 0f
    return if (peakBoostDb == 0f) 0f else -peakBoostDb
}
