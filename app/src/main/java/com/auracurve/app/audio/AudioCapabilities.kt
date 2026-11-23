package com.auracurve.app.audio

data class AudioCapabilities(
    val hasEqualizer: Boolean = false,
    val hasDynamicsProcessing: Boolean = false,
    val hasBassBoost: Boolean = false,
    val hasLoudnessEnhancer: Boolean = false,
    val totalEffectCount: Int = 0,
    val bands: List<EqualizerBandCapabilities> = emptyList(),
    val hasInputGain: Boolean = false,
    val hasLimiter: Boolean = false,
    val hasMbc: Boolean = false,
    // Upper band edges of the DynamicsProcessing pre-EQ when it is in use; empty = classic equalizer.
    val preEqCutoffsHz: List<Float> = emptyList(),
    // Why the high-resolution EQ was asked for but is not in use (device refusal); null otherwise.
    val preEqUnavailableReason: String? = null,
    // Roadmap 13: how many bands the multiband compressor runs with (5, or 4 on a device that
    // refuses 5); 0 without one.
    val mbcBandCount: Int = 0,
    // Roadmap 13, P13-2: band edges of the post-EQ behind the compressor (the pre-EQ's layout);
    // empty when the device runs PreEQ + MBC only.
    val postEqCutoffsHz: List<Float> = emptyList(),
)
