package com.auracurve.app.preset

// Chat feature: the genre bases for the genre x intensity preset selector. Each is the "Moderate" tuning that PresetIntensityResolver derives
// the other four intensity variants from. Terror/Uptempo/Early/Frenchcore/Dance
// reuse the curves/macros already tuned for BuiltInPresets.MaximumDistortion/
// CleanPunch/RawPower/FastAttack/Balanced respectively (kept there unchanged
// too, for backward compatibility with anything already bound to those ids);
// Gabber and Pop are new.
object BuiltInGenrePresets {
    val Terror =
        GenrePreset(
            id = "genre_terror",
            displayName = "Terror",
            base =
                Preset(
                    id = "genre_terror",
                    name = "Terror",
                    targetCurve =
                        listOf(
                            TargetPoint(50f, 3.5f),
                            TargetPoint(90f, 1.5f),
                            TargetPoint(300f, -3.5f),
                            TargetPoint(2500f, -1.0f),
                            TargetPoint(6000f, -2.0f),
                            TargetPoint(12000f, -1.0f),
                        ),
                    macroBassDb = 1.5f,
                    macroPunchDb = 1.0f,
                    macroHaerteDb = -1.0f,
                    requestedHeadroomDb = 5.5f,
                    mbcThresholdDb = -10.0f,
                    mbcRatio = 3.5f,
                    metadata = PresetMetadata(genre = "terror", builtIn = true),
                ),
        )

    val Uptempo =
        GenrePreset(
            id = "genre_uptempo",
            displayName = "Uptempo",
            base =
                Preset(
                    id = "genre_uptempo",
                    name = "Uptempo",
                    targetCurve =
                        listOf(
                            TargetPoint(55f, 3.0f),
                            TargetPoint(105f, 2.0f),
                            TargetPoint(280f, -2.0f),
                            TargetPoint(3500f, 1.0f),
                            TargetPoint(8500f, -1.0f),
                        ),
                    macroBassDb = 1.5f,
                    macroPunchDb = 2.0f,
                    macroHaerteDb = 0.5f,
                    requestedHeadroomDb = 4.0f,
                    mbcThresholdDb = -7.0f,
                    mbcRatio = 2.5f,
                    metadata = PresetMetadata(genre = "uptempo", builtIn = true),
                ),
        )

    // Gabber's signature "doorlussen" kick sits more in the low-mid body
    // (~120 Hz) than uptempo's deeper sub-bass, with a scoop above it to keep
    // the kick from turning boxy and a forward upper-bass "smack" for attack.
    val Gabber =
        GenrePreset(
            id = "genre_gabber",
            displayName = "Gabber",
            base =
                Preset(
                    id = "genre_gabber",
                    name = "Gabber",
                    targetCurve =
                        listOf(
                            TargetPoint(50f, 2.5f),
                            TargetPoint(120f, 3.5f),
                            TargetPoint(300f, -2.5f),
                            TargetPoint(1200f, 1.0f),
                            TargetPoint(5000f, -0.5f),
                        ),
                    macroBassDb = 1.0f,
                    macroPunchDb = 2.5f,
                    macroHaerteDb = 0.5f,
                    requestedHeadroomDb = 4.5f,
                    mbcThresholdDb = -7.0f,
                    mbcRatio = 2.8f,
                    metadata = PresetMetadata(genre = "gabber", builtIn = true),
                ),
        )

    val Early =
        GenrePreset(
            id = "genre_early",
            displayName = "Early",
            base =
                Preset(
                    id = "genre_early",
                    name = "Early",
                    targetCurve =
                        listOf(
                            TargetPoint(45f, 3.5f),
                            TargetPoint(90f, 2.0f),
                            TargetPoint(300f, -2.0f),
                            TargetPoint(1500f, 1.0f),
                            TargetPoint(5000f, 0.5f),
                        ),
                    macroBassDb = 1.5f,
                    macroPunchDb = 2.0f,
                    macroHaerteDb = 1.0f,
                    requestedHeadroomDb = 4.5f,
                    mbcThresholdDb = -7.0f,
                    mbcRatio = 2.8f,
                    metadata = PresetMetadata(genre = "early-hardcore", builtIn = true),
                ),
        )

