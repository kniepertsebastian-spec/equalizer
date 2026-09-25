package com.hardbasseq.eq.correction

import android.util.Log
import com.hardbasseq.eq.data.correction.CorrectionProfileEntity
import com.hardbasseq.eq.data.correction.CorrectionProfileJsonSerializer
import com.hardbasseq.eq.data.db.CorrectionProfileDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RoomCorrectionProfileRepository"

@Singleton
class RoomCorrectionProfileRepository
    @Inject
    constructor(
        private val dao: CorrectionProfileDao,
    ) : CorrectionProfileRepository {
        override val customProfiles: Flow<List<CorrectionProfile>> =
            dao.getAllFlow().map { entities ->
                // Same "corrupted row can't block startup" handling as
                // RoomPresetRepository - see there for why.
                entities.mapNotNull { entity ->
                    CorrectionProfileJsonSerializer
                        .importFromJson(entity.profileJson)
                        .onFailure { e -> Log.w(TAG, "Skipping corrupted correction profile ${entity.id}: ${e.message}") }
                        .getOrNull()
                }
            }

        override suspend fun save(profile: CorrectionProfile) {
            dao.upsert(
                CorrectionProfileEntity(
                    id = profile.id,
                    name = profile.name,
                    profileJson = CorrectionProfileJsonSerializer.exportToJson(profile),
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
        }

        override suspend fun delete(id: String) {
            dao.deleteById(id)
        }
    }
