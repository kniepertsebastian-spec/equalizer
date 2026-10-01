package com.hardbasseq.eq.ui.player

import com.hardbasseq.eq.integration.LinkImportResult
import com.hardbasseq.eq.integration.LoadResult
import com.hardbasseq.eq.integration.PlayerController
import com.hardbasseq.eq.playlist.PlaylistRepository
import com.hardbasseq.eq.playlist.SavedPlaylist
import com.soundcloud.equalizer.player.model.ExternalTrackInfo
import com.soundcloud.equalizer.player.model.LibraryOverview
import com.soundcloud.equalizer.player.model.PlaylistItem
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
    fun `creates an empty local playlist and refuses a blank name`() =
        runTest {
            val vm = viewModel()

            vm.createPlaylist("   ")
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(vm.playlists.value.isEmpty())
            assertTrue(vm.importState.value.isError)

            vm.createPlaylist("Auto")
            dispatcher.scheduler.advanceUntilIdle()
            val saved = vm.playlists.value.single()
            assertEquals("Auto", saved.title)
            assertTrue(saved.tracks.isEmpty())
            assertEquals(null, saved.sourceUrl)
        }

    @Test
    fun `a new playlist can start with a track`() =
        runTest {
            val vm = viewModel()

            vm.createPlaylist("Auto", track(7))
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                listOf(7L),
                vm.playlists.value
                    .single()
                    .tracks
                    .map { it.id },
            )
        }

    @Test
    fun `adding to a playlist keeps each track once`() =
        runTest {
            val vm = viewModel()
            vm.createPlaylist("Auto")
            dispatcher.scheduler.advanceUntilIdle()
            val playlist = vm.playlists.value.single()

            vm.addToPlaylist(playlist, track(1))
            vm.addToPlaylist(playlist, track(1))
            vm.addToPlaylist(playlist, track(2))
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                listOf(1L, 2L),
                vm.playlists.value
                    .single()
                    .tracks
                    .map { it.id },
            )
        }

    @Test
    fun `tracks can be removed from a local playlist but not from an imported one`() =
        runTest {
            val vm = viewModel()
            vm.createPlaylist("Auto", track(1))
            controller.nextResult = LinkImportResult.Playlist("Mix", "https://soundcloud.com/a/sets/mix", listOf(track(5)))
            vm.importFromText("https://soundcloud.com/a/sets/mix")
            dispatcher.scheduler.advanceUntilIdle()
            val local = vm.playlists.value.first { it.sourceUrl == null }
            val imported = vm.playlists.value.first { it.sourceUrl != null }

            vm.removeFromPlaylist(local, 1L)
            vm.removeFromPlaylist(imported, 5L)
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(
                vm.playlists.value
                    .first { it.sourceUrl == null }
                    .tracks
                    .isEmpty(),
            )
            assertEquals(
                1,
                vm.playlists.value
                    .first { it.sourceUrl != null }
                    .tracks.size,
            )
        }

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
    fun `account state follows the player and refreshes on demand`() =
        runTest {
            val vm = viewModel()
            assertFalse(vm.signedIn.value)

            // The sign-in screen is a separate activity: the state only changes once the
            // screen asks again after coming back.
            controller.signedIn = true
            assertFalse(vm.signedIn.value)
            vm.refreshAccount()
            assertTrue(vm.signedIn.value)

            vm.signOut()
            assertFalse(vm.signedIn.value)
            assertEquals(listOf("signout"), controller.commands)

            vm.signIn()
            assertEquals(listOf("signout", "signin"), controller.commands)
        }

    private fun candidate(
        id: Long,
        title: String,
        uploader: String,
    ) = TrackItem(id = id, title = title, artist = uploader, artworkUrl = null, streamUrl = null, durationMs = 200_000L)

    @Test
    fun `a youtube song is looked up on soundcloud and a sure match starts playing`() =
        runTest {
            controller.describeResult = LoadResult.Ok(ExternalTrackInfo("Katy Perry - Roar (Official Video)", "KatyPerryVEVO"))
            val cover = candidate(1, "Roar cover by someone", "Cover Guy")
            val original = candidate(2, "Katy Perry - Roar", "KatyPerryVEVO")
            controller.searchResults["Katy Perry Roar"] = LoadResult.Ok(listOf(cover, original))
            val vm = viewModel()

            vm.importFromText("https://youtu.be/dQw4w9WgXcQ?si=abc")
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ"), controller.describedUrls)
            val bridge = vm.bridgeState.value
            assertEquals("Katy Perry - Roar", bridge?.label)
            assertEquals(2L, vm.bridgeTrackIds().first())
            assertTrue(bridge?.startedAutomatically == true)
            val (tracks, _) = controller.playedQueues.single()
            assertEquals(listOf(2L), tracks.map { it.id })
            assertFalse(vm.importState.value.isError)
            assertTrue(vm.importMessage().startsWith("Gefunden und gestartet"))
        }

    @Test
    fun `a spotify song has no artist so the matches are shown and nothing starts by itself`() =
        runTest {
            controller.describeResult = LoadResult.Ok(ExternalTrackInfo("Roar", null))
            val withArtist = candidate(1, "Katy Perry - Roar", "Katy")
            val titleOnly = candidate(2, "Roar", "Other")
            controller.searchResults["Roar"] = LoadResult.Ok(listOf(withArtist, titleOnly))
            val vm = viewModel()

            vm.importFromText("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=x")
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"), controller.describedUrls)
            assertTrue(controller.playedQueues.isEmpty())
            assertEquals(2, vm.bridgeTrackIds().size)
            assertEquals(false, vm.bridgeState.value?.startedAutomatically)
            assertTrue(vm.importMessage().startsWith("Kein sicherer Treffer"))
        }

    @Test
    fun `when artist plus title finds nothing the title alone is tried`() =
        runTest {
            controller.describeResult = LoadResult.Ok(ExternalTrackInfo("Angerfist - Criminally Insane", null))
            controller.searchResults["Criminally Insane"] = LoadResult.Ok(listOf(candidate(5, "Criminally Insane", "Angerfist")))
            val vm = viewModel()

            vm.importFromText("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("Angerfist Criminally Insane", "Criminally Insane"), controller.searchedQueries)
            assertEquals(5L, vm.bridgeTrackIds().first())
        }

    @Test
    fun `nothing relevant on soundcloud says so and plays nothing`() =
        runTest {
            controller.describeResult = LoadResult.Ok(ExternalTrackInfo("Katy Perry - Roar", null))
            controller.searchResults["Katy Perry Roar"] = LoadResult.Ok(listOf(candidate(9, "Completely unrelated", "Nobody")))
            val vm = viewModel()

            vm.importFromText("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(vm.importState.value.isError)
            assertTrue(vm.importMessage().startsWith("Nichts Passendes"))
            assertNull(vm.bridgeState.value)
            assertTrue(controller.playedQueues.isEmpty())
        }

    @Test
    fun `a link the provider gives no details for shows its message`() =
        runTest {
            controller.describeResult = LoadResult.Error("Zu diesem Link gibt es keine Angaben")
            val vm = viewModel()

            vm.importFromText("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(vm.importState.value.isError)
            assertEquals("Zu diesem Link gibt es keine Angaben", vm.importState.value.message)
            assertTrue(controller.searchedQueries.isEmpty())
        }

    @Test
    fun `youtube playlists and spotify albums are refused without any lookup`() =
        runTest {
            val vm = viewModel()

            vm.importFromText("https://www.youtube.com/playlist?list=PL123")
            assertTrue(vm.importState.value.isError)
            vm.importFromText("https://open.spotify.com/album/4cOdK2wGLETKBW3PvgPWqT")
            assertTrue(vm.importState.value.isError)
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(controller.describedUrls.isEmpty())
        }

    private fun spotifyPage(vararg entries: Pair<String, String>): LoadResult<String> {
        val list = entries.joinToString(",") { """{"title":"${it.second}","subtitle":"${it.first}"}""" }
        return LoadResult.Ok(
            """<script id="__NEXT_DATA__" type="application/json">{"entity":{"name":"Hardcore Mix","trackList":[$list]}}</script>""",
        )
    }

    @Test
    fun `a spotify playlist becomes a soundcloud playlist of the songs found`() =
        runTest {
            val vm = viewModel()
            controller.spotifyPage = spotifyPage("Angerfist" to "Drum Go Bang", "Miss K8" to "Unknown Banger")
            controller.searchResults["Angerfist Drum Go Bang"] =
                LoadResult.Ok(listOf(track(1, "Angerfist - Drum Go Bang").copy(artist = "Angerfist")))

            vm.importFromText("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=x")
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("37i9dQZF1DXcBWIGoYBM5M"), controller.fetchedSpotifyPlaylists)
            val saved = vm.playlists.value.single()
            assertEquals("Hardcore Mix (von Spotify)", saved.title)
            assertEquals(listOf(1L), saved.tracks.map { it.id })
            assertFalse(vm.importState.value.isError)
            val message =
                vm.importState.value.message
                    .orEmpty()
            assertTrue(message, message.contains("1 von 2"))
            assertTrue(message, message.contains("Miss K8 - Unknown Banger"))
            assertTrue(controller.playedQueues.isEmpty())
        }

    @Test
    fun `an unreadable spotify page or a playlist with no matches saves nothing`() =
        runTest {
            val vm = viewModel()

            controller.spotifyPage = LoadResult.Ok("<html>nothing</html>")
            vm.importFromText("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(vm.importState.value.isError)

            controller.spotifyPage = spotifyPage("Nobody" to "Nothing")
            vm.importFromText("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(vm.importState.value.isError)

            controller.spotifyPage = LoadResult.Error("offline")
            vm.importFromText("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("offline", vm.importState.value.message)
            assertTrue(vm.playlists.value.isEmpty())
        }

    @Test
    fun `a spotify song with a readable page is searched by artist and title and starts when sure`() =
        runTest {
            controller.spotifyTrackPage =
                LoadResult.Ok(
                    """<script id="__NEXT_DATA__" type="application/json">{"entity":{"name":"Criminally Insane","artists":[{"name":"Angerfist"}]}}</script>""",
                )
            controller.searchResults["Angerfist Criminally Insane"] =
                LoadResult.Ok(listOf(candidate(7, "Angerfist - Criminally Insane", "Angerfist")))
            val vm = viewModel()

            vm.importFromText("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("4cOdK2wGLETKBW3PvgPWqT"), controller.fetchedSpotifyTracks)
            assertTrue(controller.describedUrls.isEmpty())
            assertEquals(listOf("Angerfist Criminally Insane"), controller.searchedQueries)
            assertEquals(true, vm.bridgeState.value?.startedAutomatically)
            assertEquals(
                7L,
                controller.playedQueues
                    .single()
                    .first
                    .single()
                    .id,
            )
        }

    @Test
    fun `an unsure artist search is widened by a title search and the better result wins`() =
        runTest {
            controller.describeResult = LoadResult.Ok(ExternalTrackInfo("Angerfist - Criminally Insane", null))
            controller.searchResults["Angerfist Criminally Insane"] = LoadResult.Ok(listOf(candidate(1, "Some Mix 2024", "Someone")))
            controller.searchResults["Criminally Insane"] = LoadResult.Ok(listOf(candidate(2, "Criminally Insane", "Angerfist")))
            val vm = viewModel()

            vm.importFromText("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("Angerfist Criminally Insane", "Criminally Insane"), controller.searchedQueries)
            assertEquals(2L, vm.bridgeTrackIds().first())
        }

    @Test
    fun `several spotify links in one text are read together into one playlist`() =
        runTest {
            val vm = viewModel()
            controller.spotifyPage = spotifyPage("Angerfist" to "Drum Go Bang")
            controller.searchResults["Angerfist Drum Go Bang"] =
                LoadResult.Ok(listOf(track(1, "Angerfist - Drum Go Bang").copy(artist = "Angerfist")))

            vm.importFromText(
                "Teil 1 https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M und Teil 2 https://open.spotify.com/playlist/37i9dQZF1DX0XUsuxWHRQd",
            )
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("37i9dQZF1DXcBWIGoYBM5M", "37i9dQZF1DX0XUsuxWHRQd"), controller.fetchedSpotifyPlaylists)
            val saved = vm.playlists.value.single()
            assertEquals("Hardcore Mix + 1 weitere (von Spotify)", saved.title)
            // The same song in both parts is searched and stored once.
            assertEquals(listOf(1L), saved.tracks.map { it.id })
            assertEquals(1, controller.searchedQueries.count { it == "Angerfist Drum Go Bang" })
        }

    @Test
    fun `playlists can be merged into a new one and the originals stay`() =
        runTest {
            val vm = viewModel()
            vm.createPlaylist("A", track(1))
            vm.createPlaylist("B", track(2))
            dispatcher.scheduler.advanceUntilIdle()
            val sources = vm.playlists.value.sortedBy { it.title }

            vm.mergePlaylists(sources, "Alles")
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(3, vm.playlists.value.size)
            assertEquals(
                listOf(1L, 2L),
                vm.playlists.value
                    .first { it.title == "Alles" }
                    .tracks
                    .map { it.id },
            )
            vm.mergePlaylists(emptyList(), "Nichts")
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(vm.importState.value.isError)
        }

    @Test
    fun `picking another match plays it and closing hides the list`() =
        runTest {
            controller.describeResult = LoadResult.Ok(ExternalTrackInfo("Roar", null))
            controller.searchResults["Roar"] = LoadResult.Ok(listOf(candidate(1, "Roar", "A"), candidate(2, "Roar (Remix)", "B")))
            val vm = viewModel()
            vm.importFromText("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
            dispatcher.scheduler.advanceUntilIdle()

            val bridge = vm.bridgeState.value
            val second = bridge?.matches?.first { it.track.id == 2L }
            vm.playBridgeMatch(second ?: return@runTest)
            val (played, _) = controller.playedQueues.single()
            assertEquals(listOf(2L), played.map { it.id })

            vm.dismissBridge()
            assertNull(vm.bridgeState.value)
        }

    private fun PlayerViewModel.importMessage(): String = importState.value.message.orEmpty()

    private fun PlayerViewModel.bridgeTrackIds(): List<Long> {
        val matches = bridgeState.value?.matches.orEmpty()
        return matches.map { it.track.id }
    }

    private fun playlistItem(
        id: Long,
        title: String = "Playlist $id",
    ) = PlaylistItem(id = id, title = title, trackCount = 3, artworkUrl = null)

    @Test
    fun `the library loads the own and liked playlists when signed in`() =
        runTest {
            controller.signedIn = true
            controller.libraryResult = LoadResult.Ok(LibraryOverview(own = listOf(playlistItem(1)), liked = listOf(playlistItem(2))))
            val vm = viewModel()

            vm.loadLibrary()
            dispatcher.scheduler.advanceUntilIdle()

            val library = vm.libraryState.value.library
            assertEquals(listOf(1L), library?.own?.map { it.id })
            assertEquals(listOf(2L), library?.liked?.map { it.id })
            assertFalse(vm.libraryState.value.isLoading)
        }

    @Test
    fun `the library is not requested while signed out`() =
        runTest {
            val vm = viewModel()

            vm.loadLibrary()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(0, controller.libraryLoads)
            assertNull(vm.libraryState.value.library)
        }

    @Test
    fun `a failed library load shows the message and keeps what was loaded before`() =
        runTest {
            controller.signedIn = true
            controller.libraryResult = LoadResult.Ok(LibraryOverview(own = listOf(playlistItem(1)), liked = emptyList()))
            val vm = viewModel()
            vm.loadLibrary()
            dispatcher.scheduler.advanceUntilIdle()

            controller.libraryResult = LoadResult.Error("Nicht angemeldet")
            vm.loadLibrary()
            dispatcher.scheduler.advanceUntilIdle()

            val state = vm.libraryState.value
            assertTrue(state.isError)
            assertEquals("Nicht angemeldet", state.message)
            assertEquals(listOf(1L), state.library?.own?.map { it.id })
        }

    @Test
    fun `signing out clears the library`() =
        runTest {
            controller.signedIn = true
            controller.libraryResult = LoadResult.Ok(LibraryOverview(own = listOf(playlistItem(1)), liked = emptyList()))
            val vm = viewModel()
            vm.loadLibrary()
            dispatcher.scheduler.advanceUntilIdle()
            assertNotNull(vm.libraryState.value.library)

            vm.signOut()

            assertNull(vm.libraryState.value.library)
        }

    @Test
    fun `opening a library playlist plays its tracks as the queue`() =
        runTest {
            controller.signedIn = true
            controller.playlistTracksResult = LoadResult.Ok(listOf(track(1), track(2), track(3)))
            val vm = viewModel()

            vm.playLibraryPlaylist(playlistItem(42, "Hardcore"))
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(42L), controller.loadedPlaylistIds)
            val (tracks, start) = controller.playedQueues.single()
            assertEquals(listOf(1L, 2L, 3L), tracks.map { it.id })
            assertEquals(0, start)
            assertFalse(vm.libraryState.value.isLoading)
        }

    @Test
    fun `an empty or failing playlist plays nothing and says why`() =
        runTest {
            controller.signedIn = true
            val vm = viewModel()

            vm.playLibraryPlaylist(playlistItem(1, "Leer"))
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(vm.libraryState.value.isError)
            assertEquals("Leer ist leer", vm.libraryState.value.message)

            controller.playlistTracksResult = LoadResult.Error("Netzwerkfehler")
            vm.playLibraryPlaylist(playlistItem(2))
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("Netzwerkfehler", vm.libraryState.value.message)
            assertTrue(controller.playedQueues.isEmpty())
        }

    @Test
    fun `playing the likes queues the liked tracks`() =
        runTest {
            controller.signedIn = true
            controller.likesResult = LoadResult.Ok(listOf(track(9), track(8)))
            val vm = viewModel()

            vm.playLikedTracks()
            dispatcher.scheduler.advanceUntilIdle()

            val (tracks, _) = controller.playedQueues.single()
            assertEquals(listOf(9L, 8L), tracks.map { it.id })
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

        var signedIn = false
        var describeResult: LoadResult<ExternalTrackInfo> = LoadResult.Error("not configured")
        val describedUrls = mutableListOf<String>()
        val searchResults = mutableMapOf<String, LoadResult<List<TrackItem>>>()
        val searchedQueries = mutableListOf<String>()
        var libraryResult: LoadResult<LibraryOverview> = LoadResult.Ok(LibraryOverview(emptyList(), emptyList()))
        var likesResult: LoadResult<List<TrackItem>> = LoadResult.Ok(emptyList())
        var playlistTracksResult: LoadResult<List<TrackItem>> = LoadResult.Ok(emptyList())
        val loadedPlaylistIds = mutableListOf<Long>()
        var libraryLoads = 0

        override suspend fun describeExternalLink(url: String): LoadResult<ExternalTrackInfo> {
            describedUrls.add(url)
            return describeResult
        }

        var spotifyTrackPage: LoadResult<String> = LoadResult.Error("not configured")
        val fetchedSpotifyTracks = mutableListOf<String>()

        override suspend fun fetchSpotifyTrackPage(trackId: String): LoadResult<String> {
            fetchedSpotifyTracks.add(trackId)
            return spotifyTrackPage
        }

        var spotifyPage: LoadResult<String> = LoadResult.Error("not configured")
        val fetchedSpotifyPlaylists = mutableListOf<String>()

        override suspend fun fetchSpotifyPlaylistPage(playlistId: String): LoadResult<String> {
            fetchedSpotifyPlaylists.add(playlistId)
            return spotifyPage
        }

        override suspend fun searchSoundCloud(
            query: String,
            limit: Int,
        ): LoadResult<List<TrackItem>> {
            searchedQueries.add(query)
            return searchResults[query] ?: LoadResult.Ok(emptyList())
        }

        override suspend fun loadLibrary(): LoadResult<LibraryOverview> {
            libraryLoads++
            return libraryResult
        }

        override suspend fun loadLikedTracks(): LoadResult<List<TrackItem>> = likesResult

        override suspend fun loadPlaylistTracks(playlistId: Long): LoadResult<List<TrackItem>> {
            loadedPlaylistIds.add(playlistId)
            return playlistTracksResult
        }

        override fun isSoundCloudSignedIn(): Boolean = signedIn

        override fun openSoundCloudSignIn() {
            commands.add("signin")
        }

        override fun signOutSoundCloud() {
            signedIn = false
            commands.add("signout")
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
