package com.hardbasseq.eq.settings

import com.hardbasseq.eq.audio.ProcessingSettings
import kotlinx.serialization.Serializable

// The full live editing state - which preset it's based on, whether it's been
// manually edited since (M2: "manuelle Änderung eines Presets automatisch als
// Custom markieren"), and the actual processing values (including any manual
// per-band tweaks that were never explicitly saved as their own preset).
// Persisted as a whole so a restart never silently drops in-progress edits -
// roadmap-2026.md M2's acceptance criterion is "Alle Einstellungen überleben
// App-, Prozess- und Geräteneustart", not just saved/named presets.
@Serializable
data class LiveSettings(
    val activePresetId: String,
    val isDirty: Boolean,
    val processingSettings: ProcessingSettings,
    // M3: which CorrectionProfile ("Mein Kopfhörer") is active, alongside the
    // voicing (activePresetId, "Klangstil") this was already tracking. Defaulted
    // rather than made nullable/required so JSON saved before M3 (missing this
    // field) still decodes - kotlinx.serialization applies the default for a
    // field absent from the source JSON.
    val activeCorrectionProfileId: String = "correction_none",
)
