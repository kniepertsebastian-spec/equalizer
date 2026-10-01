package com.hardbasseq.eq.playlist

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hardbasseq.eq.link.SpotifyImportJson
import com.hardbasseq.eq.link.SpotifyImportState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Where a long Spotify import stands (see SpotifyImportPlan): saved after every block of 100
// songs, so it can go on after the app was closed. Its own small DataStore, one JSON document.
interface SpotifyImportRepository {
    // The unfinished import, or null.
    val pending: Flow<SpotifyImportState?>

    suspend fun current(): SpotifyImportState?

    suspend fun save(state: SpotifyImportState)

    suspend fun clear()
}

private val Context.spotifyImportDataStore by preferencesDataStore(name = "spotify_import")
private val SPOTIFY_IMPORT_KEY = stringPreferencesKey("spotify_import_json")

@Singleton
class DataStoreSpotifyImportRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : SpotifyImportRepository {
        override val pending: Flow<SpotifyImportState?> =
            context.spotifyImportDataStore.data.map { prefs -> SpotifyImportJson.decode(prefs[SPOTIFY_IMPORT_KEY]) }

        override suspend fun current(): SpotifyImportState? = pending.first()

        override suspend fun save(state: SpotifyImportState) {
            context.spotifyImportDataStore.edit { prefs -> prefs[SPOTIFY_IMPORT_KEY] = SpotifyImportJson.encode(state) }
        }

        override suspend fun clear() {
            context.spotifyImportDataStore.edit { prefs -> prefs.remove(SPOTIFY_IMPORT_KEY) }
        }
    }
