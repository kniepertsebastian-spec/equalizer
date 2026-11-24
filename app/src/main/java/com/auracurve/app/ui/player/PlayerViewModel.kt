package com.auracurve.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auracurve.app.R
import com.auracurve.app.integration.LoadResult
import com.auracurve.app.integration.LocalTrackReader
import com.auracurve.app.integration.NoLocalTrackReader
import com.auracurve.app.integration.PlayerController
import com.auracurve.app.player.model.LibraryOverview
import com.auracurve.app.player.model.PlaylistItem
import com.auracurve.app.player.model.TrackItem
import com.auracurve.app.player.playback.NowPlaying
import com.auracurve.app.player.playback.PlayedTracksState
import com.auracurve.app.player.playback.RepeatSetting
import com.auracurve.app.playlist.LocalTrackId
import com.auracurve.app.playlist.PlaylistEditing
import com.auracurve.app.playlist.PlaylistRepository
import com.auracurve.app.playlist.SavedPlaylist
import com.auracurve.app.playlist.SavedTrack
import com.auracurve.app.text.TextProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

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

// The SoundCloud search tab: the last query, its results and what to say when there are none.
enum class SearchOutcome { IDLE, RESULTS, NO_RESULTS, FAILED }

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<TrackItem> = emptyList(),
    val outcome: SearchOutcome = SearchOutcome.IDLE,
    val errorDetail: String? = null,
)

// The Spotify sign-in as the player screen shows it: the client id of the user's own Spotify
// developer app and whether they are signed in (needed to read all songs of long playlists).
data class SpotifyAccountUi(
    val clientId: String = "",
    val signedIn: Boolean = false,
)

// The signed-in user's SoundCloud library as shown in the player screen.
data class LibraryUiState(
    val isLoading: Boolean = false,
    val library: LibraryOverview? = null,
    val message: String? = null,
    val isError: Boolean = false,
)

// Roadmap 17: which playlist the detail view shows. Saved playlists are followed by id so that
// edits (a removed track, a re-import) show up right away; SoundCloud lists are loaded once.
sealed interface OpenedPlaylistSource {
    data class Saved(
        val playlistId: String,
    ) : OpenedPlaylistSource

    data class Library(
        val playlist: PlaylistItem,
    ) : OpenedPlaylistSource

    data object Likes : OpenedPlaylistSource
}

// The opened playlist as the detail view shows it. Opening one only reads its tracks: the queue
// and what plays now stay untouched until "Play all", "Shuffle" or a track is tapped.
data class OpenedPlaylistUiState(
    val source: OpenedPlaylistSource,
    val title: String,
    // Where the list comes from and how many tracks it has, e.g. "SoundCloud · 12 tracks".
    val subtitle: String,
    val artworkUrl: String?,
    val tracks: List<TrackItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // True for playlists made on the device (no source link): only those can lose tracks here.
    val canRemoveTracks: Boolean = false,
    // The saved playlist behind a Saved source (for delete / send to SoundCloud), else null.
    val savedPlaylist: SavedPlaylist? = null,
)

