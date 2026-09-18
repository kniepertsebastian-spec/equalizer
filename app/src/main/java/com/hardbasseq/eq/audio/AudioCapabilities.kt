package com.hardbasseq.eq.audio

/**
 * Minimal, device-observed slice of [ProcessingSettings]-relevant capabilities
 * that can be determined purely from `AudioEffect.queryEffects()` (M0). Band
 * counts, frequency ranges and gain limits require an attached [Equalizer]
 * instance and are added once the session-attach spike (M2) lands.
 */
data class AudioCapabilities(
    val hasEqualizer: Boolean,
    val hasDynamicsProcessing: Boolean,
    val hasBassBoost: Boolean,
    val hasLoudnessEnhancer: Boolean,
    val totalEffectCount: Int,
)
