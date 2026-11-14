package com.hardbasseq.eq.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardbasseq.eq.data.db.AppDatabase
import com.hardbasseq.eq.data.db.MIGRATION_1_2
import com.hardbasseq.eq.data.db.MIGRATION_2_3
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * An installed database of an older schema must open with the real migrations and pass Room's own
 * validation (the same check that runs at app start). Builds the old database from the exported
 * schema JSON in app/schemas, so it needs no device. The instrumented DatabaseMigrationTest checks
 * the same on a device.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class DatabaseUpgradeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun createOldDatabase(
        name: String,
        version: Int,
    ) {
        val schema = JSONObject(File("schemas/com.hardbasseq.eq.data.db.AppDatabase/$version.json").readText()).getJSONObject("database")
        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) {
            db.execSQL(setup.getString(i).replace("34cf8ed1c4bfa4cd2f5ac4b5c5b8a8b8", schema.getString("identityHash")))
        }
        db.version = version
        db.close()
    }

    @Test
    fun schema2_opensWithTheMigrationsAndValidates() {
        createOldDatabase("upgrade-2", 2)
        val db =
            Room
                .databaseBuilder(context, AppDatabase::class.java, "upgrade-2")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .allowMainThreadQueries()
                .build()
        try {
            runBlocking {
                assertEquals(null, db.appProfileDao().getProfile("com.spotify.music"))
                assertEquals(null, db.deviceProfileDao().getProfileForRoute("x"))
                assertNotNull(db.presetDao())
            }
        } finally {
            db.close()
        }
    }
}
