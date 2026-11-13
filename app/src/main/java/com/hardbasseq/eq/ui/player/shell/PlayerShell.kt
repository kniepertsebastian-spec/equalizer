package com.hardbasseq.eq.ui.player.shell

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.context.SoundContext
import com.hardbasseq.eq.preset.TargetPoint
import com.hardbasseq.eq.ui.eq.EqStatusKind
import com.hardbasseq.eq.ui.equalizer.displayName
import com.hardbasseq.eq.ui.player.PlayerTab
import com.hardbasseq.eq.ui.player.PlayerViewModel
import com.hardbasseq.eq.ui.player.library.PlaylistDialog
import com.hardbasseq.eq.ui.player.mini.MiniPlayer
import com.hardbasseq.eq.ui.player.nowplaying.NowPlayingActions
import com.hardbasseq.eq.ui.player.nowplaying.NowPlayingContext
import com.hardbasseq.eq.ui.player.nowplaying.NowPlayingScreen
import com.hardbasseq.eq.ui.player.nowplaying.NowPlayingUi
import com.hardbasseq.eq.ui.player.queue.QueueActions
import com.hardbasseq.eq.ui.player.queue.QueueSheet
import com.hardbasseq.eq.ui.player.sound.SoundSheet
import com.soundcloud.equalizer.player.model.TrackItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Everything the shell needs to know about the sound, from MainViewModel.
data class ShellSound(
    val eqStatus: EqStatusKind,
    val soundName: String,
    val soundCurve: List<TargetPoint>,
    val activeContext: SoundContext?,
    val route: AudioRoute,
)

// The mini player, for the shell's bottom bar. Shown only while something is loaded; pulling it
// up drives the same [sheet] that "Now playing" follows.
@Composable
fun ShellMiniPlayer(
    viewModel: PlayerViewModel,
    sheet: PlayerSheetState,
    eqStatus: EqStatusKind,
    dragRangePx: Float,
) {
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val playing = nowPlaying ?: return
    val scope = rememberCoroutineScope()
    MiniPlayer(
        ui = NowPlayingUi.from(playing),
        eqStatus = eqStatus,
        onExpand = { scope.launch { sheet.expand() } },
        onDrag = { delta -> scope.launch { sheet.dragBy(delta / dragRangePx) } },
        onDragStopped = { velocity -> scope.launch { sheet.settle(velocity / dragRangePx) } },
        onTogglePlayback = viewModel::togglePlayback,
        onNext = viewModel::next,
        onOpenSound = { sheet.soundOpen = true },
    )
}

