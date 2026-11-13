package com.hardbasseq.eq.ui.player

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.hardbasseq.eq.audio.AudioDeviceType
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.preset.TargetPoint
import com.hardbasseq.eq.ui.eq.EqStatusKind
import com.hardbasseq.eq.ui.player.mini.MiniPlayer
import com.hardbasseq.eq.ui.player.nowplaying.NowPlayingActions
import com.hardbasseq.eq.ui.player.nowplaying.NowPlayingContext
import com.hardbasseq.eq.ui.player.nowplaying.NowPlayingScreen
import com.hardbasseq.eq.ui.player.nowplaying.NowPlayingUi
import com.hardbasseq.eq.ui.player.queue.QueueActions
import com.hardbasseq.eq.ui.player.queue.QueueSheet
import com.hardbasseq.eq.ui.theme.HardBassEqTheme
import com.soundcloud.equalizer.player.model.TrackItem
import com.soundcloud.equalizer.player.playback.NowPlaying
import com.soundcloud.equalizer.player.playback.PlayerNotice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// Roadmap 05 S2-S4 on the JVM: the mini player, "Now playing" and the queue sheet render, react
// and expose their TalkBack actions. Each test also writes a screenshot to
// app/build/player-screenshots/ for a look at the layout (not compared, nothing is checked in).
// A plain Application: the Hilt app would build the whole object graph for nothing.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PlayerShellUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val curve = listOf(TargetPoint(30f, 6f), TargetPoint(100f, 3f), TargetPoint(1000f, 0f), TargetPoint(8000f, 2f))

    private fun nowPlaying(
        isPlaying: Boolean = true,
        notice: PlayerNotice? = null,
        isPreview: Boolean = false,
    ) = NowPlaying(
        title = "Raw Uptempo Anthem (Extended Mix)",
        artist = "DJ Kickdrum",
        isPlaying = isPlaying,
        durationMs = 245_000L,
        positionMs = 72_000L,
        queueIndex = 1,
        isPreview = isPreview,
        notice = notice,
    )

    private fun context() =
        NowPlayingContext(
            eqStatus = EqStatusKind.ACTIVE,
            soundName = "Hardstyle Car",
            soundCurve = curve,
            contextName = "Car",
            route = AudioRoute(AudioDeviceType.BLUETOOTH, "JBL Flip 6", "bt"),
            queueCount = 23,
            signedIn = true,
        )

    private class Calls {
        val log = mutableListOf<String>()

        fun record(name: String): () -> Unit = { log.add(name) }
    }

    private fun actions(calls: Calls) =
        NowPlayingActions(
            onCollapse = calls.record("collapse"),
            onDrag = {},
            onDragStopped = {},
            onTogglePlayback = calls.record("toggle"),
            onPrevious = calls.record("previous"),
            onNext = calls.record("next"),
            onSeek = {},
            onToggleShuffle = calls.record("shuffle"),
            onCycleRepeat = calls.record("repeat"),
            onAddToPlaylist = calls.record("addToPlaylist"),
            onOpenSound = calls.record("sound"),
            onOpenQueue = calls.record("queue"),
        )

    @Test
    fun miniPlayer_showsTheTrackAndControlsPlayback() {
        val calls = Calls()
        compose.setContent {
            HardBassEqTheme {
                MiniPlayer(
                    ui = NowPlayingUi.from(nowPlaying()),
                    eqStatus = EqStatusKind.ACTIVE,
                    onExpand = calls.record("expand"),
                    onDrag = {},
                    onDragStopped = {},
                    onTogglePlayback = calls.record("toggle"),
                    onNext = calls.record("next"),
                    onOpenSound = calls.record("sound"),
                )
            }
        }

        compose.onNodeWithText("Raw Uptempo Anthem (Extended Mix)").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.onNodeWithContentDescription("Next").performClick()
        compose.onNodeWithContentDescription("EQ active").performClick()
        compose.onNodeWithContentDescription("Now playing: Raw Uptempo Anthem (Extended Mix) by DJ Kickdrum").performClick()

        assertEquals(listOf("toggle", "next", "sound", "expand"), calls.log)
        compose.onRoot().saveScreenshot("mini-player")
    }

    @Test
    fun miniPlayer_showsANoticeInsteadOfTheArtist() {
        compose.setContent {
            HardBassEqTheme {
                MiniPlayer(
                    ui = NowPlayingUi.from(nowPlaying(isPlaying = false, notice = PlayerNotice.WaitingForNetwork, isPreview = true)),
                    eqStatus = EqStatusKind.WAITING,
                    onExpand = {},
                    onDrag = {},
                    onDragStopped = {},
                    onTogglePlayback = {},
                    onNext = {},
                    onOpenSound = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
        compose.onNodeWithText("PREVIEW").assertIsDisplayed()
        compose.onRoot().saveScreenshot("mini-player-offline-preview")
    }

    @Test
    fun nowPlaying_portrait() {
        val calls = Calls()
        compose.setContent {
            HardBassEqTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    NowPlayingScreen(ui = NowPlayingUi.from(nowPlaying()), context = context(), actions = actions(calls))
                }
            }
        }

        compose.onNodeWithText("Output: JBL Flip 6").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close player").performClick()
        compose.onNodeWithContentDescription("Queue, 23 tracks").performClick()
        compose.onNodeWithContentDescription("Open equalizer: Hardstyle Car").performClick()

        assertEquals(listOf("collapse", "queue", "sound"), calls.log)
        compose.onRoot().saveScreenshot("now-playing-portrait")
    }

    @Test
    fun nowPlaying_offersTryAgainWhenPlaybackStopped() {
        val calls = Calls()
        compose.setContent {
            HardBassEqTheme {
                NowPlayingScreen(
                    ui = NowPlayingUi.from(nowPlaying(isPlaying = false, notice = PlayerNotice.NetworkGaveUp)),
                    context = context(),
                    actions = actions(calls),
                )
            }
        }

        compose.onNodeWithText("Try again").performClick()

        assertEquals(listOf("toggle"), calls.log)
        compose.onRoot().saveScreenshot("now-playing-network-gave-up")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun nowPlaying_landscape() {
        compose.setContent {
            HardBassEqTheme {
                NowPlayingScreen(ui = NowPlayingUi.from(nowPlaying()), context = context(), actions = actions(Calls()))
            }
        }

        compose.onNodeWithText("Raw Uptempo Anthem (Extended Mix)").assertIsDisplayed()
        compose.onRoot().saveScreenshot("now-playing-landscape")
    }

    @Test
    fun queueSheet_movesAndRemovesThroughTalkBackActions() {
        val calls = mutableListOf<String>()
        val queue = List(6) { TrackItem(it.toLong(), "Track $it", "Artist $it", null, null, 180_000L) }
        compose.setContent {
            HardBassEqTheme {
                QueueSheet(
                    queue = queue,
                    currentIndex = 1,
                    playedIds = setOf(0L),
                    shuffle = false,
                    actions =
                        QueueActions(
                            onPlayIndex = { calls.add("play:$it") },
                            onMove = { from, to -> calls.add("move:$from->$to") },
                            onRemove = { calls.add("remove:$it") },
                            onReinsert = { _, _ -> },
                            onClearUpcoming = { emptyList() },
                            onRestoreUpcoming = {},
                            onAddToPlaylist = {},
                            onGoToSearch = {},
                        ),
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Track 2", useUnmergedTree = true).assertIsDisplayed()
        actionOn("Track 2", "Move down")
        actionOn("Track 2", "Remove from queue")

        assertEquals(listOf("move:2->3", "remove:2"), calls)
        compose.onNode(isDialog()).saveScreenshot("queue-sheet")
    }

    // Runs a TalkBack custom action on the row that shows [title].
    private fun actionOn(
        title: String,
        label: String,
    ) {
        val rows = compose.onAllNodes(hasAnyDescendant(hasText(title)))
        val node =
            (0 until rows.fetchSemanticsNodes().size)
                .map { rows[it] }
                .last { candidate ->
                    candidate
                        .fetchSemanticsNode()
                        .config
                        .getOrNull(SemanticsActions.CustomActions)
                        ?.any { it.label == label } == true
                }
        val action =
            node
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .first { it.label == label }
        compose.runOnIdle { action.action() }
    }

    private fun SemanticsNodeInteraction.saveScreenshot(name: String) {
        val bitmap = captureToImage().asAndroidBitmap()
        val dir = File("build/player-screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
