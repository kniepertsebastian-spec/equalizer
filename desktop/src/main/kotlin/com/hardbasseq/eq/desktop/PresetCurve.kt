package com.hardbasseq.eq.desktop

import com.hardbasseq.eq.dsp.EqualizerInterpolator
import com.hardbasseq.eq.dsp.HeadroomCalculator
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
 * positive gain of the actual cascaded parametric filters - not just the
 * highest single band's own gain. Equalizer APO stacks real peaking biquads,
 * and cascaded filters' dB responses ADD, so closely-spaced boosted bands
 * (e.g. the dense bass region on Aggressive/Very Aggressive intensities) can
 * combine to a true peak above any individual band's gain; using only the
 * naive per-band max would under-compensate the Preamp and risk clipping.
 */
fun automaticPreampDb(bandGainsDb: Map<Int, Float>): Float =
    HeadroomCalculator
        .fromCascadedPeakingFilters(bandGainsDb, VirtualBands.bands, DESKTOP_FILTER_Q)
        .recommendedInputGainDb
