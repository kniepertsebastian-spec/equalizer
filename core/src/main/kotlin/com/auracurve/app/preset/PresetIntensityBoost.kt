package com.auracurve.app.preset

/**
 * "More intensity": one on/off switch instead of the five intensity steps. When on, the active
 * preset gets the former "Aggressive" step on top (more Bass and Punch macro, tighter low-band
 * compression so the extra bass stays controlled). It works on every preset, genre variants and
 * custom ones included, except neutral ones (Flat): an intense reference would defeat its purpose.
 *
 * The old genre x intensity presets stay resolvable by id (device and app profiles may be bound to
 * them); [legacy] maps them onto the genre's base plus the switch.
 */
object PresetIntensityBoost {
    private val STEP = PresetIntensity.AGGRESSIVE

    val macroBassDeltaDb: Float get() = STEP.macroBassDeltaDb
    val macroPunchDeltaDb: Float get() = STEP.macroPunchDeltaDb

    fun appliesTo(preset: Preset): Boolean = preset.metadata.genre != "neutral"

    /** The preset as it plays with the switch on (unchanged where the switch does not apply). */
    fun apply(preset: Preset): Preset {
        if (!appliesTo(preset)) return preset
        return preset.copy(
            macroBassDb = preset.macroBassDb + STEP.macroBassDeltaDb,
            macroPunchDb = preset.macroPunchDb + STEP.macroPunchDeltaDb,
            mbcThresholdDb = (preset.mbcThresholdDb + STEP.mbcThresholdDeltaDb).coerceIn(-30f, 0f),
            mbcRatio = (preset.mbcRatio + STEP.mbcRatioDelta).coerceIn(1f, 6f),
        )
    }

    /**
     * An old "genre – intensity" preset as the genre's base plus the switch: Super Soft, Soft and
     * Moderate become the base with the switch off, Aggressive and Very Aggressive the base with it
     * on. The base itself (Moderate) returns null for the switch: picking it keeps the switch as is.
     * Null for any other preset.
     */
    fun legacy(
        presetId: String,
        genres: List<GenrePreset> = BuiltInGenrePresets.all,
    ): LegacyIntensity? {
        val (genre, intensity) = PresetIntensityResolver.parse(presetId, genres) ?: return null
        val base = PresetIntensityResolver.resolve(genre, PresetIntensity.MODERATE)
        val switch =
            when (intensity) {
                PresetIntensity.MODERATE -> null
                PresetIntensity.SUPER_SOFT, PresetIntensity.SOFT -> false
                PresetIntensity.AGGRESSIVE, PresetIntensity.VERY_AGGRESSIVE -> genre.allowsIntensity
            }
        return LegacyIntensity(base, switch)
    }

    /** True for the old intensity steps other than the base, which the app no longer offers. */
    fun isRetiredStep(presetId: String): Boolean = legacy(presetId)?.moreIntensity != null
}

/** See [PresetIntensityBoost.legacy]. [moreIntensity] null = leave the switch as it is. */
data class LegacyIntensity(
    val base: Preset,
    val moreIntensity: Boolean?,
)
