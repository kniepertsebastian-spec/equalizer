package com.auracurve.app.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.auracurve.app.car.SpeedVolumeSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings of the remote control. They live in the app's no-backup directory
 * (Context.noBackupFilesDir), which neither Auto Backup nor device transfer copies. The paired
 * devices are kept beside them (RemoteDevices, module 05); the single token of module 04a is only
 * read once more to take it over.
 */
@Singleton
class RemoteServerSettings
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val store: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { File(context.noBackupFilesDir, FILE_NAME) })

        /** The single token of app versions before module 05, if there still is one. */
        suspend fun legacyToken(): String? = store.data.first()[TOKEN]

        suspend fun clearLegacyToken() {
            store.edit { it.remove(TOKEN) }
        }

        /** Module 05 (E-016): also answer in networks the phone is a client of, like the home Wi-Fi. Off by default. */
        val allowWifi: Flow<Boolean> = store.data.map { it[ALLOW_WIFI] ?: false }

        suspend fun setAllowWifi(enabled: Boolean) {
            store.edit { it[ALLOW_WIFI] = enabled }
        }

        /** 04e (U-2): start the server when the phone has booted and was unlocked once. Off by default. */
        val startAtBoot: Flow<Boolean> = store.data.map { it[START_AT_BOOT] ?: false }

        suspend fun setStartAtBoot(enabled: Boolean) {
            store.edit { it[START_AT_BOOT] = enabled }
        }

        /** 04e (R-19): the phone answers to `<hostName>.local` while the server runs. */
        val hostName: Flow<String> = store.data.map { prefs -> prefs[HOST_NAME]?.let(MdnsName::normalize) ?: MdnsName.DEFAULT }

        /** Stores [name] if it is valid (see [MdnsName.normalize]); false leaves the old one. */
        suspend fun setHostName(name: String): Boolean {
            val valid = MdnsName.normalize(name) ?: return false
            store.edit { it[HOST_NAME] = valid }
            return true
        }

        /** Module 11, F6: louder at speed. Off until switched on in the remote. */
        val speedVolume: Flow<SpeedVolumeSettings> =
            store.data.map { prefs ->
                val d = SpeedVolumeSettings()
                SpeedVolumeSettings(
                    enabled = prefs[SPEED_VOLUME_ON] ?: d.enabled,
                    maxBoostDb = prefs[SPEED_VOLUME_MAX_DB] ?: d.maxBoostDb,
                    startSpeedKmh = prefs[SPEED_VOLUME_START_KMH] ?: d.startSpeedKmh,
                    fullSpeedKmh = prefs[SPEED_VOLUME_FULL_KMH] ?: d.fullSpeedKmh,
                ).sanitized()
            }

        suspend fun setSpeedVolume(settings: SpeedVolumeSettings): SpeedVolumeSettings {
            val clean = settings.sanitized()
            store.edit {
                it[SPEED_VOLUME_ON] = clean.enabled
                it[SPEED_VOLUME_MAX_DB] = clean.maxBoostDb
                it[SPEED_VOLUME_START_KMH] = clean.startSpeedKmh
                it[SPEED_VOLUME_FULL_KMH] = clean.fullSpeedKmh
            }
            return clean
        }

        private companion object {
            val SPEED_VOLUME_ON = booleanPreferencesKey("speed_volume_on")
            val SPEED_VOLUME_MAX_DB = floatPreferencesKey("speed_volume_max_db")
            val SPEED_VOLUME_START_KMH = floatPreferencesKey("speed_volume_start_kmh")
            val SPEED_VOLUME_FULL_KMH = floatPreferencesKey("speed_volume_full_kmh")
            const val FILE_NAME = "remote_server.preferences_pb"
            val TOKEN = stringPreferencesKey("token")
            val START_AT_BOOT = booleanPreferencesKey("start_at_boot")
            val HOST_NAME = stringPreferencesKey("host_name")
            val ALLOW_WIFI = booleanPreferencesKey("allow_wifi")
        }
    }
