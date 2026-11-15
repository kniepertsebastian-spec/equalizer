package com.auracurve.app.ui.player

import com.auracurve.app.R
import com.auracurve.app.integration.LinkImportResult
import com.auracurve.app.integration.LoadResult
import com.auracurve.app.integration.PlayerController
import com.auracurve.app.integration.SpotifyPlaylistApi
import com.auracurve.app.link.LinkSource
import com.auracurve.app.link.ShareLink
import com.auracurve.app.link.SpotifyImportLedger
import com.auracurve.app.link.SpotifyImportPlan
import com.auracurve.app.link.SpotifyImportState
import com.auracurve.app.link.SpotifyPlaylist
import com.auracurve.app.link.SpotifyPlaylistPage
import com.auracurve.app.link.SpotifyTrackPage
import com.auracurve.app.link.TrackMatcher
import com.auracurve.app.link.TrackQuery
import com.auracurve.app.link.TrackQueryBuilder
import com.auracurve.app.player.model.TrackItem
import com.auracurve.app.playlist.PlaylistEditing
import com.auracurve.app.playlist.PlaylistRepository
import com.auracurve.app.playlist.SavedPlaylist
import com.auracurve.app.playlist.SavedTrack
import com.auracurve.app.playlist.SpotifyImportRepository
import com.auracurve.app.text.TextProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

// Flavor "full" (AuraCurve roadmap 02, 1.3): finds the link in pasted or shared text. A SoundCloud
// track plays right away, a SoundCloud playlist is saved; a Spotify / YouTube song is looked up on
// SoundCloud, a Spotify playlist becomes playlists of the songs found there. Moved out of
// PlayerViewModel so that the store flavor carries none of this.
class SoundCloudLinkImport
    @Inject
    constructor(
        private val controller: PlayerController,
        private val spotifyApi: SpotifyPlaylistApi,
        private val playlistRepository: PlaylistRepository,
        private val spotifyImportRepository: SpotifyImportRepository,
        private val texts: TextProvider,
    ) : LinkImport {
        private lateinit var session: LinkImportSession

        override val pending: Flow<PendingImport?> =
            spotifyImportRepository.pending.map { state -> state?.let { PendingImport(it.title, it.nextIndex, it.total) } }

        override fun attach(session: LinkImportSession) {
            this.session = session
        }

        override fun importText(text: String) {
            val url = ShareLink.extractUrl(text)
            if (url == null) {
                session.importState.value = ImportUiState(message = texts.get(R.string.import_no_link), isError = true)
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

            session.scope.launch {
                session.importState.value = ImportUiState(isLoading = true)
                when (val result = controller.resolveLink(url)) {
                    is LinkImportResult.Track -> {
                        controller.playQueue(listOf(result.track), 0)
                        session.importState.value = ImportUiState(message = texts.get(R.string.import_playing, result.track.title))
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
                        session.importState.value =
                            ImportUiState(message = texts.get(R.string.import_saved, result.title, result.tracks.size))
                    }

                    is LinkImportResult.Failed -> session.importState.value = ImportUiState(message = result.message, isError = true)
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
                session.scope.launch {
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
                        session.importState.value =
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
        override fun resume() {
            spotifyImport?.cancel()
            spotifyImport =
                session.scope.launch {
                    val saved = spotifyImportRepository.current() ?: return@launch
                    if (saved.isDone) spotifyImportRepository.clear() else runSpotifyImport(saved, readNote = "")
                }
        }

        /** Forgets the unfinished Spotify import (playlists already written stay). */
        override fun discard() {
            spotifyImport?.cancel()
            session.scope.launch {
                spotifyImportRepository.clear()
                session.importState.value = ImportUiState()
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
                session.importState.value =
                    ImportUiState(isLoading = true, message = texts.get(R.string.import_reading_spotify, index + 1, playlistIds.size))
                // Signed in to Spotify: its Web API gives all songs of the user's own playlists.
                if (controller.isSpotifySignedIn()) {
                    when (val viaApi = spotifyApi.fetchSpotifyPlaylistViaApi(id)) {
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
                    session.importState.value =
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
                    session.importState.value =
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
            session.importState.value =
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
            session.scope.launch {
                session.importState.value = ImportUiState(isLoading = true, message = texts.get(R.string.bridge_reading_title))
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
                session.importState.value = ImportUiState(isLoading = true, message = texts.get(R.string.bridge_searching, query.label))

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
                session.bridgeState.value = BridgeUiState(label = query.label, matches = matches, startedAutomatically = sure)
                val message =
                    if (sure) {
                        texts.get(R.string.bridge_found_started, matches.first().track.title)
                    } else {
                        texts.get(R.string.bridge_pick_from_list)
                    }
                session.importState.value = ImportUiState(message = message)
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

        private fun fail(message: String) {
            session.importState.value = ImportUiState(message = message, isError = true)
        }

        private fun TrackItem.toSaved() =
            SavedTrack(id = id, title = title, artist = artist, artworkUrl = artworkUrl, durationMs = durationMs)
    }

private const val CANDIDATE_LIMIT = 10
private const val MAX_SHOWN_MATCHES = 5

// A playlist import looks every song up one by one, so it is capped.
private const val MAX_SPOTIFY_TRACKS = 1_000
private const val MAX_LISTED_MISSING = 5

// Results scoring below this are noise, not candidates worth showing.
private const val MIN_SHOWN_SCORE = 0.3
