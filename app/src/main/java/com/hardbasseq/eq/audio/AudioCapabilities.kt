package com.hardbasseq.eq.audio

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
)
