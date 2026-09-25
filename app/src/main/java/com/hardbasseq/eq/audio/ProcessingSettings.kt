package com.hardbasseq.eq.audio

import kotlinx.serialization.Serializable

@Serializable
data class ProcessingSettings(
    val masterEnabled: Boolean = true,
    val bypass: Boolean = false,
    val bandGainsDb: Map<Int, Float> = emptyMap(),
    val inputGainDb: Float = 0f,
    val macroBassDb: Float = 0f,
    val macroPunchDb: Float = 0f,
    val macroHaerteDb: Float = 0f,
    val limiterEnabled: Boolean = true,
    val limiterThresholdDb: Float = -1.0f,
    val mbcEnabled: Boolean = false,
    val mbcThresholdDb: Float = -6.0f,
    val mbcRatio: Float = 2.0f,
)
