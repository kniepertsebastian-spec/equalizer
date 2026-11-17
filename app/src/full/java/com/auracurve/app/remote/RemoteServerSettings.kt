package com.auracurve.app.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The remote-control token (Pixel-Car module 04a). It lives in the app's no-backup directory
 * (Context.noBackupFilesDir), which neither Auto Backup nor device transfer copies, so a restored
 * phone never answers to the old phone's token. Never logged.
 */
@Singleton
class RemoteServerSettings
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val store: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { File(context.noBackupFilesDir, FILE_NAME) })

        val token: Flow<String?> = store.data.map { it[TOKEN] }

        /** The stored token; one is created the first time. */
        suspend fun ensureToken(): String {
            token.first()?.let { return it }
            var created = ""
            store.edit { prefs ->
                created = prefs[TOKEN] ?: RemoteToken.generate().also { prefs[TOKEN] = it }
            }
            return created
        }

        /** 04e (U-2): start the server when the phone has booted and was unlocked once. Off by default. */
        val startAtBoot: Flow<Boolean> = store.data.map { it[START_AT_BOOT] ?: false }

        suspend fun setStartAtBoot(enabled: Boolean) {
            store.edit { it[START_AT_BOOT] = enabled }
        }

        /** A new token: every device paired with the old one has to be paired again. */
        suspend fun regenerateToken(): String {
            val fresh = RemoteToken.generate()
            store.edit { it[TOKEN] = fresh }
            return fresh
        }

        private companion object {
            const val FILE_NAME = "remote_server.preferences_pb"
            val TOKEN = stringPreferencesKey("token")
            val START_AT_BOOT = booleanPreferencesKey("start_at_boot")
        }
    }
