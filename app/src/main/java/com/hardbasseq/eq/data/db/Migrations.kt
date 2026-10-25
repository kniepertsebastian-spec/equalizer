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
