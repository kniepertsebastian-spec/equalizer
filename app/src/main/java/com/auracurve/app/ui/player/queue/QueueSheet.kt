package com.auracurve.app.ui.player.queue

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
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
import com.auracurve.app.R
import com.auracurve.app.player.model.TrackItem
import com.auracurve.app.playlist.PlayerFormat
import com.auracurve.app.playlist.QueueRowKeys
import com.auracurve.app.ui.player.CoverThumb
import com.auracurve.app.ui.player.EmptyState
import com.auracurve.app.ui.player.QueueEntryMenu
import com.auracurve.app.ui.player.TrackPlayState
import com.auracurve.app.ui.player.TrackStateIcon
import com.auracurve.app.ui.player.common.SwipeAwayRow
import com.auracurve.app.ui.player.markWhenCurrent
import com.auracurve.app.ui.player.textAlpha
import com.auracurve.app.ui.player.trackPlayState
import com.auracurve.app.ui.theme.SonicGraphiteControl
import com.auracurve.app.ui.theme.SonicPaper
import com.auracurve.app.ui.theme.SonicSteel
import com.auracurve.app.ui.theme.SonicViolet
import com.auracurve.app.ui.theme.spacing
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
            // Graphite with an amber action, like the rest of the player (Material's default is a
            // light, purple snackbar).
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = SonicGraphiteControl,
                    contentColor = SonicPaper,
                    actionColor = SonicViolet,
                )
            }
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

// One entry while the list is shown. [origin] is its position in the queue the player published
// (what a command for it names); [key] stays with the entry while others are removed or moved.
private data class QueueEntry(
    val origin: Int,
    val key: Long,
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
    // Rows are keyed by entry, not by position: keyed by position, the state of a removed row
    // (how far it was swiped away) went to the entry that took its place, which then showed up
    // half off the screen.
    val rowKeys = remember { QueueRowKeys() }
    // A local copy that follows the finger; replaced whenever the player publishes its queue.
    val entries =
        remember(queue) {
            val keys = rowKeys.assign(queue.map { it.id })
            queue.mapIndexed { index, track -> QueueEntry(index, keys[index], track) }.toMutableStateList()
        }
    var draggedKey by remember(queue) { mutableStateOf<Long?>(null) }
    var dragOffset by remember(queue) { mutableFloatStateOf(0f) }
    val currentTrackId = queue.getOrNull(currentIndex)?.id

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = spacing.large)) {
        items(entries, key = { entry -> entry.key }) { entry ->
            val track = entry.track
            // Commands name the entry by its place in the published queue, which is where the
            // row is whenever nothing is being dragged.
            val position = entry.origin
            val isCurrent = position == currentIndex
            val playState = if (isCurrent) TrackPlayState.CURRENT else trackPlayState(track.id, currentTrackId, playedIds)
            val dragging = draggedKey == entry.key
            val canMoveUp = position > 0
            val canMoveDown = position < queue.lastIndex
            val removeEntry = {
                rowKeys.forget(entry.key)
                actions.onRemove(position)
                onRemoved(track, position)
            }
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
                                    if (canMoveUp) {
                                        add(
                                            CustomAccessibilityAction(moveUp) {
                                                actions.onMove(position, position - 1)
                                                true
                                            },
                                        )
                                    }
                                    if (canMoveDown) {
                                        add(
                                            CustomAccessibilityAction(moveDown) {
                                                actions.onMove(position, position + 1)
                                                true
                                            },
                                        )
                                    }
                                    add(
                                        CustomAccessibilityAction(remove) {
                                            removeEntry()
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
                SwipeAwayRow(onDismiss = removeEntry) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surface)
                                .markWhenCurrent(playState)
                                .clickable { actions.onPlayIndex(position) }
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
                            canMoveUp = canMoveUp,
                            canMoveDown = canMoveDown,
                            onMoveUp = { actions.onMove(position, position - 1) },
                            onMoveDown = { actions.onMove(position, position + 1) },
                            onRemove = removeEntry,
                            onAddToPlaylist = { actions.onAddToPlaylist(track) },
                        )
                        DragHandle(
                            title = track.title,
                            onStart = {
                                draggedKey = entry.key
                                dragOffset = 0f
                            },
                            onDrag = { dy ->
                                dragOffset += dy
                                val from = entries.indexOfFirst { it.key == entry.key }
                                val moved = QueueReorder.step(listState.layoutInfo.visibleItemsInfo, entry.key, from, dragOffset)
                                if (moved != null && from >= 0) {
                                    // The list holds on to its first visible row: when that row is part
                                    // of the swap it would scroll along. Asking for the same scroll
                                    // position keeps the list where it is.
                                    val first = listState.firstVisibleItemIndex
                                    if (from == first || moved.toIndex == first) {
                                        listState.requestScrollToItem(first, listState.firstVisibleItemScrollOffset)
                                    }
                                    entries.add(moved.toIndex, entries.removeAt(from))
                                    dragOffset += moved.offsetCorrection
                                }
                            },
                            onEnd = {
                                val to = entries.indexOfFirst { it.key == entry.key }
                                draggedKey = null
                                dragOffset = 0f
                                if (to >= 0 && to != position) actions.onMove(position, to)
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
