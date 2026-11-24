package com.auracurve.app.ui.player

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.auracurve.app.player.model.PlaylistItem
import com.auracurve.app.player.model.TrackItem
import com.auracurve.app.playlist.SavedPlaylist
import com.auracurve.app.playlist.SavedTrack
import com.auracurve.app.ui.player.library.LibraryRow
import com.auracurve.app.ui.player.library.PLAYLIST_DETAIL_LIST_TAG
import com.auracurve.app.ui.player.library.PLAYLIST_TRACK_TAG_PREFIX
import com.auracurve.app.ui.player.library.PlaylistDetailScreen
import com.auracurve.app.ui.player.library.PlaylistRow
import com.auracurve.app.ui.theme.AuraCurveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// The pieces of the player tab (the library): tabs, search with results and saved playlists.
// The screen itself needs the view models; these are the parts it is built from.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class LibraryUiTest {
    // An activity rule (not createComposeRule) so the back test can press the system back key.
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun track(
        id: Long,
        title: String,
        artist: String,
    ) = TrackItem(id = id, title = title, artist = artist, artworkUrl = null, streamUrl = null, durationMs = 245_000L)

    private val results =
        listOf(
            track(1, "Raw Uptempo Anthem (Extended Mix)", "DJ Kickdrum"),
            track(2, "Frenchcore Pressure", "Sefa Style"),
            track(3, "Night Drive", "Neon Bass"),
            track(4, "Terror Unit", "Kick Machine"),
        )

    private val playlist =
        SavedPlaylist(
            id = "p1",
            title = "Gym Hardstyle",
            tracks = listOf(SavedTrack(1, "Kick", "DJ"), SavedTrack(2, "Bass", "MC")),
        )

    @Test
    fun library_tabsSearchAndPlaylists() {
        val played = mutableListOf<String>()
        compose.setContent {
            AuraCurveTheme {
                var tab by remember { mutableStateOf(PlayerTab.SEARCH) }
                var text by remember { mutableStateOf("hardstyle") }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { LibraryHeader(onlineSources = true, onOpenServices = {}, onSelectSoundCloud = {}) }
                    item { PlayerTabs(selected = tab, onSelect = { tab = it }) }
                    searchItems(
                        text = text,
                        onTextChange = { text = it },
                        state = SearchUiState(query = "hardstyle", results = results, outcome = SearchOutcome.RESULTS),
                        onSearch = { played.add("search") },
                        onPlay = { played.add("play $it") },
                        onPlayNext = {},
                        onAddToQueue = {},
                    )
                    item {
                        PlaylistRow(
                            playlist = playlist,
                            onOpen = { played.add("open") },
                            onPlay = { played.add("playlist") },
                            onDelete = {},
                            onSendToSoundCloud = null,
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("Frenchcore Pressure").assertIsDisplayed()
        compose.onRoot().saveScreenshot("library-search")
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("Frenchcore Pressure").performClick()
        compose.onNodeWithText("Playlists").performClick()

        assertEquals(listOf("search", "play 1"), played)
    }

    // Roadmap 17: rows open the detail view, their play button still plays at once.
    @Test
    fun playlistRows_tapOpensAndPlayButtonPlays() {
        val events = mutableListOf<String>()
        val soundCloud = PlaylistItem(id = 7, title = "Raw Mix", trackCount = 30, artworkUrl = null)
        compose.setContent {
            AuraCurveTheme {
                LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    item {
                        PlaylistRow(
                            playlist = playlist,
                            onOpen = { events.add("open local") },
                            onPlay = { events.add("play local") },
                            onDelete = {},
                            onSendToSoundCloud = null,
                        )
                    }
                    item {
                        LibraryRow(
                            title = soundCloud.title,
                            subtitle = "30 tracks",
                            onPlay = { events.add("play soundcloud") },
                            onOpen = { events.add("open soundcloud") },
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("Gym Hardstyle").performClick()
        compose.onNodeWithContentDescription("Play playlist").performClick()
        compose.onNodeWithText("Raw Mix").performClick()
        compose.onNodeWithContentDescription("Play").performClick()

        assertEquals(listOf("open local", "play local", "open soundcloud", "play soundcloud"), events)
    }

    private fun longPlaylist(count: Int = 50) =
        OpenedPlaylistUiState(
            source = OpenedPlaylistSource.Library(PlaylistItem(id = 7, title = "Raw Mix", trackCount = count, artworkUrl = null)),
            title = "Raw Mix",
            subtitle = "SoundCloud · $count tracks",
            artworkUrl = null,
            tracks = (1L..count).map { track(it, "Track $it", "Artist $it") },
        )

    // A 50-track list scrolls to its last track, which ends above the mini player (a 120 dp
    // stand-in bottom bar, as in the app shell) and plays from index 49 when tapped.
    @Test
    fun playlistDetail_scrollsToTheLastTrackAboveTheMiniPlayer() {
        val played = mutableListOf<Int>()
        compose.setContent {
            AuraCurveTheme {
                Scaffold(
                    bottomBar = { Box(modifier = Modifier.fillMaxWidth().height(120.dp).testTag("mini-player")) },
                ) { padding ->
                    PlaylistDetailScreen(
                        state = longPlaylist(),
                        currentTrackId = 3L,
                        playedIds = setOf(1L, 2L),
                        onBack = {},
                        onPlayAll = {},
                        onShuffle = {},
                        onPlayTrack = { played.add(it) },
                        onPlayNext = {},
                        onAddToQueue = {},
                        onRetry = {},
                        modifier = Modifier.padding(padding).background(MaterialTheme.colorScheme.background),
                    )
                }
            }
        }

        compose.onNodeWithText("Track 50").assertIsNotDisplayed()
        // Item 0 is the header, so the 50th track is item 50.
        compose.onNodeWithTag(PLAYLIST_DETAIL_LIST_TAG).performScrollToIndex(50)
        val last = compose.onNodeWithTag("${PLAYLIST_TRACK_TAG_PREFIX}49")
        last.assertIsDisplayed()
        val lastBottom = last.getUnclippedBoundsInRoot().bottom
        val barTop = compose.onNodeWithTag("mini-player").getUnclippedBoundsInRoot().top
        assertTrue("last track ($lastBottom) must end above the mini player ($barTop)", lastBottom <= barTop)
        compose.onRoot().saveScreenshot("playlist-detail-bottom")
        last.performClick()

        assertEquals(listOf(49), played)
    }

    @Test
    fun playlistDetail_searchKeepsTheIndexInTheWholePlaylist() {
        val played = mutableListOf<Int>()
        compose.setContent {
            AuraCurveTheme {
                PlaylistDetailScreen(
                    state = longPlaylist(),
                    currentTrackId = null,
                    playedIds = emptySet(),
                    onBack = {},
                    onPlayAll = {},
                    onShuffle = {},
                    onPlayTrack = { played.add(it) },
                    onPlayNext = {},
                    onAddToQueue = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Search this playlist").performTextInput("Artist 42")
        compose.onNodeWithText("Track 42").performClick()

        assertEquals(listOf(41), played)
    }

    @Test
    fun playlistDetail_loadingAndErrorStates() {
        var state by mutableStateOf(longPlaylist().copy(tracks = emptyList(), isLoading = true))
        var retries = 0
        compose.setContent {
            AuraCurveTheme {
                PlaylistDetailScreen(
                    state = state,
                    currentTrackId = null,
                    playedIds = emptySet(),
                    onBack = {},
                    onPlayAll = {},
                    onShuffle = {},
                    onPlayTrack = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onRetry = { retries++ },
                )
            }
        }

        compose.onNodeWithText("Loading tracks…").assertIsDisplayed()
        compose.onNodeWithText("Play all").assertIsNotEnabled()

        state = state.copy(isLoading = false, errorMessage = "Timeout")
        compose.onNodeWithText("The tracks could not be loaded: Timeout").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)

        state = state.copy(errorMessage = null)
        compose.onNodeWithText("This playlist has no playable tracks.").assertIsDisplayed()
    }

    // The back arrow and the system back key both return to the overview, as in PlayerScreen
    // (BackHandler enabled only while a playlist is open).
    @Test
    fun playlistDetail_backReturnsToTheOverview() {
        var opened by mutableStateOf<OpenedPlaylistUiState?>(null)
        compose.setContent {
            AuraCurveTheme {
                BackHandler(enabled = opened != null) { opened = null }
                val current = opened
                if (current != null) {
                    PlaylistDetailScreen(
                        state = current,
                        currentTrackId = null,
                        playedIds = emptySet(),
                        onBack = { opened = null },
                        onPlayAll = {},
                        onShuffle = {},
                        onPlayTrack = {},
                        onPlayNext = {},
                        onAddToQueue = {},
                        onRetry = {},
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            LibraryRow(
                                title = "Raw Mix",
                                subtitle = "50 tracks",
                                onPlay = {},
                                onOpen = { opened = longPlaylist() },
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithText("Raw Mix").performClick()
        compose.onNodeWithText("Play all").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to the playlists").performClick()
        compose.onNodeWithText("50 tracks").assertIsDisplayed()

        compose.onNodeWithText("Raw Mix").performClick()
        compose.onNodeWithText("Play all").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("50 tracks").assertIsDisplayed()
    }

    private fun SemanticsNodeInteraction.saveScreenshot(name: String) {
        val bitmap = captureToImage().asAndroidBitmap()
        val dir = File("build/player-screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
