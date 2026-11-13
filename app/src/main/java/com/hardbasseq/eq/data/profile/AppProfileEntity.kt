package com.hardbasseq.eq.data.profile

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Roadmap 06, P5-5: the sound to use while one particular app (Spotify, YouTube, ...) is the one the
 * EQ is attached to - in addition to the per-device [DeviceProfileEntity]. Only apps the user asked
 * for have a row, so an app without one simply keeps whatever is active.
 */
@Entity(tableName = "app_profiles")
data class AppProfileEntity(
    @PrimaryKey val packageName: String,
    val boundPresetId: String,
    val boundCorrectionProfileId: String = "correction_none",
    val updatedAtMillis: Long = 0L,
)