// "Now playing" as a layer above the whole shell (not a navigation route: it is the mini player
// at full size, so the finger can pull it continuously and the tab below stays as it was), plus
// the queue and sound sheets and the "add to playlist" dialog it opens.
@Composable
fun NowPlayingLayer(
    viewModel: PlayerViewModel,
    sheet: PlayerSheetState,
    sound: ShellSound,
    dragRangePx: Float,
    onOpenEqualizer: () -> Unit,
    onGoToSearch: () -> Unit,
    onContextModeChanged: (SoundContext?) -> Unit,
) {
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val playedIds by viewModel.playedIds.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val expandRequest by viewModel.expandPlayerRequest.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var playlistTrack by remember { mutableStateOf<TrackItem?>(null) }

    // Notification / lock screen: open full screen (once something is loaded).
    LaunchedEffect(expandRequest, nowPlaying != null) {
        if (expandRequest && nowPlaying != null) {
            viewModel.consumeExpandPlayerRequest()
            sheet.expand()
        }
    }
    // Nothing loaded any more: nothing to show full screen.
    LaunchedEffect(nowPlaying == null) {
        if (nowPlaying == null) {
            sheet.queueOpen = false
            sheet.fraction.snapTo(0f)
        }
    }

    // Back closes the layer; with predictive back it follows the gesture. Open sheets are their
    // own windows and take back first.
    PredictiveBackHandler(enabled = sheet.isVisible && !sheet.queueOpen && !sheet.soundOpen) { progress ->
        try {
            progress.collect { event -> sheet.fraction.snapTo(1f - event.progress * BACK_PREVIEW_SHARE) }
            scope.launch { sheet.collapse() }
        } catch (e: CancellationException) {
            scope.launch { sheet.expand() }
            throw e
        }
    }

    val playing = nowPlaying
    if (playing != null && sheet.isVisible) {
        val ui = NowPlayingUi.from(playing)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = (1f - sheet.fraction.value) * dragRangePx },
        ) {
            NowPlayingScreen(
                ui = ui,
                context =
                    NowPlayingContext(
                        eqStatus = sound.eqStatus,
                        soundName = sound.soundName,
                        soundCurve = sound.soundCurve,
                        contextName = sound.activeContext?.displayName(),
                        route = sound.route,
                        queueCount = queue.size,
                        signedIn = signedIn,
                    ),
                actions =
                    NowPlayingActions(
                        onCollapse = { scope.launch { sheet.collapse() } },
                        onDrag = { delta -> scope.launch { sheet.dragBy(delta / dragRangePx) } },
                        onDragStopped = { velocity -> scope.launch { sheet.settle(velocity / dragRangePx) } },
                        onTogglePlayback = viewModel::togglePlayback,
                        onPrevious = viewModel::previous,
                        onNext = viewModel::next,
                        onSeek = viewModel::seekTo,
                        onToggleShuffle = viewModel::toggleShuffle,
                        onCycleRepeat = viewModel::cycleRepeat,
                        onAddToPlaylist = { playlistTrack = viewModel.currentTrack() },
                        onOpenSound = { sheet.soundOpen = true },
                        onOpenQueue = { sheet.queueOpen = true },
                    ),
            )
        }
    }

    if (sheet.queueOpen && playing != null) {
        QueueSheet(
            queue = queue,
            currentIndex = playing.queueIndex,
            playedIds = playedIds,
            shuffle = playing.shuffle,
            actions =
                QueueActions(
                    onPlayIndex = viewModel::playQueueIndex,
                    onMove = viewModel::moveQueueItem,
                    onRemove = viewModel::removeQueueItem,
                    onReinsert = viewModel::reinsertQueueItem,
                    onClearUpcoming = viewModel::clearUpcoming,
                    onRestoreUpcoming = viewModel::restoreUpcoming,
                    onAddToPlaylist = { playlistTrack = it },
                    onGoToSearch = {
                        sheet.queueOpen = false
                        viewModel.requestTab(PlayerTab.SEARCH)
                        scope.launch { sheet.collapse() }
                        onGoToSearch()
                    },
                ),
            onDismiss = { sheet.queueOpen = false },
        )
    }

    if (sheet.soundOpen) {
        SoundSheet(
            soundName = sound.soundName,
            soundCurve = sound.soundCurve,
            eqStatus = sound.eqStatus,
            pathLabel = if (playing != null) stringResource(R.string.source_soundcloud) else null,
            activeContext = sound.activeContext,
            onContextModeChanged = onContextModeChanged,
            onOpenEqualizer = {
                sheet.soundOpen = false
                scope.launch { sheet.collapse() }
                onOpenEqualizer()
            },
            onDismiss = { sheet.soundOpen = false },
        )
    }

    playlistTrack?.let { track ->
        PlaylistDialog(
            track = track,
            playlists = playlists,
            onAddTo = { playlist ->
                viewModel.addToPlaylist(playlist, track)
                playlistTrack = null
            },
            onCreate = { name ->
                viewModel.createPlaylist(name, track)
                playlistTrack = null
            },
            onDismiss = { playlistTrack = null },
        )
    }
}

// How far the layer moves down while the back gesture is held: a hint, not the whole way.
private const val BACK_PREVIEW_SHARE = 0.25f