    val Frenchcore =
        GenrePreset(
            id = "genre_frenchcore",
            displayName = "Frenchcore",
            base =
                Preset(
                    id = "genre_frenchcore",
                    name = "Frenchcore",
                    targetCurve =
                        listOf(
                            TargetPoint(50f, 2.5f),
                            TargetPoint(100f, 3.0f),
                            TargetPoint(260f, -2.5f),
                            TargetPoint(2000f, 1.0f),
                            TargetPoint(4500f, 1.5f),
                            TargetPoint(9000f, -1.0f),
                        ),
                    macroBassDb = 1.0f,
                    macroPunchDb = 3.0f,
                    macroHaerteDb = 1.5f,
                    requestedHeadroomDb = 5.0f,
                    mbcThresholdDb = -6.0f,
                    mbcRatio = 3.2f,
                    metadata = PresetMetadata(genre = "frenchcore", builtIn = true),
                ),
        )

    val Dance =
        GenrePreset(
            id = "genre_dance",
            displayName = "Dance",
            base =
                Preset(
                    id = "genre_dance",
                    name = "Dance",
                    targetCurve =
                        listOf(
                            TargetPoint(65f, 2.0f),
                            TargetPoint(120f, 1.0f),
                            TargetPoint(300f, -1.5f),
                            TargetPoint(4000f, 0.5f),
                        ),
                    macroBassDb = 0.5f,
                    macroPunchDb = 0.75f,
                    macroHaerteDb = 0.25f,
                    requestedHeadroomDb = 3.0f,
                    mbcThresholdDb = -6.0f,
                    mbcRatio = 2.0f,
                    // "hard-dance", not "dance": matches PresetDesign.forPreset's existing
                    // HARD_DANCE bucket (same one BuiltInPresets.Balanced, whose tuning this
                    // reuses, already resolves to).
                    metadata = PresetMetadata(genre = "hard-dance", builtIn = true),
                ),
        )

    // Roadmap 14: the techno/tekk family. Hardtekk and Schranz put the kick body
    // (95-110 Hz) in front of a deep 300 Hz scoop, Techno and Hard Techno lean on
    // the sub instead. Industrial Techno and Schranz are mastered dirty, so their
    // 3.5-7 kHz range is tamed rather than lifted, with a slightly lower limiter
    // ceiling. Every base carries a subsonic filter: these genres live on sub
    // pressure, but nothing below ~25-32 Hz is musical there, only cone travel.
    val Hardtekk =
        GenrePreset(
            id = "genre_hardtekk",
            displayName = "Hardtekk",
            base =
                Preset(
                    id = "genre_hardtekk",
                    name = "Hardtekk",
                    targetCurve =
                        listOf(
                            TargetPoint(50f, 2.0f),
                            TargetPoint(95f, 3.5f),
                            TargetPoint(150f, 1.5f),
                            TargetPoint(300f, -3.0f),
                            TargetPoint(2500f, 1.5f),
                            TargetPoint(5000f, -0.5f),
                            TargetPoint(10000f, -0.5f),
                        ),
                    macroBassDb = 0.8f,
                    macroPunchDb = 2.5f,
                    macroHaerteDb = 0.8f,
                    requestedHeadroomDb = 4.5f,
                    mbcThresholdDb = -7.0f,
                    mbcRatio = 3.0f,
                    limiter = LimiterConfig(enabled = true, thresholdDb = -0.3f),
                    subsonicCutoffHz = 30f,
                    metadata = PresetMetadata(genre = "hardtekk", builtIn = true),
                ),
        )

