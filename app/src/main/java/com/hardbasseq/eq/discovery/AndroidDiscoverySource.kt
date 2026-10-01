package com.hardbasseq.eq.discovery

import android.content.Context
import com.hardbasseq.eq.integration.LoadResult
import com.soundcloud.equalizer.player.model.TrackItem
import com.soundcloud.equalizer.player.playback.DiscoveryLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class AndroidDiscoverySource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : DiscoverySource {
        private val loader = DiscoveryLoader(context)

        override suspend fun tasteTracks(): LoadResult<List<TrackItem>> =
            try {
                LoadResult.Ok(loader.likedTracks())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoadResult.Error("Likes konnten nicht gelesen werden: ${e.message ?: "unbekannter Fehler"}")
            }

        override suspend fun recentUploads(artist: String): LoadResult<List<TrackItem>> =
            try {
                LoadResult.Ok(loader.recentUploads(artist))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoadResult.Error("Suche nach \"$artist\" fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}")
            }
    }
