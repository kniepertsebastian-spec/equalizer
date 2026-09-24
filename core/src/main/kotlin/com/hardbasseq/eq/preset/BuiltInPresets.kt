package com.hardbasseq.eq.preset

object BuiltInPresets {
    val CleanPunch =
        Preset(
            id = "builtin_clean_punch",
            name = "Uptempo – Clean Punch",
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
            metadata = PresetMetadata(genre = "uptempo-hardcore", builtIn = true),
        )

    val DeepRumble =
        Preset(
            id = "builtin_deep_rumble",
            name = "Uptempo – Deep Rumble",
            targetCurve =
                listOf(
                    TargetPoint(55f, 4.0f),
                    TargetPoint(105f, 1.0f),
                    TargetPoint(280f, -2.5f),
                    TargetPoint(4000f, 0.0f),
                ),
            macroBassDb = 2.0f,
            macroPunchDb = 0.5f,
            macroHaerteDb = -0.5f,
            requestedHeadroomDb = 5.0f,
            mbcThresholdDb = -9.0f,
            mbcRatio = 2.8f,
            metadata = PresetMetadata(genre = "uptempo-hardcore", builtIn = true),
        )

    val KickAttack =
        Preset(
            id = "builtin_kick_attack",
            name = "Uptempo – Kick Attack",
            targetCurve =
                listOf(
                    TargetPoint(50f, 3.0f),
                    TargetPoint(100f, 2.5f),
                    TargetPoint(250f, -3.0f),
                    TargetPoint(3200f, 1.5f),
                    TargetPoint(6000f, -1.0f),
                    TargetPoint(10000f, -0.5f),
                ),
            macroBassDb = 1.0f,
            macroPunchDb = 2.5f,
            macroHaerteDb = 1.0f,
            requestedHeadroomDb = 4.5f,
            mbcThresholdDb = -6.0f,
            mbcRatio = 3.0f,
            metadata = PresetMetadata(genre = "uptempo-hardcore", builtIn = true),
        )

    val Balanced =
        Preset(
            id = "builtin_balanced",
            name = "Hard Dance – Balanced",
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
            metadata = PresetMetadata(genre = "hard-dance", builtIn = true),
        )

    val Flat =
        Preset(
            id = "builtin_flat",
            name = "Flat / Safe",
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
        )

    val RawPower =
        Preset(
            id = "builtin_raw_power",
            name = "Hardcore – Raw Power",
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
            metadata = PresetMetadata(genre = "hardcore", builtIn = true),
        )

    val FastAttack =
        Preset(
            id = "builtin_fast_attack",
            name = "Frenchcore – Fast Attack",
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
        )

    // Terrorcore source material is already pushed into heavy distortion, so
    // this preset leans on taming (negative Härte, lower MBC threshold) and
    // low-end weight rather than adding more high-frequency harshness.
    val MaximumDistortion =
        Preset(
            id = "builtin_maximum_distortion",
            name = "Terrorcore – Maximum Distortion",
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
            metadata = PresetMetadata(genre = "terrorcore", builtIn = true),
        )

    val FinalSmash =
        Preset(
            id = "builtin_final_smash",
            name = "Uptempo – Final Smash",
            targetCurve =
                listOf(
                    TargetPoint(50f, 4.0f),
                    TargetPoint(105f, 3.0f),
                    TargetPoint(260f, -3.0f),
                    TargetPoint(3500f, 2.0f),
                    TargetPoint(7000f, 0.5f),
                    TargetPoint(12000f, -0.5f),
                ),
            macroBassDb = 2.0f,
            macroPunchDb = 3.0f,
            macroHaerteDb = 1.5f,
            requestedHeadroomDb = 5.5f,
            mbcThresholdDb = -6.0f,
            mbcRatio = 3.0f,
            metadata = PresetMetadata(genre = "uptempo-hardcore", builtIn = true),
        )

    val all =
        listOf(
            CleanPunch,
            DeepRumble,
            KickAttack,
            Balanced,
            Flat,
            RawPower,
            FastAttack,
            MaximumDistortion,
            FinalSmash,
        )
}
