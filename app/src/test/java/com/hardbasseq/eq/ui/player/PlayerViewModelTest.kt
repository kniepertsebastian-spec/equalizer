package com.hardbasseq.eq.ui.player

import com.hardbasseq.eq.integration.LinkImportResult
import com.hardbasseq.eq.integration.PlayerController
import com.hardbasseq.eq.playlist.PlaylistRepository
import com.hardbasseq.eq.playlist.SavedPlaylist
import com.soundcloud.equalizer.player.model.TrackItem
import com.soundcloud.equalizer.player.playback.NowPlaying
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val controller = FakePlayerController()
    private val repository = FakePlaylistRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PlayerViewModel(controller, repository)

    private fun track(
        id: Long,
        title: String = "Track $id",
    ) = TrackItem(
        id = id,
        title = title,
        artist = "Artist",
        artworkUrl = "https://img/$id-large.jpg",
        streamUrl = null,
        durationMs = 180_000L,
    )

    @Test
    fun `a playlist link is saved under its link and not played`() =
        runTest {
            controller.nextResult =
                LinkImportResult.Playlist("Uptempo Mix", "https://soundcloud.com/a/sets/mix", listOf(track(1), track(2)))
            val vm = viewModel()

            vm.importFromText("Hör mal: https://soundcloud.com/a/sets/mix?si=abc")
            dispatcher.scheduler.advanceUntilIdle()

            // The controller gets the extracted link (query included - normalizing it is
            // the resolver's job); the saved playlist is keyed by the resolved source url.
            assertEquals("https://soundcloud.com/a/sets/mix?si=abc", controller.resolvedUrls.single())
            val saved = vm.playlists.value.single()
            assertEquals("https://soundcloud.com/a/sets/mix", saved.id)
            assertEquals("Uptempo Mix", saved.title)
            assertEquals(listOf(1L, 2L), saved.tracks.map { it.id })
            assertTrue(controller.playedQueues.isEmpty())
            assertFalse(vm.importState.value.isError)
            assertEquals("Gespeichert: Uptempo Mix (2 Titel)", vm.importState.value.message)
        }

    @Test
    fun `importing the same playlist again replaces it instead of duplicating`() =
        runTest {
            val vm = viewModel()
            controller.nextResult = LinkImportResult.Playlist("Mix", "https://soundcloud.com/a/sets/mix", listOf(track(1)))
            vm.importFromText("https://soundcloud.com/a/sets/mix")
            dispatcher.scheduler.advanceUntilIdle()

            controller.nextResult =
                LinkImportResult.Playlist("Mix", "https://soundcloud.com/a/sets/mix", listOf(track(1), track(2), track(3)))
            vm.importFromText("https://soundcloud.com/a/sets/mix")
            dispatcher.scheduler.advanceUntilIdle()

            val playlist = vm.playlists.value.single()
            assertEquals(3, playlist.tracks.size)
        }

    @Test
    fun `a track link starts playing right away`() =
        runTest {
            controller.nextResult = LinkImportResult.Track(track(7, "Kick"))
            val vm = viewModel()

            vm.importFromText("https://soundcloud.com/a/kick")
            dispatcher.scheduler.advanceUntilIdle()

            val (tracks, start) = controller.playedQueues.single()
            assertEquals(listOf(7L), tracks.map { it.id })
            assertEquals(0, start)
            assertTrue(vm.playlists.value.isEmpty())
            assertEquals("Spielt: Kick", vm.importState.value.message)
        }

    @Test
    fun `spotify and youtube links are refused with an explanation and never resolved`() =
        runTest {
            val vm = viewModel()

            vm.importFromText("https://open.spotify.com/playlist/37i9dQ")
            assertTrue(vm.importState.value.isError)
            val spotifyState = vm.importState.value
            val spotifyMessage = spotifyState.message.orEmpty()
            assertTrue(spotifyMessage.contains("Spotify"))

            vm.importFromText("https://music.youtube.com/playlist?list=PL1")
            assertTrue(vm.importState.value.isError)
            val youtubeState = vm.importState.value
            val youtubeMessage = youtubeState.message.orEmpty()
            assertTrue(youtubeMessage.contains("YouTube"))

            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(controller.resolvedUrls.isEmpty())
        }

    @Test
    fun `text without a link and unknown hosts are rejected`() =
        runTest {
            val vm = viewModel()

            vm.importFromText("kein Link hier")
            assertEquals("Kein Link gefunden", vm.importState.value.message)

            vm.importFromText("https://example.com/song")
            assertTrue(vm.importState.value.isError)
            assertTrue(controller.resolvedUrls.isEmpty())
        }

    @Test
    fun `a failed resolve surfaces its message as an error`() =
        runTest {
            controller.nextResult = LinkImportResult.Failed("Die Playlist ist leer oder privat")
            val vm = viewModel()

            vm.importFromText("https://soundcloud.com/a/sets/private")
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(vm.importState.value.isError)
            assertEquals("Die Playlist ist leer oder privat", vm.importState.value.message)
            assertTrue(vm.playlists.value.isEmpty())
        }

    @Test
    fun `a shared link asks the ui to show the player until consumed`() =
        runTest {
            val vm = viewModel()
            assertFalse(vm.showPlayerRequest.value)

            vm.importFromText("https://open.spotify.com/track/1", fromShare = true)
            assertTrue(vm.showPlayerRequest.value)

            vm.consumeShowPlayerRequest()
            assertFalse(vm.showPlayerRequest.value)
        }

    @Test
    fun `playing a saved playlist queues its tracks without stream urls`() =
        runTest {
            controller.nextResult = LinkImportResult.Playlist("Mix", "https://soundcloud.com/a/sets/mix", listOf(track(1), track(2)))
            val vm = viewModel()
            vm.importFromText("https://soundcloud.com/a/sets/mix")
            dispatcher.scheduler.advanceUntilIdle()

            vm.playPlaylist(vm.playlists.value.single())

            val (tracks, start) = controller.playedQueues.single()
            assertEquals(listOf(1L, 2L), tracks.map { it.id })
            assertTrue(tracks.all { it.streamUrl == null })
            assertEquals(0, start)
        }

    @Test
    fun `deleting removes a saved playlist`() =
        runTest {
            controller.nextResult = LinkImportResult.Playlist("Mix", "https://soundcloud.com/a/sets/mix", listOf(track(1)))
            val vm = viewModel()
            vm.importFromText("https://soundcloud.com/a/sets/mix")
            dispatcher.scheduler.advanceUntilIdle()
            assertNotNull(vm.playlists.value.singleOrNull())

            vm.deletePlaylist(vm.playlists.value.single())
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(vm.playlists.value.isEmpty())
        }

    @Test
    fun `transport controls are forwarded to the player`() =
        runTest {
            val vm = viewModel()

            vm.next()
            vm.previous()
            vm.seekTo(42_000L)
            vm.togglePlayback()
            vm.playQueueIndex(3)

            assertEquals(listOf("next", "previous", "seek:42000", "toggle", "skip:3"), controller.commands)
        }

    @Test
    fun `now playing is passed through from the player`() =
        runTest {
            val vm = viewModel()
            assertNull(vm.nowPlaying.value)

            controller.nowPlayingFlow.value = NowPlaying(title = "Kick", artist = "DJ", isPlaying = true, durationMs = 1000L)

            assertEquals("Kick", vm.nowPlaying.value?.title)
        }

    private class FakePlayerController : PlayerController {
        val nowPlayingFlow = MutableStateFlow<NowPlaying?>(null)
        override val nowPlaying: StateFlow<NowPlaying?> = nowPlayingFlow.asStateFlow()
        override val queue: StateFlow<List<TrackItem>> = MutableStateFlow(emptyList<TrackItem>()).asStateFlow()

        var nextResult: LinkImportResult = LinkImportResult.Failed("not configured")
        val resolvedUrls = mutableListOf<String>()
        val playedQueues = mutableListOf<Pair<List<TrackItem>, Int>>()
        val commands = mutableListOf<String>()

        override fun playQueue(
            tracks: List<TrackItem>,
            startIndex: Int,
        ) {
            playedQueues.add(tracks to startIndex)
        }

        override fun skipToIndex(index: Int) {
            commands.add("skip:$index")
        }

        override fun next() {
            commands.add("next")
        }

        override fun previous() {
            commands.add("previous")
        }

        override fun seekTo(positionMs: Long) {
            commands.add("seek:$positionMs")
        }

        override fun togglePlayback() {
            commands.add("toggle")
        }

        override suspend fun resolveLink(url: String): LinkImportResult {
            resolvedUrls.add(url)
            return nextResult
        }
    }

    private class FakePlaylistRepository : PlaylistRepository {
        private val state = MutableStateFlow<List<SavedPlaylist>>(emptyList())
        override val playlists: Flow<List<SavedPlaylist>> = state

        override suspend fun save(playlist: SavedPlaylist) {
            state.value = state.value.filterNot { it.id == playlist.id } + playlist
        }

        override suspend fun delete(id: String) {
            state.value = state.value.filterNot { it.id == id }
        }
    }
}
