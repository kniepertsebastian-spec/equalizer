package com.hardbasseq.eq.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardbasseq.eq.R
import com.hardbasseq.eq.integration.LinkImportResult
import com.hardbasseq.eq.integration.LoadResult
import com.hardbasseq.eq.integration.PlayerController
import com.hardbasseq.eq.link.LinkSource
import com.hardbasseq.eq.link.ShareLink
import com.hardbasseq.eq.link.SpotifyImportLedger
import com.hardbasseq.eq.link.SpotifyImportPlan
import com.hardbasseq.eq.link.SpotifyImportState
import com.hardbasseq.eq.link.SpotifyPlaylist
import com.hardbasseq.eq.link.SpotifyPlaylistPage
import com.hardbasseq.eq.link.SpotifyTrackPage
import com.hardbasseq.eq.link.TrackMatcher
import com.hardbasseq.eq.link.TrackQuery
import com.hardbasseq.eq.link.TrackQueryBuilder
import com.hardbasseq.eq.playlist.PlaylistEditing
import com.hardbasseq.eq.playlist.PlaylistRepository
import com.hardbasseq.eq.playlist.SavedPlaylist
import com.hardbasseq.eq.playlist.SavedTrack
import com.hardbasseq.eq.playlist.SpotifyImportRepository
import com.hardbasseq.eq.text.TextProvider
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

