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
)