@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val controller: PlayerController,
        private val playlistRepository: PlaylistRepository,
        private val texts: TextProvider,
        private val localTracks: LocalTrackReader = NoLocalTrackReader,
        // Pasted and shared links: SoundCloudLinkImport in "full", NoLinkImport in "store".
        private val linkImport: LinkImport = NoLinkImport(),
    ) : ViewModel() {
        val nowPlaying: StateFlow<NowPlaying?> = controller.nowPlaying
        val queue: StateFlow<List<TrackItem>> = controller.queue

        // A long Spotify import that did not finish (null when there is none).
        val pendingImport: StateFlow<PendingImport?> =
            linkImport.pending.stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

        private val _searchState = MutableStateFlow(SearchUiState())
        val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

        private val _bridgeState = MutableStateFlow<BridgeUiState?>(null)
        val bridgeState: StateFlow<BridgeUiState?> = _bridgeState.asStateFlow()

        private val _importState = MutableStateFlow(ImportUiState())
        val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

        init {
            linkImport.attach(LinkImportSession(viewModelScope, _importState, _bridgeState))
        }

        // Set when a link was shared into the app from elsewhere or the playback notification
        // was tapped, so the UI can bring the player screen forward; cleared by
        // consumeShowPlayerRequest().
        private val _showPlayerRequest = MutableStateFlow(false)
        val showPlayerRequest: StateFlow<Boolean> = _showPlayerRequest.asStateFlow()

        private val _spotifyAccount = MutableStateFlow(SpotifyAccountUi(controller.spotifyClientId(), controller.isSpotifySignedIn()))
        val spotifyAccount: StateFlow<SpotifyAccountUi> = _spotifyAccount.asStateFlow()

        fun saveSpotifyClientId(clientId: String) {
            controller.saveSpotifyClientId(clientId)
            refreshAccount()
        }

        fun signInSpotify() {
            // The client id typed in the field counts even if "Speichern" was not tapped.
            controller.openSpotifySignIn()?.let { problem -> fail(problem) }
        }

        fun signOutSpotify() {
            controller.signOutSpotify()
            refreshAccount()
        }

        // The sign-in happens in its own activity, so the screen re-reads the state
        // whenever it comes back to the foreground.
        fun refreshAccount() {
            _spotifyAccount.value = SpotifyAccountUi(controller.spotifyClientId(), controller.isSpotifySignedIn())
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

        private val _openedPlaylist = MutableStateFlow<OpenedPlaylistUiState?>(null)
        val openedPlaylist: StateFlow<OpenedPlaylistUiState?> = _openedPlaylist.asStateFlow()

        // The load (SoundCloud) or the watch (saved playlist) behind the opened playlist; replaced
        // whenever another one is opened, cancelled when it is closed, so a late answer for a list
        // that is no longer shown can never overwrite the current one.
        private var openedJob: Job? = null

        /** Opens a playlist saved on the device; it follows later edits and closes when it is deleted. */
        fun openSavedPlaylist(playlistId: String) {
            openedJob?.cancel()
            // Shown at once from what the list already holds; the watch below keeps it current.
            _openedPlaylist.value = playlists.value.firstOrNull { it.id == playlistId }?.let(::savedPlaylistState)
            openedJob =
                viewModelScope.launch {
                    playlistRepository.playlists
                        .map { all -> all.firstOrNull { it.id == playlistId } }
                        .takeWhile { it != null }
                        .collect { playlist -> _openedPlaylist.value = savedPlaylistState(playlist!!) }
                    // Deleted (here or elsewhere): back to the overview.
                    _openedPlaylist.value = null
                }
        }

        /** Opens a SoundCloud playlist of the library: its tracks are loaded, nothing is played. */
        fun openLibraryPlaylist(playlist: PlaylistItem) =
            openRemote(
                OpenedPlaylistUiState(
                    source = OpenedPlaylistSource.Library(playlist),
                    title = playlist.title,
                    subtitle = remoteSubtitle(playlist.trackCount),
                    artworkUrl = playlist.artworkUrl,
                ),
            )

        /** Opens the liked tracks of the SoundCloud account: loaded, nothing is played. */
        fun openLikedTracks() =
            openRemote(
                OpenedPlaylistUiState(
                    source = OpenedPlaylistSource.Likes,
                    title = texts.get(R.string.library_likes_title),
                    subtitle = texts.get(R.string.playlist_detail_source_likes),
                    artworkUrl = null,
                ),
            )

        fun closeOpenedPlaylist() {
            openedJob?.cancel()
            openedJob = null
            _openedPlaylist.value = null
        }

        /** Loads the opened SoundCloud list again after a failed load (offline, timeout). */
        fun retryOpenedPlaylist() {
            val opened = _openedPlaylist.value ?: return
            if (opened.source is OpenedPlaylistSource.Saved || opened.isLoading) return
            openRemote(opened.copy(tracks = emptyList(), errorMessage = null))
        }

        /**
         * Plays the opened playlist as the queue from [startIndex] (its place in the whole list, not
         * in a search result). [shuffle] turns shuffle on and starts at a random track.
         */
        fun playOpenedPlaylist(
            startIndex: Int = 0,
            shuffle: Boolean = false,
        ) {
            val tracks = _openedPlaylist.value?.tracks.orEmpty()
            if (tracks.isEmpty()) return
            if (shuffle) {
                controller.setShuffle(true)
                controller.playQueue(tracks, Random.nextInt(tracks.size))
            } else {
                controller.playQueue(tracks, startIndex.coerceIn(0, tracks.lastIndex))
            }
        }

        private fun openRemote(initial: OpenedPlaylistUiState) {
            openedJob?.cancel()
            _openedPlaylist.value = initial.copy(isLoading = true, errorMessage = null)
            openedJob =
                viewModelScope.launch {
                    val result =
                        when (val source = initial.source) {
                            is OpenedPlaylistSource.Library -> controller.loadPlaylistTracks(source.playlist.id)
                            OpenedPlaylistSource.Likes -> controller.loadLikedTracks()
                            is OpenedPlaylistSource.Saved -> return@launch
                        }
                    _openedPlaylist.value =
                        when (result) {
                            is LoadResult.Ok ->
                                initial.copy(
                                    tracks = result.value,
                                    isLoading = false,
                                    errorMessage = null,
                                    subtitle =
                                        if (initial.source is OpenedPlaylistSource.Likes) {
                                            texts.get(R.string.playlist_detail_subtitle_likes, result.value.size)
                                        } else {
                                            remoteSubtitle(result.value.size)
                                        },
                                )

                            is LoadResult.Error -> initial.copy(isLoading = false, errorMessage = result.message)
                        }
                }
        }

        private fun remoteSubtitle(count: Int) = texts.get(R.string.playlist_detail_subtitle_soundcloud, count)

        private fun savedPlaylistState(playlist: SavedPlaylist) =
            OpenedPlaylistUiState(
                source = OpenedPlaylistSource.Saved(playlist.id),
                title = playlist.title,
                subtitle =
                    texts.get(
                        if (playlist.sourceUrl ==
                            null
                        ) {
                            R.string.playlist_detail_subtitle_device
                        } else {
                            R.string.playlist_detail_subtitle_imported
                        },
                        playlist.tracks.size,
                    ),
                artworkUrl = playlist.tracks.firstOrNull()?.artworkUrl,
                tracks = playlist.tracks.map { it.toTrackItem() },
                canRemoveTracks = PlaylistEditing.isLocal(playlist),
                savedPlaylist = playlist,
            )

        fun playLikedTracks() = playLoaded(texts.get(R.string.library_likes_title)) { controller.loadLikedTracks() }

        fun playLibraryPlaylist(playlist: PlaylistItem) = playLoaded(playlist.title) { controller.loadPlaylistTracks(playlist.id) }

        // Loads tracks from the account and starts them as the queue; a long playlist
        // takes a moment, so the state shows what is being loaded.
        private fun playLoaded(
            name: String,
            load: suspend () -> LoadResult<List<TrackItem>>,
        ) {
            viewModelScope.launch {
                val before = _libraryState.value
                _libraryState.value =
                    before.copy(isLoading = true, message = texts.get(R.string.import_loading_name, name), isError = false)
                when (val result = load()) {
                    is LoadResult.Ok -> {
                        if (result.value.isEmpty()) {
                            _libraryState.value =
                                before.copy(isLoading = false, message = texts.get(R.string.import_name_empty, name), isError = true)
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

        /** Searches SoundCloud for [query]; blank queries are ignored. The state keeps the results for the search tab. */
        fun search(query: String) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                _searchState.value = SearchUiState(query = trimmed, isLoading = true)
                _searchState.value =
                    when (val result = controller.searchSoundCloud(trimmed, SEARCH_RESULT_LIMIT)) {
                        is LoadResult.Ok ->
                            SearchUiState(
                                query = trimmed,
                                results = result.value,
                                outcome = if (result.value.isEmpty()) SearchOutcome.NO_RESULTS else SearchOutcome.RESULTS,
                            )

                        is LoadResult.Error ->
                            SearchUiState(query = trimmed, outcome = SearchOutcome.FAILED, errorDetail = result.message)
                    }
            }
        }

        /** Plays the search results as the queue, starting with the tapped one. */
        fun playSearchResult(index: Int) {
            val results = _searchState.value.results
            if (index !in results.indices) return
            controller.playQueue(results, index)
        }

        fun signIn() = controller.openSoundCloudSignIn()

        fun signOut() {
            controller.signOutSoundCloud()
            refreshAccount()
        }

        // The tab the player screen should switch to (a shared link wants the playlists), once.
        private val _requestedTab = MutableStateFlow<PlayerTab?>(null)
        val requestedTab: StateFlow<PlayerTab?> = _requestedTab.asStateFlow()

        fun consumeRequestedTab() {
            _requestedTab.value = null
        }

        /** Switches the player page to [tab] (e.g. "Go to search" from the empty queue). */
        fun requestTab(tab: PlayerTab) {
            _requestedTab.value = tab
        }

        // "Now playing" should open full screen (notification, lock screen), once.
        private val _expandPlayerRequest = MutableStateFlow(false)
        val expandPlayerRequest: StateFlow<Boolean> = _expandPlayerRequest.asStateFlow()

        fun consumeExpandPlayerRequest() {
            _expandPlayerRequest.value = false
        }

        fun consumeShowPlayerRequest() {
            _showPlayerRequest.value = false
        }

        /**
         * The playback notification, the lock screen or the car was tapped (see PlayerLaunch):
         * bring the player screen forward. The text of [showPlayerRequest]'s comment applies,
         * only the trigger differs.
         */
        fun requestShowPlayer() {
            _showPlayerRequest.value = true
        }

        /** Like [requestShowPlayer], and "Now playing" opens full screen on top. */
        fun requestNowPlaying() {
            _showPlayerRequest.value = true
            _expandPlayerRequest.value = true
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
            if (fromShare) {
                _showPlayerRequest.value = true
                _requestedTab.value = PlayerTab.PLAYLISTS
            }
            _bridgeState.value = null

            linkImport.importText(text)
        }

        /** Goes on with the saved, unfinished Spotify import. */
        fun resumeSpotifyImport() = linkImport.resume()

        /** Forgets the unfinished Spotify import (playlists already written stay). */
        fun discardSpotifyImport() = linkImport.discard()

        /** Merges the chosen playlists (in the given order) into one new playlist; the originals stay. */
        fun mergePlaylists(
            sources: List<SavedPlaylist>,
            title: String,
        ) {
            viewModelScope.launch {
                val existing = playlistRepository.playlists.first()
                val merged = PlaylistEditing.merge(title, sources, existing, System.currentTimeMillis())
                if (merged == null) {
                    fail(texts.get(R.string.merge_needs_input))
                    return@launch
                }
                playlistRepository.save(merged)
                _importState.value =
                    ImportUiState(message = texts.get(R.string.merge_done, merged.title, merged.tracks.size, sources.size))
            }
        }

        fun playBridgeMatch(match: BridgeMatch) {
            controller.playQueue(listOf(match.track), 0)
        }

        fun dismissBridge() {
            _bridgeState.value = null
        }

        // P5-7: audio files picked on the device play as the queue, through the same chain as SoundCloud
        // (curve, virtual bass, mono bass, limiter) - nothing is looked up, the file is the stream.
        fun playLocalFiles(addresses: List<String>) {
            if (addresses.isEmpty()) return
            viewModelScope.launch {
                val tracks = localTracks.read(addresses)
                if (tracks.isEmpty()) {
                    fail(texts.get(R.string.local_files_unreadable))
                    return@launch
                }
                controller.playQueue(tracks, 0)
                if (tracks.size < addresses.size) {
                    _importState.value = ImportUiState(message = texts.get(R.string.local_files_some_skipped, addresses.size - tracks.size))
                }
            }
        }

        // Plays the playlist as the queue, from [startIndex] (a track picked in the opened list).
        fun playPlaylist(
            playlist: SavedPlaylist,
            startIndex: Int = 0,
        ) {
            if (playlist.tracks.isEmpty()) return
            controller.playQueue(playlist.tracks.map { it.toTrackItem() }, startIndex.coerceIn(0, playlist.tracks.lastIndex))
        }

        // The track that is playing right now (from the queue), null when nothing plays.
        fun currentTrack(): TrackItem? = queue.value.getOrNull(nowPlaying.value?.queueIndex ?: -1)

        /** Makes a playlist on the device, optionally starting with [firstTrack]. Blank names are refused. */
        fun createPlaylist(
            title: String,
            firstTrack: TrackItem? = null,
        ) {
            if (firstTrack != null && LocalTrackId.isLocal(firstTrack.id)) {
                fail(texts.get(R.string.local_files_not_in_playlists))
                return
            }
            viewModelScope.launch {
                val existing = playlistRepository.playlists.first()
                val playlist = PlaylistEditing.create(title, existing, System.currentTimeMillis(), firstTrack?.toSaved())
                if (playlist == null) {
                    fail(texts.get(R.string.playlist_name_needed))
                    return@launch
                }
                playlistRepository.save(playlist)
                _importState.value = ImportUiState(message = texts.get(R.string.playlist_created, playlist.title))
            }
        }

        fun addToPlaylist(
            playlist: SavedPlaylist,
            track: TrackItem,
        ) {
            if (LocalTrackId.isLocal(track.id)) {
                fail(texts.get(R.string.local_files_not_in_playlists))
                return
            }
            viewModelScope.launch {
                // Read again: the list shown may be a moment old.
                val latest = playlistRepository.playlists.first().firstOrNull { it.id == playlist.id } ?: return@launch
                if (PlaylistEditing.contains(latest, track.id)) {
                    _importState.value = ImportUiState(message = texts.get(R.string.playlist_already_in, track.title, latest.title))
                    return@launch
                }
                playlistRepository.save(PlaylistEditing.addTrack(latest, track.toSaved()))
                _importState.value = ImportUiState(message = texts.get(R.string.playlist_added_to, latest.title))
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

        /**
         * Sends the playlists to the signed-in SoundCloud account as private playlists; ones sent
         * before are updated in place (their SoundCloud id is kept). Empty playlists are skipped.
         */
        fun syncToSoundCloud(playlists: List<SavedPlaylist>) {
            if (!controller.isSoundCloudSignedIn()) {
                fail(texts.get(R.string.soundcloud_sign_in_first))
                return
            }
            val toSend = playlists.filter { it.tracks.isNotEmpty() }
            if (toSend.isEmpty()) {
                fail(texts.get(R.string.soundcloud_nothing_to_send))
                return
            }
            viewModelScope.launch {
                _importState.value = ImportUiState(isLoading = true)
                var sent = 0
                for (selected in toSend) {
                    val latest = playlistRepository.playlists.first().firstOrNull { it.id == selected.id } ?: continue
                    val result = controller.pushPlaylistToSoundCloud(latest.title, latest.tracks.map { it.id }, latest.soundCloudId)
                    if (result is LoadResult.Error) {
                        fail(texts.get(R.string.soundcloud_send_failed, latest.title, result.message))
                        return@launch
                    }
                    playlistRepository.save(latest.copy(soundCloudId = (result as LoadResult.Ok).value))
                    sent++
                }
                _importState.value = ImportUiState(message = texts.get(R.string.soundcloud_sent, sent))
            }
        }

        fun deletePlaylist(playlist: SavedPlaylist) {
            viewModelScope.launch { playlistRepository.delete(playlist.id) }
        }

        fun playQueueIndex(index: Int) = controller.skipToIndex(index)

        /** Puts [track] right after the one that plays now (or starts it when nothing is loaded). */
        fun playNext(track: TrackItem) = controller.playNext(listOf(track))

        /** Puts [track] at the end of the queue (or starts it when nothing is loaded). */
        fun addToQueue(track: TrackItem) = controller.addToQueue(listOf(track))

        fun playNextFromPlaylist(track: SavedTrack) = controller.playNext(listOf(track.toTrackItem()))

        fun addToQueueFromPlaylist(track: SavedTrack) = controller.addToQueue(listOf(track.toTrackItem()))

        /** Moves the queue entry at [from] to [to]; positions outside the queue are ignored. */
        fun moveQueueItem(
            from: Int,
            to: Int,
        ) {
            val size = queue.value.size
            if (from == to || from !in 0 until size || to !in 0 until size) return
            controller.moveQueueItem(from, to)
        }

        fun removeQueueItem(index: Int) {
            if (index in queue.value.indices) controller.removeQueueItem(index)
        }

        /**
         * Undo of a removal: [track] goes back to [index]. The player has no "insert at", so it
         * is appended and then moved; the service handles both requests in the order they are sent.
         */
        fun reinsertQueueItem(
            track: TrackItem,
            index: Int,
        ) {
            val appendedAt = queue.value.size
            controller.addToQueue(listOf(track))
            if (index in 0 until appendedAt) controller.moveQueueItem(appendedAt, index)
        }

        /** Removes everything after the track that plays now; returns what was removed (for Undo). */
        fun clearUpcoming(): List<TrackItem> {
            val current = nowPlaying.value?.queueIndex ?: return emptyList()
            val all = queue.value
            if (current !in all.indices || current == all.lastIndex) return emptyList()
            // One command for all of them: one per entry meant hundreds of commands (and as many
            // redraws of the list) for a long queue.
            controller.removeQueueItemsAfter(current)
            return all.subList(current + 1, all.size).toList()
        }

        /** Undo of [clearUpcoming]: the tracks go back to the end of the queue, in their order. */
        fun restoreUpcoming(tracks: List<TrackItem>) {
            if (tracks.isNotEmpty()) controller.addToQueue(tracks)
        }

        fun toggleShuffle() = controller.setShuffle(nowPlaying.value?.shuffle != true)

        /** Repeat steps through off -> whole queue -> this track -> off. */
        fun cycleRepeat() = controller.setRepeat(nextRepeat(nowPlaying.value?.repeat ?: RepeatSetting.OFF))

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

internal fun nextRepeat(current: RepeatSetting): RepeatSetting =
    when (current) {
        RepeatSetting.OFF -> RepeatSetting.ALL
        RepeatSetting.ALL -> RepeatSetting.ONE
        RepeatSetting.ONE -> RepeatSetting.OFF
    }

private const val SEARCH_RESULT_LIMIT = 30
