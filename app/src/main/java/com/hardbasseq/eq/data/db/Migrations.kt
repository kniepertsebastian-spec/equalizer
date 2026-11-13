package com.hardbasseq.eq.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Release builds with schema 1 are installed, so this must be a real migration:
// without it Room would refuse to open the database at start.
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE device_profiles ADD COLUMN contextMode TEXT DEFAULT NULL")
        }
    }

// Roadmap 06, P5-5: the sound remembered per app. A new table only, so installed data is untouched.
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `app_profiles` (`packageName` TEXT NOT NULL, `boundPresetId` TEXT NOT NULL, " +
                    "`boundCorrectionProfileId` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`packageName`))",
            )
        }
    }
