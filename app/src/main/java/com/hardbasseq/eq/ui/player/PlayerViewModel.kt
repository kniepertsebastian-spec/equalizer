package com.hardbasseq.eq.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardbasseq.eq.integration.LinkImportResult
import com.hardbasseq.eq.integration.LoadResult
import com.hardbasseq.eq.integration.PlayerController
import com.hardbasseq.eq.link.LinkSource
import com.hardbasseq.eq.link.ShareLink
import com.hardbasseq.eq.link.SpotifyPlaylistPage
import com.hardbasseq.eq.link.SpotifyTrackPage
import com.hardbasseq.eq.link.TrackMatcher
import com.hardbasseq.eq.link.TrackQuery
import com.hardbasseq.eq.link.TrackQueryBuilder
import com.hardbasseq.eq.playlist.PlaylistEditing
import com.hardbasseq.eq.playlist.PlaylistRepository
import com.hardbasseq.eq.playlist.SavedPlaylist
import com.hardbasseq.eq.playlist.SavedTrack
import com.soundcloud.equalizer.player.model.LibraryOverview
import com.soundcloud.equalizer.player.model.PlaylistItem
import com.soundcloud.equalizer.player.model.TrackItem
import com.soundcloud.equalizer.player.playback.NowPlaying
import com.soundcloud.equalizer.player.playback.PlayedTracksState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Feedback line under the link field: what the last import did (or why it could not).
data class ImportUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

// One SoundCloud search result for a song shared from YouTube / Spotify, with how well
// it fits (0..1, see TrackMatcher).
data class BridgeMatch(
    val track: TrackItem,
    val score: Double,
)

// What was found on SoundCloud for a shared YouTube / Spotify song. `startedAutomatically`
// is true when the best match was sure enough to start playing by itself.
data class BridgeUiState(
    val label: String,
    val matches: List<BridgeMatch>,
    val startedAutomatically: Boolean,
)

