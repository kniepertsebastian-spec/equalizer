package com.hardbasseq.eq.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hardbasseq.eq.data.preset.PresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM custom_presets ORDER BY updatedAtMillis DESC")
    fun getAllFlow(): Flow<List<PresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PresetEntity)

    @Query("DELETE FROM custom_presets WHERE id = :id")
    suspend fun deleteById(id: String)
}
