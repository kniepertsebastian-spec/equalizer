package com.hardbasseq.eq.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hardbasseq.eq.data.preset.PresetEntity
import com.hardbasseq.eq.data.profile.DeviceProfileEntity

@Database(
    entities = [PresetEntity::class, DeviceProfileEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao

    abstract fun deviceProfileDao(): DeviceProfileDao
}
