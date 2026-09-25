package com.hardbasseq.eq.data.correction

import androidx.room.Entity
import androidx.room.PrimaryKey

// Mirrors PresetEntity: only custom (imported/user-saved) correction profiles are
// stored here - BuiltInCorrectionProfiles.None stays a compile-time constant and
// is never written to this table.
@Entity(tableName = "custom_correction_profiles")
data class CorrectionProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val profileJson: String,
    val updatedAtMillis: Long,
)
