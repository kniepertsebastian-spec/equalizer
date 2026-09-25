package com.hardbasseq.eq.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppSettingsRepository"
private val Context.dataStore by preferencesDataStore(name = "app_settings")
private val LIVE_SETTINGS_KEY = stringPreferencesKey("live_settings_json")

@Singleton
class DataStoreAppSettingsRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : AppSettingsRepository {
        private val json = Json { ignoreUnknownKeys = true }

        override val liveSettings: Flow<LiveSettings?> =
            context.dataStore.data.map { prefs ->
                val raw = prefs[LIVE_SETTINGS_KEY] ?: return@map null
                runCatching { json.decodeFromString(LiveSettings.serializer(), raw) }
                    .onFailure { e -> Log.w(TAG, "Corrupted saved settings, falling back to defaults", e) }
                    .getOrNull()
            }

        override suspend fun save(liveSettings: LiveSettings) {
            context.dataStore.edit { prefs ->
                prefs[LIVE_SETTINGS_KEY] = json.encodeToString(LiveSettings.serializer(), liveSettings)
            }
        }
    }
