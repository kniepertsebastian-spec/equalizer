package com.hardbasseq.eq.discovery

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// The state behind "Interesting new uploads" (watched artists, this week's playlist,
// what was shown or swiped away), kept on the device as one JSON document in its own
// DataStore - same approach as the saved playlists, no database schema involved.
interface DiscoveryRepository {
    val state: Flow<DiscoveryState>

    suspend fun current(): DiscoveryState

    // Reads, transforms and writes in one atomic step, so the weekly background job and
    // the screen cannot overwrite each other's changes.
    suspend fun update(transform: (DiscoveryState) -> DiscoveryState)
}

private val Context.discoveryDataStore by preferencesDataStore(name = "discovery")
private val DISCOVERY_KEY = stringPreferencesKey("discovery_json")

@Singleton
class DataStoreDiscoveryRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : DiscoveryRepository {
        override val state: Flow<DiscoveryState> =
            context.discoveryDataStore.data.map { prefs -> DiscoveryStateJson.decode(prefs[DISCOVERY_KEY]) }

        override suspend fun current(): DiscoveryState = state.first()

        override suspend fun update(transform: (DiscoveryState) -> DiscoveryState) {
            context.discoveryDataStore.edit { prefs ->
                val updated = transform(DiscoveryStateJson.decode(prefs[DISCOVERY_KEY]))
                prefs[DISCOVERY_KEY] = DiscoveryStateJson.encode(updated)
            }
        }
    }
