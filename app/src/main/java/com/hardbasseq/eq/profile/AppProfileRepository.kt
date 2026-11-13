package com.hardbasseq.eq.profile

import com.hardbasseq.eq.data.db.AppProfileDao
import com.hardbasseq.eq.data.profile.AppProfileEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** The sound remembered per app (roadmap 06, P5-5). */
interface AppProfileRepository {
    val allProfiles: Flow<List<AppProfileEntity>>

    suspend fun getProfile(packageName: String): AppProfileEntity?

    suspend fun saveProfile(
        packageName: String,
        boundPresetId: String,
        boundCorrectionProfileId: String,
    )

    suspend fun deleteProfile(packageName: String)
}

object NoAppProfileRepository : AppProfileRepository {
    override val allProfiles: Flow<List<AppProfileEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun getProfile(packageName: String): AppProfileEntity? = null

    override suspend fun saveProfile(
        packageName: String,
        boundPresetId: String,
        boundCorrectionProfileId: String,
    ) = Unit

    override suspend fun deleteProfile(packageName: String) = Unit
}

@Singleton
class RoomAppProfileRepository
    @Inject
    constructor(
        private val dao: AppProfileDao,
    ) : AppProfileRepository {
        override val allProfiles: Flow<List<AppProfileEntity>> = dao.getAllProfilesFlow()

        override suspend fun getProfile(packageName: String): AppProfileEntity? = dao.getProfile(packageName)

        override suspend fun saveProfile(
            packageName: String,
            boundPresetId: String,
            boundCorrectionProfileId: String,
        ) {
            dao.saveProfile(
                AppProfileEntity(
                    packageName = packageName,
                    boundPresetId = boundPresetId,
                    boundCorrectionProfileId = boundCorrectionProfileId,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
        }

        override suspend fun deleteProfile(packageName: String) = dao.deleteProfile(packageName)
    }