@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val controller: PlayerController,
        private val playlistRepository: PlaylistRepository,
        private val spotifyImportRepository: SpotifyImportRepository,
        private val texts: TextProvider,
    ) : ViewModel() {
        val nowPlaying: StateFlow<NowPlaying?> = controller.nowPlaying
        val queue: StateFlow<List<TrackItem>> = controller.queue

        // A long Spotify import that did not finish (null when there is none).
        val pendingImport: StateFlow<SpotifyImportState?> =
            spotifyImportRepository.pending.stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

        // Set when a link was shared into the app from elsewhere, so the UI can bring
        // the player screen forward; cleared by consumeShowPlayerRequest().
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
            if (fromShare) {
                _showPlayerRequest.value = true
                _requestedTab.value = PlayerTab.PLAYLISTS
            }
            _bridgeState.value = null

            val url = ShareLink.extractUrl(text)
            if (url == null) {
                _importState.value = ImportUiState(message = texts.get(R.string.import_no_link), isError = true)
                return
            }
            when (ShareLink.classify(url)) {
                LinkSource.SOUNDCLOUD -> Unit
                LinkSource.SPOTIFY -> {
                    val playlistIds = ShareLink.extractUrls(text).mapNotNull { ShareLink.spotifyPlaylistId(it) }.distinct()
                    if (playlistIds.isNotEmpty()) return importSpotifyPlaylists(playlistIds)
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

                LinkSource.OTHER -> return fail(texts.get(R.string.import_only_soundcloud))
            }

            viewModelScope.launch {
                _importState.value = ImportUiState(isLoading = true)
                when (val result = controller.resolveLink(url)) {
                    is LinkImportResult.Track -> {
                        controller.playQueue(listOf(result.track), 0)
                        _importState.value = ImportUiState(message = texts.get(R.string.import_playing, result.track.title))
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
                        _importState.value = ImportUiState(message = texts.get(R.string.import_saved, result.title, result.tracks.size))
                    }

                    is LinkImportResult.Failed -> _importState.value = ImportUiState(message = result.message, isError = true)
                }
            }
        }

        private var spotifyImport: Job? = null

        // A Spotify playlist cannot be played from Spotify, but its list of songs is public:
        // every song is looked up on SoundCloud, and what is found becomes a playlist on
        // the device. Songs without a sure match are left out and named in the message.
        //
        // Spotify's public page lists only the first ~100 songs of a playlist. A longer one
        // can be split into parts of up to 100 in Spotify and shared together (several links
        // in one text): all parts are read and put together. Whatever the size, the songs are
        // looked up in blocks of 100: each block becomes its own playlist and the position is
        // saved after it, so an interrupted import goes on where it stopped.
        private fun importSpotifyPlaylists(playlistIds: List<String>) {
            spotifyImport?.cancel()
            spotifyImport =
                viewModelScope.launch {
                    val key = SpotifyImportPlan.keyFor(playlistIds)
                    val saved = spotifyImportRepository.current()
                    if (saved != null && saved.key == key && !saved.isDone) {
                        // The same links again after an interruption: no need to read them anew.
                        runSpotifyImport(saved, readNote = "")
                        return@launch
                    }
                    val read = readSpotifyPlaylists(playlistIds, key) ?: return@launch
                    // Songs imported from these links before are not imported again (sharing the
                    // same, only partly readable playlist twice used to repeat its first 100).
                    val presentIds =
                        playlistRepository.playlists
                            .first()
                            .flatMap { list -> list.tracks.map { it.id } }
                            .toSet()
                    val todo = SpotifyImportLedger.remaining(spotifyImportRepository.ledger(), key, read.first.tracks, presentIds)
                    if (todo.isEmpty()) {
                        _importState.value =
                            ImportUiState(
                                message = texts.get(R.string.import_nothing_new, read.first.total, read.second),
                            )
                        return@launch
                    }
                    val alreadyDone = read.first.total - todo.size
                    val title = if (alreadyDone > 0) texts.get(R.string.import_addendum_title, read.first.title) else read.first.title
                    val skippedNote = if (alreadyDone > 0) texts.get(R.string.import_skipped_note, alreadyDone) else ""
                    runSpotifyImport(SpotifyImportPlan.start(key, title, todo), readNote = read.second + skippedNote)
                }
        }

        /** Goes on with the saved, unfinished Spotify import. */
        fun resumeSpotifyImport() {
            spotifyImport?.cancel()
            spotifyImport =
                viewModelScope.launch {
                    val saved = spotifyImportRepository.current() ?: return@launch
                    if (saved.isDone) spotifyImportRepository.clear() else runSpotifyImport(saved, readNote = "")
                }
        }

        /** Forgets the unfinished Spotify import (playlists already written stay). */
        fun discardSpotifyImport() {
            spotifyImport?.cancel()
            viewModelScope.launch {
                spotifyImportRepository.clear()
                _importState.value = ImportUiState()
            }
        }

        // Reads the song lists of the links; null when nothing could be read (the reason is
        // shown). The second value is a note about parts that could not be read.
        private suspend fun readSpotifyPlaylists(
            playlistIds: List<String>,
            key: String,
        ): Pair<SpotifyImportState, String>? {
            val parts = mutableListOf<SpotifyPlaylist>()
            var lastError: String? = null
            // Songs of a playlist read from the public page: that page stops at 100.
            var cutOffByPage = false
            // Why the sign-in could not be used for a playlist (shown when the page falls short).
            var apiNote = ""
            playlistIds.forEachIndexed { index, id ->
                _importState.value =
                    ImportUiState(isLoading = true, message = texts.get(R.string.import_reading_spotify, index + 1, playlistIds.size))
                // Signed in to Spotify: its Web API gives all songs of the user's own playlists.
                if (controller.isSpotifySignedIn()) {
                    when (val viaApi = controller.fetchSpotifyPlaylistViaApi(id)) {
                        is LoadResult.Ok -> {
                            parts.add(viaApi.value)
                            return@forEachIndexed
                        }

                        is LoadResult.Error -> apiNote = texts.get(R.string.import_spotify_signin_note, viaApi.message)
                    }
                }
                when (val result = controller.fetchSpotifyPlaylistPage(id)) {
                    is LoadResult.Ok -> {
                        val page = SpotifyPlaylistPage.parse(result.value)
                        if (page == null) {
                            lastError = texts.get(R.string.import_spotify_unreadable)
                        } else {
                            parts.add(page)
                            if (page.tracks.size == SpotifyImportPlan.BATCH_SIZE) cutOffByPage = true
                        }
                    }

                    is LoadResult.Error -> lastError = result.message
                }
            }
            if (parts.isEmpty()) {
                fail(lastError ?: texts.get(R.string.import_spotify_failed))
                return null
            }
            // The same song in two parts is searched once.
            val wanted =
                parts
                    .flatMap { it.tracks }
                    .distinctBy { it.artist.lowercase() to it.title.lowercase() }
                    .take(MAX_SPOTIFY_TRACKS)
            val title =
                if (parts.size ==
                    1
                ) {
                    parts.first().title
                } else {
                    texts.get(R.string.import_title_more, parts.first().title, parts.size - 1)
                }
            val unreadable =
                if (parts.size <
                    playlistIds.size
                ) {
                    texts.get(R.string.import_parts_unreadable, playlistIds.size - parts.size)
                } else {
                    ""
                }
            // Spotify's page stops at 100 songs: a list of exactly that size is probably cut off.
            val cutOff = if (cutOffByPage) texts.get(R.string.import_cut_off_hint) + apiNote else ""
            return SpotifyImportPlan.start(key, title, wanted) to (unreadable + cutOff)
        }

        // Looks the songs up block by block (see importSpotifyPlaylists). Stops without
        // advancing when the lookups fail because of the connection, so that "continue" can
        // try this block again.
        private suspend fun runSpotifyImport(
            initial: SpotifyImportState,
            readNote: String,
        ) {
            var state = initial
            val totalParts = SpotifyImportPlan.totalParts(state.total)
            val missing = mutableListOf<String>()
            val seen = mutableSetOf<Long>()
            while (!state.isDone) {
                val batch = SpotifyImportPlan.nextBatch(state)
                val part = SpotifyImportPlan.nextPartNumber(state)
                val found = LinkedHashMap<Long, TrackItem>()
                // Song -> SoundCloud track (or NOT_FOUND) for the ledger; failed lookups are left out
                // so that they are tried again next time.
                val looked = mutableMapOf<String, Long>()
                var failed = 0
                for ((index, entry) in batch.withIndex()) {
                    val done = state.nextIndex + index + 1
                    _importState.value =
                        ImportUiState(
                            isLoading = true,
                            message = texts.get(R.string.import_part_progress, part, totalParts, done, state.total),
                        )
                    val query = TrackQueryBuilder.fromArtistAndTitle(entry.artist, entry.title)
                    when (val outcome = lookUp(query)) {
                        is Lookup.Failed -> failed++
                        is Lookup.NotFound -> {
                            missing.add(query.label)
                            looked[SpotifyImportLedger.songKey(entry)] = SpotifyImportLedger.NOT_FOUND
                        }

                        is Lookup.Hit -> {
                            looked[SpotifyImportLedger.songKey(entry)] = outcome.track.id
                            if (seen.add(outcome.track.id)) found[outcome.track.id] = outcome.track
                        }
                    }
                }
                if (failed > batch.size / 2) {
                    _importState.value =
                        ImportUiState(
                            message =
                                texts.get(R.string.import_interrupted, state.nextIndex, state.total),
                            isError = true,
                        )
                    spotifyImportRepository.save(state)
                    return
                }
                var wrote = false
                if (found.isNotEmpty()) {
                    val existing = playlistRepository.playlists.first()
                    val name = spotifyPlaylistName(state.title, part, totalParts)
                    val playlist =
                        PlaylistEditing
                            .create(name, existing, System.currentTimeMillis())
                            ?.copy(tracks = found.values.map { it.toSaved() })
                    if (playlist != null) {
                        playlistRepository.save(playlist)
                        wrote = true
                    }
                }
                state = SpotifyImportPlan.advance(state, batch.size, found.size, wrote)
                // After every block: the position is safe even if the app is closed now.
                spotifyImportRepository.record(state.key, looked)
                spotifyImportRepository.save(state)
            }
            spotifyImportRepository.clear()
            if (state.foundSoFar == 0) {
                fail(texts.get(R.string.import_none_found))
                return
            }
            val skipped =
                if (missing.isEmpty()) {
                    ""
                } else {
                    texts.get(R.string.import_missing_list, missing.take(MAX_LISTED_MISSING).joinToString(", ")) +
                        if (missing.size >
                            MAX_LISTED_MISSING
                        ) {
                            texts.get(R.string.import_missing_more, missing.size - MAX_LISTED_MISSING)
                        } else {
                            ""
                        }
                }
            val name = spotifyPlaylistName(state.title, 1, 1)
            val headline =
                if (totalParts <=
                    1
                ) {
                    texts.get(R.string.import_headline_one, name)
                } else {
                    texts.get(R.string.import_headline_many, state.title, state.partsWritten)
                }
            _importState.value =
                ImportUiState(message = texts.get(R.string.import_result, headline, state.foundSoFar, state.total, readNote, skipped))
        }

        private fun spotifyPlaylistName(
            title: String,
            part: Int,
            totalParts: Int,
        ) = SpotifyImportPlan.playlistName(
            title,
            part,
            totalParts,
            fromSpotify = texts.get(R.string.import_name_from_spotify),
            partOfTotal = { number, total -> texts.get(R.string.import_name_part, number, total) },
        )

        // What looking a song up on SoundCloud gave: a sure match, nothing, or an error.
        private sealed interface Lookup {
            data class Hit(
                val track: TrackItem,
            ) : Lookup

            data object NotFound : Lookup

            data object Failed : Lookup
        }

        private suspend fun lookUp(query: TrackQuery): Lookup {
            val candidates =
                when (val result = candidatesFor(query)) {
                    is LoadResult.Ok -> result.value
                    is LoadResult.Error -> return Lookup.Failed
                }
            val best = TrackMatcher.rank(query, candidates, { it.title }, { it.artist }).firstOrNull() ?: return Lookup.NotFound
            return if (TrackMatcher.isConfident(query, best.second)) Lookup.Hit(best.first) else Lookup.NotFound
        }

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
                fail(texts.get(R.string.bridge_single_only, service))
                return
            }
            viewModelScope.launch {
                _importState.value = ImportUiState(isLoading = true, message = texts.get(R.string.bridge_reading_title))
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
                _importState.value = ImportUiState(isLoading = true, message = texts.get(R.string.bridge_searching, query.label))

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
                    fail(texts.get(R.string.bridge_nothing_found, query.label))
                    return@launch
                }

                val sure = TrackMatcher.isConfident(query, matches.first().score)
                if (sure) controller.playQueue(listOf(matches.first().track), 0)
                _bridgeState.value = BridgeUiState(label = query.label, matches = matches, startedAutomatically = sure)
                val message =
                    if (sure) {
                        texts.get(R.string.bridge_found_started, matches.first().track.title)
                    } else {
                        texts.get(R.string.bridge_pick_from_list)
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
private const val SEARCH_RESULT_LIMIT = 30
private const val MAX_SHOWN_MATCHES = 5

// A playlist import looks every song up one by one, so it is capped.
private const val MAX_SPOTIFY_TRACKS = 1_000
private const val MAX_LISTED_MISSING = 5

// Results scoring below this are noise, not candidates worth showing.
private const val MIN_SHOWN_SCORE = 0.3
