package com.auracurve.app.preset

/**
 * "More intensity": one on/off switch instead of the five intensity steps. When on, the active
 * preset gets the former "Aggressive" step on top (more Bass and Punch macro, tighter low-band
 * compression so the extra bass stays controlled). It works on every preset, genre variants and
 * custom ones included, except neutral ones (Flat): an intense reference would defeat its purpose.
 *
 * The old genre x intensity presets stay resolvable by id (device and app profiles may be bound to
 * them); [legacy] maps them onto the genre's base plus the switch.
 *
 * Roadmap 18: presets for the built-in player ([PresetSourceScope.OWN_PLAYER]) get half the step.
 * The player has no loudness normalization in front of its chain, so the full step pushed their
 * kick back up to the system-EQ level the player presets had just been tuned down from.
 */
object PresetIntensityBoost {
    private val STEP = PresetIntensity.AGGRESSIVE

    /** The share of the step a built-in player preset gets. */
    const val OWN_PLAYER_SHARE = 0.5f

    /** The full step's Bass delta (what presets for other apps get). */
    val macroBassDeltaDb: Float get() = STEP.macroBassDeltaDb

    /** The full step's Punch delta (what presets for other apps get). */
    val macroPunchDeltaDb: Float get() = STEP.macroPunchDeltaDb

    fun appliesTo(preset: Preset): Boolean = preset.metadata.genre != "neutral"

    /** How much of the step [preset] gets: 0 where the switch does not apply, half for the built-in player. */
    fun shareFor(preset: Preset): Float =
        when {
            !appliesTo(preset) -> 0f
            preset.metadata.sourceScope == PresetSourceScope.OWN_PLAYER -> OWN_PLAYER_SHARE
            else -> 1f
        }

    /** The Bass delta the switch adds to [preset]. */
    fun macroBassDeltaDb(preset: Preset): Float = STEP.macroBassDeltaDb * shareFor(preset)

    /** The Punch delta the switch adds to [preset]. */
    fun macroPunchDeltaDb(preset: Preset): Float = STEP.macroPunchDeltaDb * shareFor(preset)

    /** The preset as it plays with the switch on (unchanged where the switch does not apply). */
    fun apply(preset: Preset): Preset {
        val share = shareFor(preset)
        if (share == 0f) return preset
        return preset.copy(
            macroBassDb = preset.macroBassDb + STEP.macroBassDeltaDb * share,
            macroPunchDb = preset.macroPunchDb + STEP.macroPunchDeltaDb * share,
            mbcThresholdDb = (preset.mbcThresholdDb + STEP.mbcThresholdDeltaDb * share).coerceIn(-30f, 0f),
            mbcRatio = (preset.mbcRatio + STEP.mbcRatioDelta * share).coerceIn(1f, 6f),
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
