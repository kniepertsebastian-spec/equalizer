package com.hardbasseq.eq.data.preset

import androidx.room.Entity
import androidx.room.PrimaryKey

// Only ever holds custom (user-saved) presets - built-ins stay compile-time
// constants (BuiltInPresets.all) and are never written here, matching the M2
// acceptance criterion "Built-ins bleiben unveränderlich". The whole domain
// Preset is stored as one validated JSON blob (see PresetJsonSerializer)
// rather than one column per field: it reuses that already-tested
// (de)serialization/validation logic directly for the "corrupted data can't
// block startup" requirement (a row that fails to parse is just skipped, see
// PresetRepository), and means a new Preset field never needs its own
// migration - only a change to the *shape* of Preset itself would.
@Entity(tableName = "custom_presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val presetJson: String,
    val updatedAtMillis: Long,
)
