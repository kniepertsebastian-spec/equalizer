package com.hardbasseq.eq.preset

/** Visual treatment of the equalizer screen; it never changes audio processing. */
enum class PresetDesign {
    UPTEMPO_HARDCORE,
    TERRORCORE,
    GABBER,
    HARD_DANCE,
    FLAT,
    ;

    companion object {
        fun forPreset(preset: Preset): PresetDesign {
            // Names/IDs keep imported or renamed presets intuitive; metadata makes
            // duplicated built-ins retain their look even after a custom rename.
            val key = "${preset.id} ${preset.name} ${preset.metadata.genre}".lowercase()
            return when {
                "terror" in key -> TERRORCORE
                "uptempo" in key -> UPTEMPO_HARDCORE
                "hard-dance" in key || "hard dance" in key -> HARD_DANCE
                "frenchcore" in key || "hardcore" in key || "gabber" in key -> GABBER
                else -> FLAT
            }
        }
    }
}
