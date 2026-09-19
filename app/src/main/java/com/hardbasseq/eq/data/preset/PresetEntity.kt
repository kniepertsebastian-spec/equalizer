package com.hardbasseq.eq.data.preset

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val schemaVersion: Int = 1,
    val targetCurveJson: String,
    val requestedHeadroomDb: Float = 4.0f,
    val genre: String = "custom",
    val isBuiltIn: Boolean = false,
)
