package com.hardbasseq.eq.ui.player.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.hardbasseq.eq.R
import com.hardbasseq.eq.playlist.PlayerFormat
import com.hardbasseq.eq.ui.player.CoverThumb
import com.hardbasseq.eq.ui.player.EmptyState
import com.hardbasseq.eq.ui.player.QueueEntryMenu
import com.hardbasseq.eq.ui.player.TrackPlayState
import com.hardbasseq.eq.ui.player.TrackStateIcon
import com.hardbasseq.eq.ui.player.common.SwipeAwayRow
import com.hardbasseq.eq.ui.player.markWhenCurrent
import com.hardbasseq.eq.ui.player.textAlpha
import com.hardbasseq.eq.ui.player.trackPlayState
import com.hardbasseq.eq.ui.theme.SonicPaper
import com.hardbasseq.eq.ui.theme.SonicSteel
import com.hardbasseq.eq.ui.theme.spacing
import com.soundcloud.equalizer.player.model.TrackItem
import kotlinx.coroutines.launch

class QueueActions(
    val onPlayIndex: (Int) -> Unit,
    val onMove: (from: Int, to: Int) -> Unit,
    val onRemove: (Int) -> Unit,
    val onReinsert: (TrackItem, Int) -> Unit,
    val onClearUpcoming: () -> List<TrackItem>,
    val onRestoreUpcoming: (List<TrackItem>) -> Unit,
    val onAddToPlaylist: (TrackItem) -> Unit,
    val onGoToSearch: () -> Unit,
)

// The queue as a bottom sheet over "Now playing". Rows are dragged by their handle (the order
// goes to the player once, when the finger lifts) and swiped sideways to remove them, with Undo.
// Every gesture also exists as a TalkBack action and in the row's menu.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<TrackItem>,
    currentIndex: Int,
    playedIds: Set<Long>,
    shuffle: Boolean,
    actions: QueueActions,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val undoLabel = stringResource(R.string.action_undo)
    val showUndo: (String, () -> Unit) -> Unit = { message, undo ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Box {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                QueueHeader(
                    count = queue.size,
                    shuffle = shuffle,
                    canClear = currentIndex in queue.indices && currentIndex < queue.lastIndex,
                    onClearUpcoming = {
                        val removed = actions.onClearUpcoming()
                        if (removed.isNotEmpty()) {
                            showUndo(
                                resources.getQuantityString(R.plurals.queue_cleared, removed.size, removed.size),
                            ) { actions.onRestoreUpcoming(removed) }
                        }
                    },
                )
                if (queue.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.queue_empty_title),
                        text = stringResource(R.string.queue_empty_text),
                        actionLabel = stringResource(R.string.queue_go_search),
                        onAction = actions.onGoToSearch,
                    )
                } else {
                    ReorderableQueue(
                        queue = queue,
                        currentIndex = currentIndex,
                        playedIds = playedIds,
                        actions = actions,
                        onRemoved = { track, index ->
                            showUndo(resources.getString(R.string.queue_removed, track.title)) { actions.onReinsert(track, index) }
                        },
                    )
                }
            }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}

@Composable
private fun QueueHeader(
    count: Int,
    shuffle: Boolean,
    canClear: Boolean,
    onClearUpcoming: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.medium)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pluralStringResource(R.plurals.queue_header, count, count),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SonicPaper,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (canClear) {
                TextButton(onClick = onClearUpcoming) { Text(stringResource(R.string.queue_clear_upcoming)) }
            }
        }
        if (shuffle) {
            Text(
                text = stringResource(R.string.queue_shuffle_order_hint),
                style = MaterialTheme.typography.bodySmall,
                color = SonicSteel,
            )
        }
    }
}

// One entry while the list is shown: [origin] is its position when the sheet got this queue,
// so dragging can report where it came from.
private data class QueueEntry(
    val origin: Int,
    val track: TrackItem,
)

