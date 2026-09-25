package com.hardbasseq.eq.correction

import kotlinx.coroutines.flow.Flow

/**
 * Custom (imported/user-saved) correction profiles only - BuiltInCorrectionProfiles
 * are compile-time constants and never go through here. See CorrectionProfileEntity.
 */
interface CorrectionProfileRepository {
    val customProfiles: Flow<List<CorrectionProfile>>

    suspend fun save(profile: CorrectionProfile)

    suspend fun delete(id: String)
}