    val Techno =
        GenrePreset(
            id = "genre_techno",
            displayName = "Techno",
            base =
                Preset(
                    id = "genre_techno",
                    name = "Techno",
                    targetCurve =
                        listOf(
                            TargetPoint(45f, 3.0f),
                            TargetPoint(90f, 2.0f),
                            TargetPoint(250f, -1.5f),
                            TargetPoint(1200f, 0.0f),
                            TargetPoint(3500f, 0.5f),
                            TargetPoint(8000f, 1.0f),
                            TargetPoint(13000f, 0.5f),
                        ),
                    macroBassDb = 1.2f,
                    macroPunchDb = 1.2f,
                    macroHaerteDb = 0.2f,
                    requestedHeadroomDb = 3.5f,
                    mbcThresholdDb = -8.0f,
                    mbcRatio = 2.2f,
                    limiter = LimiterConfig(enabled = true, thresholdDb = -0.3f),
                    subsonicCutoffHz = 25f,
                    metadata = PresetMetadata(genre = "techno", builtIn = true),
                ),
        )

    val HardTechno =
        GenrePreset(
            id = "genre_hard_techno",
            displayName = "Hard Techno",
            base =
                Preset(
                    id = "genre_hard_techno",
                    name = "Hard Techno",
                    targetCurve =
                        listOf(
                            TargetPoint(48f, 3.5f),
                            TargetPoint(100f, 2.5f),
                            TargetPoint(280f, -2.5f),
                            TargetPoint(1800f, 0.5f),
                            TargetPoint(3500f, 1.0f),
                            TargetPoint(6500f, -1.0f),
                            TargetPoint(11000f, 0.5f),
                        ),
                    macroBassDb = 1.5f,
                    macroPunchDb = 2.0f,
                    macroHaerteDb = 0.5f,
                    requestedHeadroomDb = 4.5f,
                    mbcThresholdDb = -7.5f,
                    mbcRatio = 2.8f,
                    limiter = LimiterConfig(enabled = true, thresholdDb = -0.3f),
                    subsonicCutoffHz = 28f,
                    metadata = PresetMetadata(genre = "hard-techno", builtIn = true),
                ),
        )

    val IndustrialTechno =
        GenrePreset(
            id = "genre_industrial_techno",
            displayName = "Industrial Techno",
            base =
                Preset(
                    id = "genre_industrial_techno",
                    name = "Industrial Techno",
                    targetCurve =
                        listOf(
                            TargetPoint(42f, 3.8f),
                            TargetPoint(90f, 2.0f),
                            TargetPoint(280f, -3.0f),
                            TargetPoint(1500f, 0.5f),
                            TargetPoint(3500f, -1.0f),
                            TargetPoint(6000f, -1.8f),
                            TargetPoint(11000f, 1.0f),
                        ),
                    macroBassDb = 1.5f,
                    macroPunchDb = 1.5f,
                    macroHaerteDb = -0.5f,
                    requestedHeadroomDb = 5.0f,
                    mbcThresholdDb = -9.0f,
                    mbcRatio = 3.2f,
                    limiter = LimiterConfig(enabled = true, thresholdDb = -0.5f),
                    subsonicCutoffHz = 28f,
                    metadata = PresetMetadata(genre = "industrial-techno", builtIn = true),
                ),
        )

    val Schranz =
        GenrePreset(
            id = "genre_schranz",
            displayName = "Schranz",
            base =
                Preset(
                    id = "genre_schranz",
                    name = "Schranz",
                    targetCurve =
                        listOf(
                            TargetPoint(50f, 2.0f),
                            TargetPoint(105f, 3.2f),
                            TargetPoint(160f, 1.0f),
                            TargetPoint(300f, -3.5f),
                            TargetPoint(2000f, 0.5f),
                            TargetPoint(4000f, -1.5f),
                            TargetPoint(7000f, -1.5f),
                            TargetPoint(12000f, -0.5f),
                        ),
                    macroBassDb = 0.8f,
                    macroPunchDb = 2.5f,
                    macroHaerteDb = -0.5f,
                    requestedHeadroomDb = 4.5f,
                    mbcThresholdDb = -9.5f,
                    mbcRatio = 3.4f,
                    limiter = LimiterConfig(enabled = true, thresholdDb = -0.5f),
                    subsonicCutoffHz = 32f,
                    metadata = PresetMetadata(genre = "schranz", builtIn = true),
                ),
        )

