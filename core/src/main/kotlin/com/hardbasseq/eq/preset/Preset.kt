package com.hardbasseq.eq.preset

import kotlinx.serialization.Serializable

@Serializable
data class TargetPoint(
    val frequencyHz: Float,
    val gainDb: Float,
)

@Serializable
data class LimiterConfig(
    val enabled: Boolean = true,
    // Session 24: raised -1.0 -> -0.3dB at the user's explicit request for more
    // headroom/punch, after already loosening the input-gain and MBC makeup-gain
    // stages ahead of this in AndroidAudioEngine. -0.3dBFS is a standard mastering
    // true-peak ceiling, not an arbitrary number - the actual clipping guarantee
    // comes from the Limiter's ratio (10:1, unchanged, in AndroidAudioEngine), not
    // this threshold, so this alone doesn't add real clipping risk.
    val thresholdDb: Float = -0.3f,
)

@Serializable
data class PresetMetadata(
    val genre: String = "uptempo-hardcore",
    val builtIn: Boolean = false,
)

@Serializable
data class Preset(
    val id: String,
    val schemaVersion: Int = 1,
    val name: String,
    val targetCurve: List<TargetPoint>,
    val macroBassDb: Float = 0f,
    val macroPunchDb: Float = 0f,
    val macroHaerteDb: Float = 0f,
    val requestedHeadroomDb: Float = 4.0f,
    val mbcEnabled: Boolean = true,
    val mbcThresholdDb: Float = -8.0f,
    val mbcRatio: Float = 2.5f,
    val limiter: LimiterConfig = LimiterConfig(),
    val metadata: PresetMetadata = PresetMetadata(),
)
