package com.hardbasseq.eq.preset

// Chat feature: one row of the genre x intensity preset selector. `base` is the
// genre's "Moderate" tuning; PresetIntensityResolver derives the other four
// intensity variants from it. `allowsIntensity = false` for Flat - a "very
// aggressive" neutral reference preset would contradict its whole purpose, so
// Flat ignores the intensity axis entirely (all five buttons resolve to the
// same unmodified preset).
data class GenrePreset(
    val id: String,
    val displayName: String,
    val base: Preset,
    val allowsIntensity: Boolean = true,
)
