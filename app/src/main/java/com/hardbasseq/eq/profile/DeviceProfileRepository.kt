package com.hardbasseq.eq.profile

import com.hardbasseq.eq.data.profile.DeviceProfileEntity
import kotlinx.coroutines.flow.Flow

interface DeviceProfileRepository {
    val allProfiles: Flow<List<DeviceProfileEntity>>

    suspend fun getProfileForRoute(routeId: String): DeviceProfileEntity?

    suspend fun saveProfile(
        routeId: String,
        routeType: String,
        displayName: String,
        boundPresetId: String,
        boundCorrectionProfileId: String,
    )
}
