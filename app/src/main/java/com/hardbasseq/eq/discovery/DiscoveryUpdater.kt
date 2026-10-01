package com.hardbasseq.eq.discovery

import com.hardbasseq.eq.integration.LoadResult
import com.hardbasseq.eq.playlist.SavedTrack
import com.soundcloud.equalizer.player.model.TrackItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

// Where new uploads for an artist name come from (SoundCloud search).
interface DiscoverySource {
    suspend fun recentUploads(artist: String): LoadResult<List<TrackItem>>
}

sealed interface RefreshResult {
    // Nothing to do: no artists, or (when only refreshing if due) this week is built.
    data object Skipped : RefreshResult

    data class Updated(
        val count: Int,
    ) : RefreshResult

    data class Failed(
        val message: String,
    ) : RefreshResult
}

// Builds the weekly playlist: asks the source for every watched artist, keeps what
// qualifies (DiscoveryRotation.isEligible) and lets DiscoveryRotation pick. Shared by
// the screen and the weekly background job; a lock keeps two refreshes from running
// over each other.
@Singleton
class DiscoveryUpdater
    @Inject
    constructor(
        private val repository: DiscoveryRepository,
        private val source: DiscoverySource,
    ) {
        private val lock = Mutex()

        suspend fun refresh(
            nowMs: Long,
            utcOffsetMs: Long,
            onlyIfDue: Boolean,
        ): RefreshResult =
            lock.withLock {
                val weekKey = WeekKey.of(nowMs, utcOffsetMs)
                val state = repository.current()
                if (state.artists.isEmpty()) return@withLock RefreshResult.Skipped
                if (onlyIfDue && !DiscoveryRotation.needsRotation(state, weekKey)) return@withLock RefreshResult.Skipped

                val candidates = mutableListOf<DiscoveryTrack>()
                var failures = 0
                var firstError: String? = null
                for (artist in state.artists) {
                    when (val result = source.recentUploads(artist)) {
                        is LoadResult.Ok ->
                            result.value
                                .filter { DiscoveryRotation.isEligible(artist, it.title, it.artist, it.durationMs, it.createdAtMs, nowMs) }
                                .mapTo(candidates) { it.toDiscoveryTrack(artist) }

                        is LoadResult.Error -> {
                            failures++
                            if (firstError == null) firstError = result.message
                        }
                    }
                }
                // Nothing could be asked at all (offline, SoundCloud down): keep what is
                // there and try again later instead of replacing it with an empty week.
                if (failures == state.artists.size) return@withLock RefreshResult.Failed(firstError ?: "Keine Antwort von SoundCloud")

                var count = 0
                repository.update { current ->
                    DiscoveryRotation.rotate(current, candidates, weekKey, nowMs).also { count = it.playlist.size }
                }
                RefreshResult.Updated(count)
            }

        private fun TrackItem.toDiscoveryTrack(artist: String) =
            DiscoveryTrack(
                track = SavedTrack(id = id, title = title, artist = this.artist, artworkUrl = artworkUrl, durationMs = durationMs),
                artist = artist,
                uploadedAtMs = createdAtMs,
            )
    }
