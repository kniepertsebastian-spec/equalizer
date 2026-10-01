package com.hardbasseq.eq.discovery

import com.hardbasseq.eq.playlist.SavedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryRotationTest {
    private val now = 20_724L * 86_400_000L
    private val day = 86_400_000L

    private fun track(
        id: Long,
        artist: String,
        ageDays: Int,
    ) = DiscoveryTrack(
        track = SavedTrack(id = id, title = "$artist - Song $id", artist = "Uploader", durationMs = 200_000L),
        artist = artist,
        uploadedAtMs = now - ageDays * day,
    )

    private fun many(
        artist: String,
        firstId: Long,
        count: Int,
    ) = (0 until count).map { track(firstId + it, artist, ageDays = it + 1) }

    @Test
    fun `normalizes and dedupes artists and forces a rebuild`() {
        var state = DiscoveryState(weekKey = 5L)
        state = DiscoveryRotation.addArtist(state, "  Angerfist  ")
        state = DiscoveryRotation.addArtist(state, "angerfist")
        state = DiscoveryRotation.addArtist(state, "x")
        state = DiscoveryRotation.addArtist(state, "Miss  K8")

        assertEquals(listOf("Angerfist", "Miss K8"), state.artists)
        assertEquals(DiscoveryState.NEVER, state.weekKey)
    }

    @Test
    fun `the number of artists is capped`() {
        var state = DiscoveryState()
        for (i in 1..40) state = DiscoveryRotation.addArtist(state, "Artist $i")
        assertEquals(DiscoveryRotation.MAX_ARTISTS, state.artists.size)
    }

    @Test
    fun `removing an artist drops their suggestions`() {
        val state =
            DiscoveryState(
                artists = listOf("A", "B"),
                playlist = listOf(track(1, "A", 1), track(2, "B", 1)),
                pool = listOf(track(3, "A", 2)),
            )

        val result = DiscoveryRotation.removeArtist(state, "a")

        assertEquals(listOf("B"), result.artists)
        assertEquals(listOf(2L), result.playlist.map { it.track.id })
        assertTrue(result.pool.isEmpty())
    }

    @Test
    fun `rotation is needed for a new week or a forced rebuild, never without artists`() {
        assertFalse(DiscoveryRotation.needsRotation(DiscoveryState(), 10))
        val built = DiscoveryState(artists = listOf("A"), weekKey = 10)
        assertFalse(DiscoveryRotation.needsRotation(built, 10))
        assertTrue(DiscoveryRotation.needsRotation(built, 11))
        assertTrue(DiscoveryRotation.needsRotation(built.copy(weekKey = DiscoveryState.NEVER), 10))
    }

    @Test
    fun `eligibility needs the artist, a single song and a recent upload`() {
        fun ok(
            artist: String = "Angerfist",
            title: String = "Angerfist - Drum Go Bang",
            uploader: String = "Someone",
            duration: Long = 200_000,
            uploaded: Long = now - day,
        ) = DiscoveryRotation.isEligible(artist, title, uploader, duration, uploaded, now)

        assertTrue(ok())
        assertTrue("uploader carries the name", ok(title = "Drum Go Bang", uploader = "Angerfist"))
        assertFalse("not the artist", ok(title = "Somebody else - Song"))
        assertFalse("a DJ set", ok(duration = 3_600_000))
        assertFalse("zero duration", ok(duration = 0))
        assertFalse("too old", ok(uploaded = now - 60 * day))
        assertFalse("unknown date", ok(uploaded = 0))
    }

    @Test
    fun `rotation fills up to twenty, newest first, with artists taking turns`() {
        val candidates = many("A", 100, 30) + many("B", 200, 30)

        val state = DiscoveryRotation.rotate(DiscoveryState(artists = listOf("A", "B")), candidates, weekKey = 7, nowMs = now)

        assertEquals(DiscoveryRotation.TARGET_SIZE, state.playlist.size)
        val fromA = state.playlist.count { it.artist == "A" }
        assertEquals(10, fromA)
        // Newest first within an artist.
        val tracksOfA = state.playlist.filter { it.artist == "A" }
        assertEquals(listOf(100L, 101L), tracksOfA.take(2).map { it.track.id })
        assertEquals(7L, state.weekKey)
        assertEquals(now, state.updatedAtMs)
        val shownIds = state.playlist.map { it.track.id }
        assertEquals(shownIds.toSet(), state.seenIds.toSet())
        assertTrue(state.pool.isNotEmpty())
    }

    @Test
    fun `swiped away tracks never come back and seen tracks come last`() {
        val candidates = many("A", 100, 25)
        val first = DiscoveryRotation.rotate(DiscoveryState(artists = listOf("A")), candidates, 1, now)
        val firstShown = first.playlist.first()
        val dismissedId = firstShown.track.id
        val afterSwipe = DiscoveryRotation.dismiss(first, dismissedId)

        val next = DiscoveryRotation.rotate(afterSwipe, candidates, 2, now)

        assertFalse(dismissedId in next.playlist.map { it.track.id })
        // 25 candidates, 20 shown last week: the 5 not shown yet come first (and are all in).
        val shownLastWeek = first.playlist.map { it.track.id }.toSet()
        val fresh = next.playlist.filter { it.track.id !in shownLastWeek }
        assertEquals(5, fresh.size)
        // Only 5 are new, so the list is topped up with earlier ones - to the minimum, not the maximum.
        assertEquals(DiscoveryRotation.MIN_SIZE, next.playlist.size)
    }

    @Test
    fun `with few unseen tracks the list is topped up from earlier suggestions to the minimum`() {
        val candidates = many("A", 100, 18)
        val first = DiscoveryRotation.rotate(DiscoveryState(artists = listOf("A")), candidates, 1, now)
        assertEquals(18, first.playlist.size)

        // Next week: nothing new, the same 18 candidates, all seen.
        val next = DiscoveryRotation.rotate(first, candidates, 2, now)

        assertEquals(DiscoveryRotation.MIN_SIZE, next.playlist.size)
    }

    @Test
    fun `dismissing removes the track, remembers it and refills from the pool below the minimum`() {
        val playlist = many("A", 100, DiscoveryRotation.MIN_SIZE)
        val pool = many("A", 500, 3)
        val state = DiscoveryState(artists = listOf("A"), playlist = playlist, pool = pool)

        val result = DiscoveryRotation.dismiss(state, 100)

        assertFalse(100L in result.playlist.map { it.track.id })
        assertTrue(100L in result.dismissedIds)
        assertEquals(DiscoveryRotation.MIN_SIZE, result.playlist.size)
        assertEquals(2, result.pool.size)
    }

    @Test
    fun `dismissing above the minimum does not refill`() {
        val state = DiscoveryState(artists = listOf("A"), playlist = many("A", 100, 20), pool = many("A", 500, 3))

        val result = DiscoveryRotation.dismiss(state, 100)

        assertEquals(19, result.playlist.size)
        assertEquals(3, result.pool.size)
    }

    @Test
    fun `a dismissed track in the pool is never used as a refill`() {
        val playlist = many("A", 100, DiscoveryRotation.MIN_SIZE)
        val state = DiscoveryState(artists = listOf("A"), playlist = playlist, pool = many("A", 500, 2), dismissedIds = listOf(500L))

        val result = DiscoveryRotation.dismiss(state, 100)

        assertFalse(500L in result.playlist.map { it.track.id })
        assertTrue(501L in result.playlist.map { it.track.id })
    }

    @Test
    fun `state survives json and unreadable data reads as empty`() {
        val state = DiscoveryRotation.rotate(DiscoveryState(artists = listOf("A")), many("A", 1, 5), 3, now)
        assertEquals(state, DiscoveryStateJson.decode(DiscoveryStateJson.encode(state)))
        assertEquals(DiscoveryState(), DiscoveryStateJson.decode("{oops"))
        assertEquals(DiscoveryState(), DiscoveryStateJson.decode(null))
    }

    @Test
    fun `typing a genre switches to your own list and forces a rebuild`() {
        var state = DiscoveryState(genreMode = GenreMode.AUTO, weekKey = 3L)
        state = DiscoveryRotation.addGenre(state, "  Uptempo  Hardcore ")
        state = DiscoveryRotation.addGenre(state, "uptempo hardcore")
        state = DiscoveryRotation.addGenre(state, "x")

        assertEquals(listOf("uptempo hardcore"), state.genres)
        assertEquals(GenreMode.MANUAL, state.genreMode)
        assertEquals(DiscoveryState.NEVER, state.weekKey)

        state = DiscoveryRotation.removeGenre(state.copy(weekKey = 5L), "uptempo hardcore")
        assertTrue(state.genres.isEmpty())
        assertEquals(DiscoveryState.NEVER, state.weekKey)
    }

    @Test
    fun `the wanted genres follow the mode`() {
        val state = DiscoveryState(genres = listOf("gabber"), autoGenres = listOf("uptempo"))

        assertEquals(emptyList<String>(), DiscoveryRotation.wantedGenres(state.copy(genreMode = GenreMode.OFF)))
        assertEquals(listOf("gabber"), DiscoveryRotation.wantedGenres(state.copy(genreMode = GenreMode.MANUAL)))
        assertEquals(listOf("uptempo"), DiscoveryRotation.wantedGenres(state.copy(genreMode = GenreMode.AUTO)))
    }

    @Test
    fun `switching to like-my-music rereads the likes and rebuilds`() {
        val state = DiscoveryState(genreMode = GenreMode.OFF, autoGenresWeek = 9L, weekKey = 9L)

        val result = DiscoveryRotation.setGenreMode(state, GenreMode.AUTO)

        assertEquals(DiscoveryState.NEVER, result.autoGenresWeek)
        assertEquals(DiscoveryState.NEVER, result.weekKey)
        assertEquals(9L, DiscoveryRotation.setGenreMode(state, GenreMode.MANUAL).autoGenresWeek)
    }

    @Test
    fun `uploads that confirmed the genre come before ones without genre info`() {
        val unconfirmed = many("A", 100, 25).map { it.copy(genreConfirmed = false) }
        val confirmed = many("A", 200, 5).map { it.copy(genreConfirmed = true) }

        val state = DiscoveryRotation.rotate(DiscoveryState(artists = listOf("A")), unconfirmed + confirmed, 1, now)

        val firstFive = state.playlist.take(5).map { it.track.id }
        assertEquals((200L..204L).toSet(), firstFive.toSet())
        assertEquals(DiscoveryRotation.TARGET_SIZE, state.playlist.size)
    }

    @Test
    fun `old saved state without genre fields still reads and defaults to like-my-music`() {
        val raw = """{"artists":["A"],"playlist":[],"weekKey":3}"""

        val state = DiscoveryStateJson.decode(raw)

        assertEquals(GenreMode.AUTO, state.genreMode)
        assertEquals(listOf("A"), state.artists)
    }
}
