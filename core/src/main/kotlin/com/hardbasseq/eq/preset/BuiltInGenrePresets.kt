package com.hardbasseq.eq.preset

// Chat feature: the 8 genre bases for the new genre x intensity preset
// selector. Each is the "Moderate" tuning that PresetIntensityResolver derives
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

    val all = listOf(Terror, Uptempo, Gabber, Early, Frenchcore, Dance, Pop, Flat)

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