    // Gentle mainstream "smile" curve - mild bass/treble lift, no scoop, low
    // macros and a high MBC threshold so it rarely engages. Deliberately the
    // calmest genre base besides Flat.
    val Pop =
        GenrePreset(
            id = "genre_pop",
            displayName = "Pop",
            base =
                Preset(
                    id = "genre_pop",
                    name = "Pop",
                    targetCurve =
                        listOf(
                            TargetPoint(60f, 1.5f),
                            TargetPoint(150f, 0.5f),
                            TargetPoint(1000f, 0.0f),
                            TargetPoint(3000f, 0.3f),
                            TargetPoint(10000f, 1.0f),
                        ),
                    macroBassDb = 0.3f,
                    macroPunchDb = 0.3f,
                    macroHaerteDb = 0.2f,
                    requestedHeadroomDb = 2.0f,
                    mbcThresholdDb = -8.0f,
                    mbcRatio = 1.8f,
                    metadata = PresetMetadata(genre = "pop", builtIn = true),
                ),
        )

    val Rap =
        GenrePreset(
            id = "genre_rap",
            displayName = "Rap",
            base =
                Preset(
                    id = "genre_rap",
                    name = "Rap",
                    targetCurve =
                        listOf(
                            TargetPoint(50f, 3.0f),
                            TargetPoint(90f, 2.0f),
                            TargetPoint(250f, -1.0f),
                            TargetPoint(2500f, 1.0f),
                            TargetPoint(5000f, 0.5f),
                            TargetPoint(9000f, -0.5f),
                        ),
                    macroBassDb = 0.8f,
                    macroPunchDb = 0.5f,
                    macroHaerteDb = 0.0f,
                    requestedHeadroomDb = 3.5f,
                    mbcThresholdDb = -8.0f,
                    mbcRatio = 2.0f,
                    metadata = PresetMetadata(genre = "rap", builtIn = true),
                ),
        )

    // Neutral reference - ignores the intensity axis entirely (see
    // GenrePreset.allowsIntensity).
    val Flat =
        GenrePreset(
            id = "genre_flat",
            displayName = "Flat",
            allowsIntensity = false,
            base =
                Preset(
                    id = "genre_flat",
                    name = "Flat",
                    targetCurve =
                        listOf(
                            TargetPoint(60f, 0f),
                            TargetPoint(230f, 0f),
                            TargetPoint(910f, 0f),
                            TargetPoint(3600f, 0f),
                            TargetPoint(14000f, 0f),
                        ),
                    requestedHeadroomDb = 0.0f,
                    mbcEnabled = false,
                    limiter = LimiterConfig(enabled = false, thresholdDb = 0f),
                    metadata = PresetMetadata(genre = "neutral", builtIn = true),
                ),
        )

    val all =
        listOf(
            Terror,
            Uptempo,
            Gabber,
            Early,
            Frenchcore,
            Dance,
            Hardtekk,
            Techno,
            HardTechno,
            IndustrialTechno,
            Schranz,
            Pop,
            Rap,
            Flat,
        )

    // Every (genre, intensity) combination, pre-resolved into a concrete
    // Preset - this is what BuiltInPresets.all folds in, so findPresetById
    // keeps working unchanged for a device profile bound to one of these.
    val allResolvedPresets: List<Preset> =
        all.flatMap { genre ->
            if (genre.allowsIntensity) {
                PresetIntensity.entries.map { intensity -> PresetIntensityResolver.resolve(genre, intensity) }
            } else {
                listOf(PresetIntensityResolver.resolve(genre, PresetIntensity.MODERATE))
            }
        }
}
