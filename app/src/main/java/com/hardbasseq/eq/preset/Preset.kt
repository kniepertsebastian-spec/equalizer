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
    val thresholdDb: Float = -1.0f,
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
    val limiter: LimiterConfig = LimiterConfig(),
    val metadata: PresetMetadata = PresetMetadata(),
)
