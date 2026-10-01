package com.hardbasseq.eq.discovery

import com.hardbasseq.eq.link.TrackMatcher

// The rules of the weekly "Interesting new uploads" playlist, free of Android and
// network so they can be tested: which uploads qualify, how a week's playlist is
// picked, what happens when one is swiped away.
object DiscoveryRotation {
    const val TARGET_SIZE = 20
    const val MIN_SIZE = 15
    const val MAX_ARTISTS = 30
    const val MAX_POOL = 60
    const val MAX_SEEN = 400
    const val MAX_DISMISSED = 1000

    // "New" means uploaded within this long; older finds are not suggestions.
    const val MAX_AGE_MS = 35L * 24 * 60 * 60 * 1000

    // Single songs only: longer uploads are DJ sets, mixes and compilations.
    const val MAX_TRACK_MS = 10L * 60 * 1000

    fun normalizeArtist(raw: String): String? =
        raw
            .trim()
            .replace(Regex("\\s+"), " ")
            .takeIf { it.length in 2..60 }

    // Adding an artist forces a rebuild, so their uploads show up right away.
    fun addArtist(
        state: DiscoveryState,
        raw: String,
    ): DiscoveryState {
        val name = normalizeArtist(raw) ?: return state
        if (state.artists.any { it.equals(name, ignoreCase = true) } || state.artists.size >= MAX_ARTISTS) return state
        return state.copy(artists = state.artists + name, weekKey = DiscoveryState.NEVER)
    }

    fun removeArtist(
        state: DiscoveryState,
        name: String,
    ): DiscoveryState =
        state.copy(
            artists = state.artists.filterNot { it.equals(name, ignoreCase = true) },
            playlist = state.playlist.filterNot { it.artist.equals(name, ignoreCase = true) },
            pool = state.pool.filterNot { it.artist.equals(name, ignoreCase = true) },
        )

    const val MAX_GENRES = 10

    // Genre settings change what qualifies, so they force a rebuild like a new artist.
    fun setGenreMode(
        state: DiscoveryState,
        mode: GenreMode,
    ): DiscoveryState =
        state.copy(
            genreMode = mode,
            weekKey = DiscoveryState.NEVER,
            // Switching to "like my music" re-reads the likes.
            autoGenresWeek = if (mode == GenreMode.AUTO) DiscoveryState.NEVER else state.autoGenresWeek,
        )

    // Typing a genre means choosing your own list.
    fun addGenre(
        state: DiscoveryState,
        raw: String,
    ): DiscoveryState {
        val name = GenreMatcher.normalize(raw).takeIf { it.length in 2..40 } ?: return state
        if (name in state.genres || state.genres.size >= MAX_GENRES) return state
        return state.copy(genres = state.genres + name, genreMode = GenreMode.MANUAL, weekKey = DiscoveryState.NEVER)
    }

    fun removeGenre(
        state: DiscoveryState,
        genre: String,
    ): DiscoveryState = state.copy(genres = state.genres - genre, weekKey = DiscoveryState.NEVER)

    fun withAutoGenres(
        state: DiscoveryState,
        genres: List<String>,
        weekKey: Long,
    ): DiscoveryState = state.copy(autoGenres = genres, autoGenresWeek = weekKey)

    // The genres uploads have to fit right now; empty means no filtering.
    fun wantedGenres(state: DiscoveryState): List<String> =
        when (state.genreMode) {
            GenreMode.OFF -> emptyList()
            GenreMode.MANUAL -> state.genres
            GenreMode.AUTO -> state.autoGenres
        }

    fun needsRotation(
        state: DiscoveryState,
        weekKey: Long,
    ): Boolean = state.artists.isNotEmpty() && state.weekKey != weekKey

