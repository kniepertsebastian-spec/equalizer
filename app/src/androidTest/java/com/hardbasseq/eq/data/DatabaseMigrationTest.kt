package com.hardbasseq.eq.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hardbasseq.eq.data.db.AppDatabase
import com.hardbasseq.eq.data.db.MIGRATION_1_2
import com.hardbasseq.eq.data.db.MIGRATION_2_3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Roadmap 06, P6-3: an installed schema-1 database must open as schema 2 without losing rows.
 * Needs a device or emulator (room-testing opens real SQLite files); the schemas come from
 * app/schemas, see the androidTest asset directory in app/build.gradle.kts.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate1To2_keepsDeviceProfilesAndAddsContextMode() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL(
                "INSERT INTO device_profiles (routeId, routeType, displayName, boundPresetId, boundCorrectionProfileId, " +
                    "headphoneAcousticsOverride) VALUES ('route-1', 'BLUETOOTH', 'Headset', 'builtin_clean_punch', 'correction_none', 1)",
            )
            execSQL(
                "INSERT INTO custom_presets (id, name, presetJson, updatedAtMillis) VALUES ('p1', 'Mine', '{}', 42)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)

        migrated.query("SELECT routeId, boundPresetId, headphoneAcousticsOverride, contextMode FROM device_profiles").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("route-1", cursor.getString(0))
            assertEquals("builtin_clean_punch", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertNull("the new column starts empty", cursor.getString(3).takeIf { !cursor.isNull(3) })
        }
        migrated.query("SELECT name, updatedAtMillis FROM custom_presets").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("Mine", cursor.getString(0))
            assertEquals(42L, cursor.getLong(1))
        }
    }

    @Test
    fun migrate2To3_addsTheAppProfilesTableAndKeepsEverythingElse() {
        helper.createDatabase(DB_NAME, 2).apply {
            execSQL(
                "INSERT INTO device_profiles (routeId, routeType, displayName, boundPresetId, boundCorrectionProfileId, " +
                    "headphoneAcousticsOverride, contextMode) VALUES ('route-1', 'CAR', 'Car', 'builtin_flat', 'correction_none', NULL, 'CAR')",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3)

        migrated.execSQL(
            "INSERT INTO app_profiles (packageName, boundPresetId, boundCorrectionProfileId, updatedAtMillis) " +
                "VALUES ('com.spotify.music', 'builtin_deep_rumble', 'correction_none', 1)",
        )
        migrated.query("SELECT COUNT(*) FROM app_profiles").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT contextMode FROM device_profiles WHERE routeId = 'route-1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("CAR", cursor.getString(0))
        }
    }

    @Test
    fun migrateAll_from1To3_works() {
        helper.createDatabase(DB_NAME, 1).close()

        helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_1_2, MIGRATION_2_3).close()
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