// The signed-in user's SoundCloud library as shown in the player screen.
data class LibraryUiState(
    val isLoading: Boolean = false,
    val library: LibraryOverview? = null,
    val message: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val controller: PlayerController,
        private val playlistRepository: PlaylistRepository,
    ) : ViewModel() {
        val nowPlaying: StateFlow<NowPlaying?> = controller.nowPlaying
        val queue: StateFlow<List<TrackItem>> = controller.queue

        // Ids of the tracks that were started in the player (this app session).
        val playedIds: StateFlow<Set<Long>> = PlayedTracksState.ids

        // Newest first.
        val playlists: StateFlow<List<SavedPlaylist>> =
            playlistRepository.playlists
                .map { list -> list.sortedByDescending { it.createdAtMs } }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        private val _signedIn = MutableStateFlow(controller.isSoundCloudSignedIn())
        val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

        private val _libraryState = MutableStateFlow(LibraryUiState())
        val libraryState: StateFlow<LibraryUiState> = _libraryState.asStateFlow()

        private val _bridgeState = MutableStateFlow<BridgeUiState?>(null)
        val bridgeState: StateFlow<BridgeUiState?> = _bridgeState.asStateFlow()

        private val _importState = MutableStateFlow(ImportUiState())
        val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

        // Set when a link was shared into the app from elsewhere, so the UI can bring
        // the player screen forward; cleared by consumeShowPlayerRequest().
        private val _showPlayerRequest = MutableStateFlow(false)
        val showPlayerRequest: StateFlow<Boolean> = _showPlayerRequest.asStateFlow()

        // The sign-in happens in its own activity, so the screen re-reads the state
        // whenever it comes back to the foreground.
        fun refreshAccount() {
            _signedIn.value = controller.isSoundCloudSignedIn()
            if (!_signedIn.value) _libraryState.value = LibraryUiState()
        }

        /** Loads (or reloads) the signed-in user's playlists; does nothing while signed out or already loading. */
        fun loadLibrary() {
            if (!controller.isSoundCloudSignedIn() || _libraryState.value.isLoading) return
            viewModelScope.launch {
                _libraryState.value = _libraryState.value.copy(isLoading = true, message = null, isError = false)
                _libraryState.value =
                    when (val result = controller.loadLibrary()) {
                        is LoadResult.Ok -> LibraryUiState(library = result.value)
                        is LoadResult.Error ->
                            _libraryState.value.copy(isLoading = false, message = result.message, isError = true)
                    }
            }
        }

        fun playLikedTracks() = playLoaded("Likes") { controller.loadLikedTracks() }

        fun playLibraryPlaylist(playlist: PlaylistItem) = playLoaded(playlist.title) { controller.loadPlaylistTracks(playlist.id) }

        // Loads tracks from the account and starts them as the queue; a long playlist
        // takes a moment, so the state shows what is being loaded.
        private fun playLoaded(
            name: String,
            load: suspend () -> LoadResult<List<TrackItem>>,
        ) {
            viewModelScope.launch {
                val before = _libraryState.value
                _libraryState.value = before.copy(isLoading = true, message = "Lade $name …", isError = false)
                when (val result = load()) {
                    is LoadResult.Ok -> {
                        if (result.value.isEmpty()) {
                            _libraryState.value = before.copy(isLoading = false, message = "$name ist leer", isError = true)
                        } else {
                            controller.playQueue(result.value, 0)
                            _libraryState.value = before.copy(isLoading = false, message = null, isError = false)
                        }
                    }

                    is LoadResult.Error ->
                        _libraryState.value = before.copy(isLoading = false, message = result.message, isError = true)
                }
            }
        }

        fun signIn() = controller.openSoundCloudSignIn()

        fun signOut() {
            controller.signOutSoundCloud()
            refreshAccount()
        }

        fun consumeShowPlayerRequest() {
            _showPlayerRequest.value = false
        }

        fun dismissImportMessage() {
            _importState.value = ImportUiState()
        }

        /**
         * Imports whatever text was pasted or shared: finds the link in it, and for a
         * SoundCloud link plays a single track right away or saves a playlist. Other
         * services are recognized only to explain why they cannot be played here.
         */
        fun importFromText(
            text: String,
            fromShare: Boolean = false,
        ) {
            if (fromShare) _showPlayerRequest.value = true
            _bridgeState.value = null

            val url = ShareLink.extractUrl(text)
            if (url == null) {
                _importState.value = ImportUiState(message = "Kein Link gefunden", isError = true)
                return
            }
            when (ShareLink.classify(url)) {
                LinkSource.SOUNDCLOUD -> Unit
                LinkSource.SPOTIFY -> {
                    ShareLink.spotifyPlaylistId(url)?.let { return importSpotifyPlaylist(it) }
                    val trackId = ShareLink.spotifyTrackId(url)
                    return bridgeExternalSong(
                        trackId?.let { ShareLink.canonicalSpotifyTrackUrl(it) },
                        isYouTube = false,
                        spotifyTrackId = trackId,
                    )
                }

                LinkSource.YOUTUBE -> {
                    val videoId = ShareLink.youtubeVideoId(url)
                    return bridgeExternalSong(videoId?.let { ShareLink.canonicalYouTubeUrl(it) }, isYouTube = true)
                }

                LinkSource.OTHER -> return fail("Nur SoundCloud-Links werden unterstützt")
            }

            viewModelScope.launch {
                _importState.value = ImportUiState(isLoading = true)
                when (val result = controller.resolveLink(url)) {
                    is LinkImportResult.Track -> {
                        controller.playQueue(listOf(result.track), 0)
                        _importState.value = ImportUiState(message = "Spielt: ${result.track.title}")
                    }

                    is LinkImportResult.Playlist -> {
                        playlistRepository.save(
                            SavedPlaylist(
                                // The link is the identity: importing it again refreshes
                                // the playlist instead of adding a duplicate.
                                id = result.sourceUrl,
                                title = result.title,
                                sourceUrl = result.sourceUrl,
                                tracks = result.tracks.map { it.toSaved() },
                                createdAtMs = System.currentTimeMillis(),
                            ),
                        )
                        _importState.value = ImportUiState(message = "Gespeichert: ${result.title} (${result.tracks.size} Titel)")
                    }

                    is LinkImportResult.Failed -> _importState.value = ImportUiState(message = result.message, isError = true)
                }
            }
        }

        private var spotifyImport: Job? = null

        // A Spotify playlist cannot be played from Spotify, but its list of songs is public:
        // every song is looked up on SoundCloud, and what is found becomes a playlist on
        // the device. Songs without a sure match are left out and named in the message.
        private fun importSpotifyPlaylist(playlistId: String) {
            spotifyImport?.cancel()
            spotifyImport =
                viewModelScope.launch {
                    _importState.value = ImportUiState(isLoading = true, message = "Lese die Spotify-Playlist …")
                    val page =
                        when (val result = controller.fetchSpotifyPlaylistPage(playlistId)) {
                            is LoadResult.Ok -> result.value
                            is LoadResult.Error -> return@launch fail(result.message)
                        }
                    val playlist = SpotifyPlaylistPage.parse(page)
                    if (playlist == null) {
                        fail("Die Titel dieser Spotify-Playlist konnten nicht gelesen werden (nur öffentliche Playlists)")
                        return@launch
                    }
                    val wanted = playlist.tracks.take(MAX_SPOTIFY_TRACKS)
                    val found = LinkedHashMap<Long, TrackItem>()
                    val missing = mutableListOf<String>()
                    wanted.forEachIndexed { index, entry ->
                        _importState.value =
                            ImportUiState(isLoading = true, message = "Suche auf SoundCloud … ${index + 1} von ${wanted.size}")
                        val query = TrackQueryBuilder.fromArtistAndTitle(entry.artist, entry.title)
                        val match = findOnSoundCloud(query)
                        if (match == null) missing.add(query.label) else found.putIfAbsent(match.id, match)
                    }
                    if (found.isEmpty()) {
                        fail("Nichts davon gibt es auf SoundCloud (oder die Suche ging nicht)")
                        return@launch
                    }
                    val existing = playlistRepository.playlists.first()
                    val saved =
                        PlaylistEditing
                            .create("${playlist.title} (von Spotify)", existing, System.currentTimeMillis())
                            ?.copy(tracks = found.values.map { it.toSaved() })
                    if (saved == null) {
                        fail("Die Playlist konnte nicht angelegt werden")
                        return@launch
                    }
                    playlistRepository.save(saved)
                    val skipped =
                        if (missing.isEmpty()) {
                            ""
                        } else {
                            ". Nicht gefunden: ${missing.take(MAX_LISTED_MISSING).joinToString(", ")}" +
                                if (missing.size > MAX_LISTED_MISSING) " und ${missing.size - MAX_LISTED_MISSING} weitere" else ""
                        }
                    _importState.value =
                        ImportUiState(message = "„${saved.title}“ angelegt: ${found.size} von ${wanted.size} Titeln gefunden$skipped")
                }
        }

        // The best sure match on SoundCloud for a song, or null when there is none (or
        // the search failed - one bad request must not stop a whole playlist).
        private suspend fun findOnSoundCloud(query: TrackQuery): TrackItem? {
            val candidates = (candidatesFor(query) as? LoadResult.Ok)?.value ?: return null
            val best = TrackMatcher.rank(query, candidates, { it.title }, { it.artist }).firstOrNull() ?: return null
            return best.first.takeIf { TrackMatcher.isConfident(query, best.second) }
        }

        // A song shared from YouTube / Spotify cannot be played from there (protected
        // streams), but what it is can be read from the link's public preview data and
        // looked up on SoundCloud. Only single songs: playlists, albums, channels and
        // long mixes have no single song to look for. `canonicalUrl` is null for those.
        private fun bridgeExternalSong(
            canonicalUrl: String?,
            isYouTube: Boolean,
            spotifyTrackId: String? = null,
        ) {
            if (canonicalUrl == null) {
                val service = if (isYouTube) "YouTube" else "Spotify"
                fail("Nur einzelne $service-Titel werden unterstützt - keine Playlists, Alben, Kanäle oder Kurzlinks")
                return
            }
            viewModelScope.launch {
                _importState.value = ImportUiState(isLoading = true, message = "Lese den Titel …")
                // Spotify's preview names only the song; its public track page also names the
                // artist, which makes the SoundCloud search (and the ranking) far more precise.
                val spotifyQuery = spotifyTrackId?.let { spotifyQueryFor(it) }
                val query =
                    spotifyQuery ?: run {
                        val info =
                            when (val result = controller.describeExternalLink(canonicalUrl)) {
                                is LoadResult.Ok -> result.value
                                is LoadResult.Error -> return@launch fail(result.message)
                            }
                        if (isYouTube) {
                            TrackQueryBuilder.fromYouTube(
                                info.title,
                                info.author,
                            )
                        } else {
                            TrackQueryBuilder.fromTitleOnly(info.title)
                        }
                    }
                _importState.value = ImportUiState(isLoading = true, message = "Suche „${query.label}“ auf SoundCloud …")

                val candidates =
                    when (val result = candidatesFor(query)) {
                        is LoadResult.Ok -> result.value
                        is LoadResult.Error -> return@launch fail(result.message)
                    }

                val matches =
                    TrackMatcher
                        .rank(query, candidates.distinctBy { it.id }, { it.title }, { it.artist })
                        .filter { it.second >= MIN_SHOWN_SCORE }
                        .take(MAX_SHOWN_MATCHES)
                        .map { BridgeMatch(track = it.first, score = it.second) }
                if (matches.isEmpty()) {
                    fail("Nichts Passendes auf SoundCloud gefunden für „${query.label}“")
                    return@launch
                }

                val sure = TrackMatcher.isConfident(query, matches.first().score)
                if (sure) controller.playQueue(listOf(matches.first().track), 0)
                _bridgeState.value = BridgeUiState(label = query.label, matches = matches, startedAutomatically = sure)
                val message =
                    if (sure) {
                        "Gefunden und gestartet: ${matches.first().track.title}"
                    } else {
                        "Kein sicherer Treffer - wähle einen aus der Liste"
                    }
                _importState.value = ImportUiState(message = message)
            }
        }

        // Artist and song of a Spotify track from its public page, null when that cannot be read.
        private suspend fun spotifyQueryFor(trackId: String): TrackQuery? {
            val page = (controller.fetchSpotifyTrackPage(trackId) as? LoadResult.Ok)?.value ?: return null
            val track = SpotifyTrackPage.parse(page) ?: return null
            return TrackQueryBuilder.fromArtistAndTitle(track.artist, track.title)
        }

        // SoundCloud results worth ranking for a song. "Artist title" goes first; when that
        // gives nothing sure and the artist is known, a search by title alone adds the
        // tracks the uploader named differently (the ranking still weighs the artist).
        private suspend fun candidatesFor(query: TrackQuery): LoadResult<List<TrackItem>> {
            val first = controller.searchSoundCloud(query.searchText, CANDIDATE_LIMIT)
            if (first !is LoadResult.Ok) return first
            var all = first.value.distinctBy { it.id }
            if (query.artist != null) {
                val best = TrackMatcher.rank(query, all, { it.title }, { it.artist }).firstOrNull()
                if (best == null || !TrackMatcher.isConfident(query, best.second)) {
                    val more = controller.searchSoundCloud(query.title, CANDIDATE_LIMIT)
                    if (more is LoadResult.Ok) all = (all + more.value).distinctBy { it.id }
                }
            }
            return LoadResult.Ok(all)
        }

        fun playBridgeMatch(match: BridgeMatch) {
            controller.playQueue(listOf(match.track), 0)
        }

        fun dismissBridge() {
            _bridgeState.value = null
        }

        fun playPlaylist(playlist: SavedPlaylist) {
            controller.playQueue(playlist.tracks.map { it.toTrackItem() }, 0)
        }

        // The track that is playing right now (from the queue), null when nothing plays.
        fun currentTrack(): TrackItem? = queue.value.getOrNull(nowPlaying.value?.queueIndex ?: -1)

        /** Makes a playlist on the device, optionally starting with [firstTrack]. Blank names are refused. */
        fun createPlaylist(
            title: String,
            firstTrack: TrackItem? = null,
        ) {
            viewModelScope.launch {
                val existing = playlistRepository.playlists.first()
                val playlist = PlaylistEditing.create(title, existing, System.currentTimeMillis(), firstTrack?.toSaved())
                if (playlist == null) {
                    fail("Bitte einen Namen eingeben")
                    return@launch
                }
                playlistRepository.save(playlist)
                _importState.value = ImportUiState(message = "Playlist „${playlist.title}“ angelegt")
            }
        }

        fun addToPlaylist(
            playlist: SavedPlaylist,
            track: TrackItem,
        ) {
            viewModelScope.launch {
                // Read again: the list shown may be a moment old.
                val latest = playlistRepository.playlists.first().firstOrNull { it.id == playlist.id } ?: return@launch
                if (PlaylistEditing.contains(latest, track.id)) {
                    _importState.value = ImportUiState(message = "„${track.title}“ ist schon in „${latest.title}“")
                    return@launch
                }
                playlistRepository.save(PlaylistEditing.addTrack(latest, track.toSaved()))
                _importState.value = ImportUiState(message = "Zu „${latest.title}“ hinzugefügt")
            }
        }

        fun removeFromPlaylist(
            playlist: SavedPlaylist,
            trackId: Long,
        ) {
            if (!PlaylistEditing.isLocal(playlist)) return
            viewModelScope.launch {
                val latest = playlistRepository.playlists.first().firstOrNull { it.id == playlist.id } ?: return@launch
                playlistRepository.save(PlaylistEditing.removeTrack(latest, trackId))
            }
        }

        fun deletePlaylist(playlist: SavedPlaylist) {
            viewModelScope.launch { playlistRepository.delete(playlist.id) }
        }

        fun playQueueIndex(index: Int) = controller.skipToIndex(index)

        fun next() = controller.next()

        fun previous() = controller.previous()

        fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

        fun togglePlayback() = controller.togglePlayback()

        private fun fail(message: String) {
            _importState.value = ImportUiState(message = message, isError = true)
        }

        private fun TrackItem.toSaved() =
            SavedTrack(id = id, title = title, artist = artist, artworkUrl = artworkUrl, durationMs = durationMs)

        // No stream URL on purpose - the player resolves a fresh one by id when the
        // track plays.
        private fun SavedTrack.toTrackItem() =
            TrackItem(id = id, title = title, artist = artist, artworkUrl = artworkUrl, streamUrl = null, durationMs = durationMs)
    }

private const val CANDIDATE_LIMIT = 10
private const val MAX_SHOWN_MATCHES = 5

// A playlist import looks every song up one by one, so it is capped.
private const val MAX_SPOTIFY_TRACKS = 150
private const val MAX_LISTED_MISSING = 5

// Results scoring below this are noise, not candidates worth showing.
private const val MIN_SHOWN_SCORE = 0.3
