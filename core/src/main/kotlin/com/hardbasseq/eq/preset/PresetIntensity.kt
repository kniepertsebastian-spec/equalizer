package com.hardbasseq.eq.preset

// Chat feature: genre x intensity preset redesign. Each level is an additive
// delta on top of a GenrePreset's own "Moderate" tuning - only macroBassDb and
// macroPunchDb, since the user's own definition of "aggressive" was bass/punch
// ("mehr Wumms, viel Bass"), not treble, so Härte stays whatever each genre
// already tuned it to. The MBC threshold/ratio deltas tighten the low band's
// compression as bass grows so it stays controlled rather than boomy at high
// intensity (the user's "Qualität steigend bei den Bässen"), and loosen it at
// low intensity since a quiet bass macro doesn't need much compression either.
// This, together with the existing headroom-based automatic input-gain cut
// (which already pulls down harder as the peak boost grows), is what keeps
// perceived loudness comparatively stable across intensities ("Lautstärke
// ändert sich hingegen kaum") without any new loudness-matching machinery.
enum class PresetIntensity(
    val id: String,
    val displayName: String,
    val macroBassDeltaDb: Float,
    val macroPunchDeltaDb: Float,
    val mbcThresholdDeltaDb: Float,
    val mbcRatioDelta: Float,
) {
    SUPER_SOFT("super_soft", "Super Soft", -2.5f, -1.5f, 2.0f, -0.4f),
    SOFT("soft", "Soft", -1.25f, -0.75f, 1.0f, -0.2f),
    MODERATE("moderate", "Moderate", 0f, 0f, 0f, 0f),
    AGGRESSIVE("aggressive", "Aggressive", 2.5f, 1.5f, -1.5f, 0.4f),
    VERY_AGGRESSIVE("very_aggressive", "Very Aggressive", 5.0f, 3.0f, -3.0f, 0.8f),
    ;

    companion object {
        fun fromId(id: String): PresetIntensity? = entries.find { it.id == id }
    }
}