@Composable
private fun ReorderableQueue(
    queue: List<TrackItem>,
    currentIndex: Int,
    playedIds: Set<Long>,
    actions: QueueActions,
    onRemoved: (TrackItem, Int) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))
    // A local copy that follows the finger; replaced whenever the player publishes its queue.
    val entries = remember(queue) { queue.mapIndexed { i, t -> QueueEntry(i, t) }.toMutableStateList() }
    var draggedOrigin by remember(queue) { mutableStateOf<Int?>(null) }
    var dragOffset by remember(queue) { mutableFloatStateOf(0f) }
    val currentTrackId = queue.getOrNull(currentIndex)?.id

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = spacing.large)) {
        itemsIndexed(entries, key = { _, entry -> entry.origin }) { index, entry ->
            val track = entry.track
            val isCurrent = entry.origin == currentIndex
            val playState = if (isCurrent) TrackPlayState.CURRENT else trackPlayState(track.id, currentTrackId, playedIds)
            val dragging = draggedOrigin == entry.origin
            val moveUp = stringResource(R.string.queue_move_up)
            val moveDown = stringResource(R.string.queue_move_down)
            val remove = stringResource(R.string.queue_remove)
            val addToPlaylist = stringResource(R.string.queue_add_to_playlist)
            Box(
                modifier =
                    Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .padding(horizontal = spacing.small, vertical = 2.dp)
                        .semantics {
                            customActions =
                                buildList {
                                    if (index > 0) {
                                        add(
                                            CustomAccessibilityAction(moveUp) {
                                                actions.onMove(index, index - 1)
                                                true
                                            },
                                        )
                                    }
                                    if (index < entries.lastIndex) {
                                        add(
                                            CustomAccessibilityAction(moveDown) {
                                                actions.onMove(index, index + 1)
                                                true
                                            },
                                        )
                                    }
                                    add(
                                        CustomAccessibilityAction(remove) {
                                            actions.onRemove(index)
                                            onRemoved(track, index)
                                            true
                                        },
                                    )
                                    add(
                                        CustomAccessibilityAction(addToPlaylist) {
                                            actions.onAddToPlaylist(track)
                                            true
                                        },
                                    )
                                }
                        },
            ) {
                SwipeAwayRow(onDismiss = {
                    actions.onRemove(index)
                    onRemoved(track, index)
                }) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surface)
                                .markWhenCurrent(playState)
                                .clickable { actions.onPlayIndex(index) }
                                .padding(start = spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { TrackStateIcon(playState) }
                        CoverThumb(url = track.artworkUrl)
                        Spacer(modifier = Modifier.size(spacing.small))
                        Column(modifier = Modifier.weight(1f).padding(vertical = spacing.small)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = SonicPaper.copy(alpha = playState.textAlpha()),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text =
                                    listOf(track.artist, PlayerFormat.duration(track.durationMs).takeIf { track.durationMs > 0 })
                                        .filterNotNull()
                                        .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = SonicSteel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        QueueEntryMenu(
                            trackTitle = track.title,
                            canMoveUp = index > 0,
                            canMoveDown = index < entries.lastIndex,
                            onMoveUp = { actions.onMove(index, index - 1) },
                            onMoveDown = { actions.onMove(index, index + 1) },
                            onRemove = {
                                actions.onRemove(index)
                                onRemoved(track, index)
                            },
                            onAddToPlaylist = { actions.onAddToPlaylist(track) },
                        )
                        DragHandle(
                            title = track.title,
                            onStart = {
                                draggedOrigin = entry.origin
                                dragOffset = 0f
                            },
                            onDrag = { dy ->
                                dragOffset += dy
                                val moved = QueueReorder.step(listState.layoutInfo.visibleItemsInfo, entry.origin, dragOffset)
                                if (moved != null) {
                                    val from = entries.indexOfFirst { it.origin == entry.origin }
                                    entries.add(moved.toIndex, entries.removeAt(from))
                                    dragOffset += moved.offsetCorrection
                                }
                            },
                            onEnd = {
                                val to = entries.indexOfFirst { it.origin == entry.origin }
                                draggedOrigin = null
                                dragOffset = 0f
                                if (to >= 0 && to != entry.origin) actions.onMove(entry.origin, to)
                            },
                        )
                    }
                }
            }
        }
    }
}

// The handle that starts a drag: no long press needed, it is only a handle.
@Composable
private fun DragHandle(
    title: String,
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: () -> Unit,
) {
    // The gesture outlives recompositions; always call the latest callbacks.
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start() },
                        onDragEnd = { end() },
                        onDragCancel = { end() },
                    ) { change, amount ->
                        change.consume()
                        drag(amount.y)
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.DragHandle, contentDescription = stringResource(R.string.queue_reorder_handle, title), tint = SonicSteel)
    }
}
