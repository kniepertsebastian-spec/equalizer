package com.auracurve.app.preset

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
                // Techno/tekk family (roadmap 14), mapped onto the existing looks: the dark,
                // distorted styles share Terrorcore's, Hardtekk the raw Gabber look, fast hard
                // techno the Uptempo one and straight techno the club-oriented Hard Dance one.
                // The more specific names come first, since all of them contain "techno".
                "industrial" in key || "schranz" in key -> TERRORCORE
                "hardtekk" in key -> GABBER
                "hard-techno" in key || "hard techno" in key || "hard_techno" in key -> UPTEMPO_HARDCORE
                "techno" in key -> HARD_DANCE
                else -> FLAT
            }
        }
    }
}
