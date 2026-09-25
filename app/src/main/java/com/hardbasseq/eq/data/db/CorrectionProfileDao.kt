package com.hardbasseq.eq.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hardbasseq.eq.data.correction.CorrectionProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CorrectionProfileDao {
    @Query("SELECT * FROM custom_correction_profiles ORDER BY updatedAtMillis DESC")
    fun getAllFlow(): Flow<List<CorrectionProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CorrectionProfileEntity)

    @Query("DELETE FROM custom_correction_profiles WHERE id = :id")
    suspend fun deleteById(id: String)
}
