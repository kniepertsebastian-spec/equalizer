package com.hardbasseq.eq.desktop

import com.hardbasseq.eq.dsp.CurveComposer
import com.hardbasseq.eq.dsp.EqualizerInterpolator
import com.hardbasseq.eq.dsp.HeadroomCalculator
import com.hardbasseq.eq.preset.PortableSoundProfile
import com.hardbasseq.eq.preset.Preset

/** Q factor used for every generated parametric band. Wide enough that 15
 * log-spaced bells blend into a smooth curve instead of visible ripples. */
const val DESKTOP_FILTER_Q = 1.4f

/** Butterworth Q of the subsonic high-pass, same as SubsonicFilterCurve. */
const val DESKTOP_SUBSONIC_Q = 0.7071f

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

/** What every desktop backend (Equalizer APO export, own player) needs to reproduce a sound. */
data class ResolvedSound(
    val title: String,
    val bandGainsDb: Map<Int, Float>,
    /** True high-pass corner in Hz, 0 = off. */
    val subsonicCutoffHz: Float,
) {
    val preampDb: Float get() = automaticPreampDb(bandGainsDb)
}

fun resolveSound(
    preset: Preset,
    macroBassDb: Float = preset.macroBassDb,
    macroPunchDb: Float = preset.macroPunchDb,
    macroHaerteDb: Float = preset.macroHaerteDb,
): ResolvedSound =
    ResolvedSound(
        title = preset.name,
        bandGainsDb = resolveBandGains(preset, macroBassDb, macroPunchDb, macroHaerteDb),
        subsonicCutoffHz = preset.subsonicCutoffHz,
    )

/**
 * Resolves a profile exported from the phone. Live Android bands
 * (`appliedEqCurve`) already contain the original macros and the subsonic
 * roll-off, so only macro changes made after the import are applied on top
 * and no second high-pass is added.
 */
fun resolveSound(
    profile: PortableSoundProfile,
    macroBassDb: Float = profile.macroBassDb,
    macroPunchDb: Float = profile.macroPunchDb,
    macroHaerteDb: Float = profile.macroHaerteDb,
): ResolvedSound {
    val usingAppliedCurve = profile.appliedEqCurve.isNotEmpty()
    val curve =
        if (usingAppliedCurve) {
            profile.appliedEqCurve
        } else {
            CurveComposer.combine(profile.correction.curve, profile.preset.targetCurve)
        }
    val bass = if (usingAppliedCurve) macroBassDb - profile.macroBassDb else macroBassDb
    val punch = if (usingAppliedCurve) macroPunchDb - profile.macroPunchDb else macroPunchDb
    val haerte = if (usingAppliedCurve) macroHaerteDb - profile.macroHaerteDb else macroHaerteDb
    return ResolvedSound(
        title = profile.preset.name,
        bandGainsDb = EqualizerInterpolator.interpolateCurveToBands(curve, VirtualBands.bands, bass, punch, haerte),
        subsonicCutoffHz = if (usingAppliedCurve) 0f else profile.preset.subsonicCutoffHz,
    )
}
