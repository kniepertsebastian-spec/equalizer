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
    // Context-mode features (see Preset for the same three). 0 = off.
    val loudnessMaxBoostDb: Float = 0f,
    val subsonicCutoffHz: Float = 0f,
    val virtualBassMix: Float = 0f,
    // "High-resolution EQ": ask the engine for the DynamicsProcessing pre-EQ instead of the
    // classic 5-band system equalizer, which squeezes the whole curve onto five broad bands.
    // On by default; devices that refuse the pre-EQ fall back to the classic equalizer.
    // Only takes effect when the engine reports preEqCutoffsHz after (re)attaching.
    val highResolutionEq: Boolean = true,
    // One gain per band of PreEqLayout.CUTOFFS_HZ; empty = not in use.
    val preEqGainsDb: List<Float> = emptyList(),
)
