package com.hardbasseq.eq.correction

import com.hardbasseq.eq.preset.TargetPoint
import kotlinx.serialization.Serializable

// M3 "Datenmodell": headphone/speaker correction curve, kept separate from the
// VoicingPreset (== the existing Preset/BuiltInPresets) it gets combined with via
// CurveComposer. sourceLabel is shown in the UI (e.g. "AutoEQ: Sennheiser HD 599",
// "Manuell") so a user can tell an imported correction from a hand-tuned one.
@Serializable
data class CorrectionProfile(
    val id: String,
    val name: String,
    val curve: List<TargetPoint> = emptyList(),
    val sourceLabel: String = "Manuell",
    val builtIn: Boolean = false,
)

object BuiltInCorrectionProfiles {
    // The default: no correction applied. An empty curve interpolates to 0 dB
    // everywhere (see EqualizerInterpolator.interpolateFrequency), so this is a
    // true no-op in CurveComposer, not a special case.
    val None =
        CorrectionProfile(
            id = "correction_none",
            name = "Kein Korrekturprofil",
            curve = emptyList(),
            sourceLabel = "Keine Korrektur",
            builtIn = true,
        )

    val all = listOf(None)
}