    // Whether an upload found for `artist` is a suggestion at all: it names the artist
    // (in the title or the uploader - re-uploads by others are the point), is a single
    // song, and is recent. Uploads with an unknown date are not trusted as new.
    fun isEligible(
        artist: String,
        title: String,
        uploader: String,
        durationMs: Long,
        uploadedAtMs: Long,
        nowMs: Long,
    ): Boolean =
        TrackMatcher.mentionsArtist(artist, title, uploader) &&
            durationMs in 1..MAX_TRACK_MS &&
            uploadedAtMs > 0 &&
            nowMs - uploadedAtMs <= MAX_AGE_MS

    // Builds the playlist for a new week from freshly found candidates: never anything
    // swiped away, unseen tracks first, artists taking turns so one prolific uploader
    // does not fill the list, and - when too few unseen tracks exist - topped up with
    // earlier suggestions so the list stays at 15-20.
    fun rotate(
        state: DiscoveryState,
        candidates: List<DiscoveryTrack>,
        weekKey: Long,
        nowMs: Long,
    ): DiscoveryState {
        val dismissed = state.dismissedIds.toSet()
        val usable = candidates.filter { it.track.id !in dismissed }.distinctBy { it.track.id }
        val seen = state.seenIds.toSet()
        val unseen = usable.filter { it.track.id !in seen }
        val seenBefore = usable.filter { it.track.id in seen }

        val picked = interleaveConfirmedFirst(unseen).take(TARGET_SIZE).toMutableList()
        if (picked.size < MIN_SIZE) {
            val pickedIds = picked.map { it.track.id }.toSet()
            val filler = interleaveConfirmedFirst(seenBefore).filter { it.track.id !in pickedIds }
            picked.addAll(filler.take(MIN_SIZE - picked.size))
        }

        val pickedIds = picked.map { it.track.id }.toSet()
        val pool =
            usable
                .filter { it.track.id !in pickedIds }
                .sortedWith(compareByDescending<DiscoveryTrack> { it.genreConfirmed }.thenByDescending { it.uploadedAtMs })
                .take(MAX_POOL)
        return state.copy(
            playlist = picked,
            pool = pool,
            seenIds = (state.seenIds + picked.map { it.track.id }).distinct().takeLast(MAX_SEEN),
            weekKey = weekKey,
            updatedAtMs = nowMs,
        )
    }

    // Swiping a track away: it never returns, and the gap is filled from the held-back
    // candidates while the list is below its minimum.
    fun dismiss(
        state: DiscoveryState,
        trackId: Long,
    ): DiscoveryState {
        val dismissed = (state.dismissedIds + trackId).distinct().takeLast(MAX_DISMISSED)
        val playlist = state.playlist.filterNot { it.track.id == trackId }.toMutableList()
        val pool = state.pool.filterNot { it.track.id == trackId || it.track.id in dismissed }.toMutableList()
        while (playlist.size < MIN_SIZE && pool.isNotEmpty()) playlist.add(pool.removeAt(0))
        return state.copy(
            playlist = playlist,
            pool = pool,
            dismissedIds = dismissed,
            seenIds = (state.seenIds + playlist.map { it.track.id }).distinct().takeLast(MAX_SEEN),
        )
    }

    // Uploads that positively fit the wanted genres come first; ones with no genre info
    // (neither fitting nor clashing) only after them.
    private fun interleaveConfirmedFirst(tracks: List<DiscoveryTrack>): List<DiscoveryTrack> =
        interleave(tracks.filter { it.genreConfirmed }) + interleave(tracks.filterNot { it.genreConfirmed })

    // Round-robin over the artists, each artist's uploads newest first.
    private fun interleave(tracks: List<DiscoveryTrack>): List<DiscoveryTrack> {
        val byArtist =
            tracks
                .groupBy { it.artist.lowercase() }
                .values
                .map { group -> group.sortedByDescending { it.uploadedAtMs } }
                .sortedByDescending { group -> group.first().uploadedAtMs }
        val result = mutableListOf<DiscoveryTrack>()
        var round = 0
        while (true) {
            val row = byArtist.mapNotNull { it.getOrNull(round) }
            if (row.isEmpty()) break
            result.addAll(row)
            round++
        }
        return result
    }
}
