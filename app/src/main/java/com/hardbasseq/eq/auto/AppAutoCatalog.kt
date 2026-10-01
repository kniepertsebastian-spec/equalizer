package com.hardbasseq.eq.auto

import com.hardbasseq.eq.discovery.DiscoveryRepository
import com.hardbasseq.eq.integration.LoadResult
import com.hardbasseq.eq.integration.PlayerController
import com.hardbasseq.eq.playlist.PlaylistRepository
import com.hardbasseq.eq.playlist.SavedTrack
import com.soundcloud.equalizer.player.model.TrackItem
import com.soundcloud.equalizer.player.playback.AutoCatalog
import com.soundcloud.equalizer.player.playback.AutoNode
import com.soundcloud.equalizer.player.playback.AutoQueue
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// What the car (Android Auto) can browse: the playlists on the device, this week's
// "Interesting new uploads" and the signed-in account's likes. Lists in a car are kept
// short on purpose - scrolling them while driving is no use.
@Singleton
class AppAutoCatalog(
    private val playlistRepository: PlaylistRepository,
    private val discoveryRepository: DiscoveryRepository,
    // The signed-in account's likes; empty when signed out or when loading failed.
    private val likesLoader: suspend () -> List<TrackItem>,
    // A plain SoundCloud search (voice commands for something that is in no playlist).
    private val searchLoader: suspend (String) -> List<TrackItem>,
) : AutoCatalog {
    @Inject
    constructor(
        playlistRepository: PlaylistRepository,
        discoveryRepository: DiscoveryRepository,
        controller: PlayerController,
    ) : this(
        playlistRepository,
        discoveryRepository,
        { (controller.loadLikedTracks() as? LoadResult.Ok)?.value.orEmpty() },
        { query -> (controller.searchSoundCloud(query, SEARCH_LIMIT) as? LoadResult.Ok)?.value.orEmpty() },
    )

    // The likes are loaded from the network once per browse; starting a track reuses them.
    private var likes: List<TrackItem> = emptyList()

    override suspend fun children(parentId: String): List<AutoNode> =
        when (val id = AutoMediaId.parse(parentId)) {
            AutoMediaId.Root -> rootFolders()
            AutoMediaId.PlaylistsFolder ->
                playlistRepository.playlists
                    .first()
                    .sortedByDescending { it.createdAtMs }
                    .take(MAX_ENTRIES)
                    .map { AutoNode(AutoMediaId.playlistFolder(it.id), it.title, "${it.tracks.size} Titel", playable = false) }

            AutoMediaId.DiscoveryFolder ->
                discoveryRepository
                    .current()
                    .playlist
                    .take(MAX_ENTRIES)
                    .map { trackNode(AutoMediaId.discoveryTrack(it.track.id), it.track) }

            AutoMediaId.LikesFolder -> loadLikes().take(MAX_ENTRIES).map { trackNode(AutoMediaId.likedTrack(it.id), it) }
            is AutoMediaId.PlaylistFolder ->
                playlistRepository.playlists
                    .first()
                    .firstOrNull { it.id == id.playlistId }
                    ?.tracks
                    .orEmpty()
                    .take(MAX_ENTRIES)
                    .map { trackNode(AutoMediaId.playlistTrack(id.playlistId, it.id), it) }

            is AutoMediaId.Track, null -> emptyList()
        }

    override suspend fun queueFor(mediaId: String): AutoQueue? {
        val id = AutoMediaId.parse(mediaId) as? AutoMediaId.Track ?: return null
        val tracks =
            when (id.source) {
                AutoMediaId.Source.PLAYLIST ->
                    playlistRepository.playlists
                        .first()
                        .firstOrNull { it.id == id.playlistId }
                        ?.tracks
                        .orEmpty()
                        .take(MAX_ENTRIES)
                        .map { it.toTrackItem() }

                AutoMediaId.Source.DISCOVERY ->
                    discoveryRepository
                        .current()
                        .playlist
                        .take(MAX_ENTRIES)
                        .map { it.track.toTrackItem() }

                AutoMediaId.Source.LIKES -> likes.ifEmpty { loadLikes() }.take(MAX_ENTRIES)
            }
        val start = tracks.indexOfFirst { it.id == id.trackId }
        return if (start < 0) null else AutoQueue(tracks, start)
    }

    // Voice: a playlist whose name contains the words wins, then a SoundCloud search.
    // Nothing said ("play music") plays the newest playlist, or else the likes.
    override suspend fun queueForSearch(query: String): AutoQueue? {
        val words = query.trim()
        val all = playlistRepository.playlists.first().filter { it.tracks.isNotEmpty() }
        if (words.isEmpty()) {
            val newest = all.maxByOrNull { it.createdAtMs }
            if (newest != null) return AutoQueue(newest.tracks.take(MAX_ENTRIES).map { it.toTrackItem() }, 0)
            return likesQueue()
        }
        all.firstOrNull { it.title.contains(words, ignoreCase = true) }?.let { playlist ->
            return AutoQueue(playlist.tracks.take(MAX_ENTRIES).map { it.toTrackItem() }, 0)
        }
        val found = searchLoader(words).take(MAX_ENTRIES)
        return if (found.isEmpty()) null else AutoQueue(found, 0)
    }

    private suspend fun likesQueue(): AutoQueue? {
        val liked = likes.ifEmpty { loadLikes() }.take(MAX_ENTRIES)
        return if (liked.isEmpty()) null else AutoQueue(liked, 0)
    }

    private fun rootFolders() =
        listOf(
            AutoNode(AutoMediaId.PLAYLISTS, "Meine Playlists", playable = false),
            AutoNode(AutoMediaId.DISCOVERY, "Interesting new uploads", playable = false),
            AutoNode(AutoMediaId.LIKES, "Likes", playable = false),
        )

    private suspend fun loadLikes(): List<TrackItem> {
        val loaded = likesLoader()
        likes = loaded
        return loaded
    }

    private fun trackNode(
        mediaId: String,
        track: SavedTrack,
    ) = AutoNode(mediaId, track.title, track.artist, track.artworkUrl, playable = true)

    private fun trackNode(
        mediaId: String,
        track: TrackItem,
    ) = AutoNode(mediaId, track.title, track.artist, track.artworkUrl, playable = true)

    // No stream address on purpose: the player resolves a fresh one by id when it plays.
    private fun SavedTrack.toTrackItem() =
        TrackItem(id = id, title = title, artist = artist, artworkUrl = artworkUrl, streamUrl = null, durationMs = durationMs)

    private companion object {
        const val MAX_ENTRIES = 100
        const val SEARCH_LIMIT = 20
    }
}
