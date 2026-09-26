package com.hardbasseq.eq.profile

import com.hardbasseq.eq.data.db.DeviceProfileDao
import com.hardbasseq.eq.data.profile.DeviceProfileEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomDeviceProfileRepository
    @Inject
    constructor(
        private val dao: DeviceProfileDao,
    ) : DeviceProfileRepository {
        override val allProfiles: Flow<List<DeviceProfileEntity>> = dao.getAllProfilesFlow()

        override suspend fun getProfileForRoute(routeId: String): DeviceProfileEntity? = dao.getProfileForRoute(routeId)

        override suspend fun saveProfile(
            routeId: String,
            routeType: String,
            displayName: String,
            boundPresetId: String,
            boundCorrectionProfileId: String,
            headphoneAcousticsOverride: Boolean?,
        ) {
            dao.saveProfile(
                DeviceProfileEntity(
                    routeId = routeId,
                    routeType = routeType,
                    displayName = displayName,
                    boundPresetId = boundPresetId,
                    boundCorrectionProfileId = boundCorrectionProfileId,
                    headphoneAcousticsOverride = headphoneAcousticsOverride,
                ),
            )
        }
    }
