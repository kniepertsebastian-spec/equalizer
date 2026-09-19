package com.hardbasseq.eq.preset

object BuiltInPresets {

    val CleanPunch = Preset(
        id = "builtin_clean_punch",
        name = "Uptempo – Clean Punch",
        targetCurve = listOf(
            TargetPoint(55f, 3.0f),
            TargetPoint(105f, 2.0f),
            TargetPoint(280f, -2.0f),
            TargetPoint(3500f, 1.0f),
            TargetPoint(8500f, -1.0f),
        ),
        requestedHeadroomDb = 4.0f,
        metadata = PresetMetadata(genre = "uptempo-hardcore", builtIn = true),
    )

    val DeepRumble = Preset(
        id = "builtin_deep_rumble",
        name = "Uptempo – Deep Rumble",
        targetCurve = listOf(
            TargetPoint(55f, 4.0f),
            TargetPoint(105f, 1.0f),
            TargetPoint(280f, -2.5f),
            TargetPoint(4000f, 0.0f),
        ),
        requestedHeadroomDb = 5.0f,
        metadata = PresetMetadata(genre = "uptempo-hardcore", builtIn = true),
    )

    val Balanced = Preset(
        id = "builtin_balanced",
        name = "Hard Dance – Balanced",
        targetCurve = listOf(
            TargetPoint(65f, 2.0f),
            TargetPoint(120f, 1.0f),
            TargetPoint(300f, -1.5f),
            TargetPoint(4000f, 0.5f),
        ),
        requestedHeadroomDb = 3.0f,
        metadata = PresetMetadata(genre = "hard-dance", builtIn = true),
    )

    val Flat = Preset(
        id = "builtin_flat",
        name = "Flat / Safe",
        targetCurve = listOf(
            TargetPoint(60f, 0f),
            TargetPoint(230f, 0f),
            TargetPoint(910f, 0f),
            TargetPoint(3600f, 0f),
            TargetPoint(14000f, 0f),
        ),
        requestedHeadroomDb = 0.0f,
        limiter = LimiterConfig(enabled = false, thresholdDb = 0f),
        metadata = PresetMetadata(genre = "neutral", builtIn = true),
    )

    val all = listOf(CleanPunch, DeepRumble, Balanced, Flat)
}
