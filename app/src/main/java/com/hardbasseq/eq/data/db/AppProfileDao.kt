package com.hardbasseq.eq.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hardbasseq.eq.data.profile.AppProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppProfileDao {
    @Query("SELECT * FROM app_profiles WHERE packageName = :packageName")
    suspend fun getProfile(packageName: String): AppProfileEntity?

    @Query("SELECT * FROM app_profiles")
    fun getAllProfilesFlow(): Flow<List<AppProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: AppProfileEntity): Long

    @Query("DELETE FROM app_profiles WHERE packageName = :packageName")
    suspend fun deleteProfile(packageName: String)
}
