package com.hardbasseq.eq.data.profile

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_profiles")
data class DeviceProfileEntity(
    @PrimaryKey val routeId: String,
    val routeType: String,
    val displayName: String,
    val boundPresetId: String,
)
