package com.auracurve.app.ui.player

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.auracurve.app.player.model.TrackItem
import com.auracurve.app.playlist.SavedPlaylist
import com.auracurve.app.playlist.SavedTrack
import com.auracurve.app.ui.player.library.PlaylistRow
import com.auracurve.app.ui.theme.AuraCurveTheme
import org.junit.Assert.assertEquals
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
    @get:Rule
    val compose = createComposeRule()

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
                            onPlay = { played.add("playlist") },
                            onPlayTrack = {},
                            onDelete = {},
                            onSendToSoundCloud = null,
                            onRemoveTrack = {},
                            onPlayNext = {},
                            onAddToQueue = {},
                            currentTrackId = null,
                            playedIds = emptySet(),
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

    private fun SemanticsNodeInteraction.saveScreenshot(name: String) {
        val bitmap = captureToImage().asAndroidBitmap()
        val dir = File("build/player-screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
