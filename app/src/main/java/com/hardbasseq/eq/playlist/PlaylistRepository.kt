package com.hardbasseq.eq.playlist

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Playlists imported from share links, kept on the device. Stored as one JSON
// document in its own DataStore rather than in the Room database: a playlist is
// read and written as a whole, and this way the existing database schema (and
// its migration story) stays untouched.
interface PlaylistRepository {
    val playlists: Flow<List<SavedPlaylist>>

    // Upsert by id - importing the same link again refreshes its playlist.
    suspend fun save(playlist: SavedPlaylist)

    suspend fun delete(id: String)
}

private val Context.playlistDataStore by preferencesDataStore(name = "playlists")
private val PLAYLISTS_KEY = stringPreferencesKey("playlists_json")

@Singleton
class DataStorePlaylistRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PlaylistRepository {
        override val playlists: Flow<List<SavedPlaylist>> =
            context.playlistDataStore.data.map { prefs -> SavedPlaylistJson.decode(prefs[PLAYLISTS_KEY]) }

        override suspend fun save(playlist: SavedPlaylist) {
            context.playlistDataStore.edit { prefs ->
                val current = SavedPlaylistJson.decode(prefs[PLAYLISTS_KEY])
                prefs[PLAYLISTS_KEY] = SavedPlaylistJson.encode(current.filterNot { it.id == playlist.id } + playlist)
            }
        }

        override suspend fun delete(id: String) {
            context.playlistDataStore.edit { prefs ->
                val current = SavedPlaylistJson.decode(prefs[PLAYLISTS_KEY])
                prefs[PLAYLISTS_KEY] = SavedPlaylistJson.encode(current.filterNot { it.id == id })
            }
        }
    }
