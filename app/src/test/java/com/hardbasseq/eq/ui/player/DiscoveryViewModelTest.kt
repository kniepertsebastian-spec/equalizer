package com.hardbasseq.eq.ui.player

import com.hardbasseq.eq.discovery.DiscoveryRepository
import com.hardbasseq.eq.discovery.DiscoverySource
import com.hardbasseq.eq.discovery.DiscoveryState
import com.hardbasseq.eq.discovery.DiscoveryUpdater
import com.hardbasseq.eq.integration.LinkImportResult
import com.hardbasseq.eq.integration.LoadResult
import com.hardbasseq.eq.integration.PlayerController
import com.hardbasseq.eq.link.SpotifyPlaylist
import com.hardbasseq.eq.text.FakeTextProvider
import com.soundcloud.equalizer.player.model.ExternalTrackInfo
import com.soundcloud.equalizer.player.model.LibraryOverview
import com.soundcloud.equalizer.player.model.TrackItem
import com.soundcloud.equalizer.player.playback.NowPlaying
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeRepository()
    private val source = FakeSource()
    private val controller = RecordingController()
    private val day = 86_400_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = DiscoveryViewModel(repository, DiscoveryUpdater(repository, source), controller, FakeTextProvider())

    private fun DiscoveryViewModel.playlistIds(): List<Long> {
        val playlist = state.value.playlist
        return playlist.map { it.track.id }
    }

    // Uploaded two days ago, relative to the real clock the view model uses.
    private fun upload(
        id: Long,
        title: String,
    ) = TrackItem(
        id = id,
        title = title,
        artist = "Uploader",
        artworkUrl = null,
        streamUrl = null,
        durationMs = 200_000L,
        createdAtMs = System.currentTimeMillis() - 2 * day,
    )

    @Test
    fun `opening the screen builds this weeks list for saved artists`() =
        runTest {
            repository.set(DiscoveryState(artists = listOf("Angerfist")))
            source.results["Angerfist"] = LoadResult.Ok(listOf(upload(1, "Angerfist - A"), upload(2, "Angerfist - B")))

            val vm = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, vm.playlistIds().size)
            assertFalse(vm.uiState.value.isLoading)
        }

    @Test
    fun `adding an artist saves the name and fetches their uploads right away`() =
        runTest {
            source.results["Miss K8"] = LoadResult.Ok(listOf(upload(7, "Miss K8 - Bomb")))
            val vm = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            vm.addArtist("  Miss K8 ")
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("Miss K8"), vm.state.value.artists)
            assertEquals(listOf(7L), vm.playlistIds())
        }

    @Test
    fun `a duplicate or invalid artist name says so`() =
        runTest {
            repository.set(DiscoveryState(artists = listOf("Angerfist"), weekKey = 1L))
            val vm = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            vm.addArtist("angerfist")
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(vm.uiState.value.isError)
            assertEquals(listOf("Angerfist"), vm.state.value.artists)
        }

    @Test
    fun `removing an artist removes their suggestions`() =
        runTest {
            repository.set(DiscoveryState(artists = listOf("Angerfist")))
            source.results["Angerfist"] = LoadResult.Ok(listOf(upload(1, "Angerfist - A")))
            val vm = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            vm.removeArtist("Angerfist")
            dispatcher.scheduler.advanceUntilIdle()

            val artists = vm.state.value.artists
            assertTrue(artists.isEmpty())
            assertTrue(vm.playlistIds().isEmpty())
        }

    @Test
    fun `swiping a track away removes it for good`() =
        runTest {
            repository.set(DiscoveryState(artists = listOf("Angerfist")))
            source.results["Angerfist"] = LoadResult.Ok(listOf(upload(1, "Angerfist - A"), upload(2, "Angerfist - B")))
            val vm = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            val playlist = vm.state.value.playlist
            val toSwipe = playlist.first { it.track.id == 1L }
            vm.dismiss(toSwipe)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(2L), vm.playlistIds())
            val dismissed = vm.state.value.dismissedIds
            assertTrue(1L in dismissed)

            // A manual refresh does not bring it back.
            vm.refreshNow()
            dispatcher.scheduler.advanceUntilIdle()
            assertFalse(1L in vm.playlistIds())
        }

    @Test
    fun `playing starts the playlist at the chosen track without stream urls`() =
        runTest {
            repository.set(DiscoveryState(artists = listOf("Angerfist")))
            source.results["Angerfist"] = LoadResult.Ok(listOf(upload(1, "Angerfist - A"), upload(2, "Angerfist - B")))
            val vm = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            vm.play(1)

            val (tracks, start) = controller.played.single()
            assertEquals(2, tracks.size)
            assertEquals(1, start)
            assertTrue(tracks.all { it.streamUrl == null })
        }

    @Test
    fun `playing an empty list does nothing`() =
        runTest {
            val vm = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            vm.playAll()

            assertTrue(controller.played.isEmpty())
        }

    @Test
    fun `a failed refresh shows the error and keeps the list`() =
        runTest {
            repository.set(DiscoveryState(artists = listOf("Angerfist")))
            source.results["Angerfist"] = LoadResult.Error("offline")

            val vm = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(vm.uiState.value.isError)
            assertEquals("offline", vm.uiState.value.message)
            assertNull(vm.playlistIds().firstOrNull())
        }

    private class FakeRepository : DiscoveryRepository {
        private val flow = MutableStateFlow(DiscoveryState())
        override val state: Flow<DiscoveryState> = flow

        fun set(state: DiscoveryState) {
            flow.value = state
        }

        override suspend fun current(): DiscoveryState = flow.first()

        override suspend fun update(transform: (DiscoveryState) -> DiscoveryState) {
            flow.value = transform(flow.value)
        }
    }

    private class FakeSource : DiscoverySource {
        val results = mutableMapOf<String, LoadResult<List<TrackItem>>>()

        override suspend fun tasteTracks(): LoadResult<List<TrackItem>> = LoadResult.Error("signed out")

        override suspend fun recentUploads(artist: String): LoadResult<List<TrackItem>> = results[artist] ?: LoadResult.Ok(emptyList())
    }

    // Only playQueue matters here; everything else is unused by the discovery screen.
    private class RecordingController : PlayerController {
        val played = mutableListOf<Pair<List<TrackItem>, Int>>()
        override val nowPlaying: StateFlow<NowPlaying?> = MutableStateFlow<NowPlaying?>(null).asStateFlow()
        override val queue: StateFlow<List<TrackItem>> = MutableStateFlow(emptyList<TrackItem>()).asStateFlow()

        override fun playQueue(
            tracks: List<TrackItem>,
            startIndex: Int,
        ) {
            played.add(tracks to startIndex)
        }

        override fun skipToIndex(index: Int) = unused()

        override fun next() = unused()

        override fun previous() = unused()

        override fun seekTo(positionMs: Long) = unused()

        override fun togglePlayback() = unused()

        override suspend fun resolveLink(url: String): LinkImportResult = unused()

        override fun isSoundCloudSignedIn(): Boolean = false

        override suspend fun describeExternalLink(url: String): LoadResult<ExternalTrackInfo> = unused()

        override suspend fun fetchSpotifyTrackPage(trackId: String): LoadResult<String> = unused()

        override fun spotifyClientId(): String = unused()

        override fun saveSpotifyClientId(clientId: String) = unused()

        override fun isSpotifySignedIn(): Boolean = unused()

        override fun openSpotifySignIn(): String? = unused()

        override fun signOutSpotify() = unused()

        override suspend fun fetchSpotifyPlaylistViaApi(playlistId: String): LoadResult<SpotifyPlaylist> = unused()

        override suspend fun fetchSpotifyPlaylistPage(playlistId: String): LoadResult<String> = unused()

        override suspend fun searchSoundCloud(
            query: String,
            limit: Int,
        ): LoadResult<List<TrackItem>> = unused()

        override suspend fun loadLibrary(): LoadResult<LibraryOverview> = unused()

        override suspend fun loadLikedTracks(): LoadResult<List<TrackItem>> = unused()

        override suspend fun loadPlaylistTracks(playlistId: Long): LoadResult<List<TrackItem>> = unused()

        override suspend fun pushPlaylistToSoundCloud(
            title: String,
            trackIds: List<Long>,
            existingId: Long?,
        ): LoadResult<Long> = unused()

        override fun openSoundCloudSignIn() = unused()

        override fun signOutSoundCloud() = unused()

        private fun unused(): Nothing = error("not used by the discovery screen")
    }
}
