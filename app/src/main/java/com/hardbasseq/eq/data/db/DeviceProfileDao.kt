package com.hardbasseq.eq.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hardbasseq.eq.data.profile.DeviceProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceProfileDao {
    @Query("SELECT * FROM device_profiles WHERE routeId = :routeId")
    suspend fun getProfileForRoute(routeId: String): DeviceProfileEntity?

    @Query("SELECT * FROM device_profiles")
    fun getAllProfilesFlow(): Flow<List<DeviceProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: DeviceProfileEntity): Long
}
