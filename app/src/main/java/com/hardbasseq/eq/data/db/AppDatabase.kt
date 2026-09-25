package com.hardbasseq.eq.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hardbasseq.eq.data.preset.PresetEntity
import com.hardbasseq.eq.data.profile.DeviceProfileEntity

// M2: exportSchema = true now that this DB has a real write path (see
// PresetRepository) - schemas/ holds the versioned JSON baselines
// MigrationTestHelper needs. No such test exists yet: it needs Robolectric or
// instrumentation, neither of which this repo has set up (ci.yml only runs
// testDebugUnitTest, a plain-JVM tier Room's SQLite bindings can't run under).
// Adding that infra is a prerequisite for the *next* schema change, not this
// one - `custom_presets` never had a real writer before today (verified via
// a repo-wide search for PresetDao call sites - there were none), so version
// 1 here is the first schema real user data will ever be written under.
@Database(
    entities = [PresetEntity::class, DeviceProfileEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao

    abstract fun deviceProfileDao(): DeviceProfileDao
}
