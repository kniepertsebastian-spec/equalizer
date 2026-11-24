package com.auracurve.app.ui.player.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.player.model.TrackItem
import com.auracurve.app.playlist.PlaylistSearch
import com.auracurve.app.playlist.SavedTrack
import com.auracurve.app.ui.player.CoverThumb
import com.auracurve.app.ui.player.OpenedPlaylistUiState
import com.auracurve.app.ui.player.PillTextField
import com.auracurve.app.ui.player.TrackQueueMenu
import com.auracurve.app.ui.player.TrackStateIcon
import com.auracurve.app.ui.player.markWhenCurrent
import com.auracurve.app.ui.player.textAlpha
import com.auracurve.app.ui.player.trackPlayState
import com.auracurve.app.ui.theme.SonicPaper
import com.auracurve.app.ui.theme.SonicSteel
import com.auracurve.app.ui.theme.spacing

// Roadmap 17: one opened playlist (saved on the device, a SoundCloud playlist or the likes) as its
// own scrollable page. A fixed bar on top (back, title, actions for saved lists), then one
// LazyColumn: a header with cover, "Play all", "Shuffle" and the search, then the tracks.
//
// The bottom is kept clear by the shell: the NavHost the player tab lives in is padded by the
// height of the mini player and the navigation bar (incl. the system navigation bar), so the
// last track always ends above the mini player and stays tappable. Only the status bar on top is
// handled here, as everywhere in the player tab.
@Composable
fun PlaylistDetailScreen(
    state: OpenedPlaylistUiState,
    currentTrackId: Long?,
    playedIds: Set<Long>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    // The track's place in the whole playlist (not in the search result).
    onPlayTrack: (Int) -> Unit,
    onPlayNext: (TrackItem) -> Unit,
    onAddToQueue: (TrackItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    // Null where the list cannot lose tracks (imported, SoundCloud).
    onRemoveTrack: ((Long) -> Unit)? = null,
    // Null hides the action (SoundCloud lists, the "store" flavor).
    onSendToSoundCloud: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
) {
    val spacing = MaterialTheme.spacing
    val bars = WindowInsets.systemBars.asPaddingValues()
    // Per opened list: a search typed for one playlist does not carry over to the next.
    var search by rememberSaveable(state.source) { mutableStateOf("") }
    val hits =
        PlaylistSearch.filter(
            state.tracks.map { SavedTrack(id = it.id, title = it.title, artist = it.artist) },
            search,
        )
    val canPlay = state.tracks.isNotEmpty() && !state.isLoading
    CompositionLocalProvider(LocalContentColor provides SonicPaper) {
        Column(modifier = modifier.fillMaxSize()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = bars.calculateTopPadding() + spacing.small, start = spacing.small, end = spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.playlist_detail_back))
                }
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (onSendToSoundCloud != null) {
                    IconButton(onClick = onSendToSoundCloud, enabled = state.tracks.isNotEmpty()) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = stringResource(R.string.playlist_to_soundcloud),
                            tint = SonicSteel,
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.playlist_delete), tint = SonicSteel)
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag(PLAYLIST_DETAIL_LIST_TAG),
                contentPadding =
                    PaddingValues(
                        start = spacing.medium,
                        end = spacing.medium,
                        top = spacing.small,
                        bottom = spacing.large,
                    ),
                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
            ) {
                item(key = "header") {
                    PlaylistDetailHeader(
                        state = state,
                        canPlay = canPlay,
                        search = search,
                        onSearchChange = { search = it },
                        onPlayAll = onPlayAll,
                        onShuffle = onShuffle,
                    )
                }
                when {
                    state.isLoading ->
                        item(key = "loading") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.large),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(spacing.small))
                                Text(stringResource(R.string.playlist_detail_loading), color = SonicSteel)
                            }
                        }

                    state.errorMessage != null ->
                        item(key = "error") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.large),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.playlist_detail_error, state.errorMessage),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.size(spacing.small))
                                Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                            }
                        }

                    state.tracks.isEmpty() ->
                        item(key = "empty") {
                            Text(
                                text =
                                    stringResource(
                                        if (state.canRemoveTracks) R.string.playlist_empty_hint else R.string.playlist_detail_empty_remote,
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SonicSteel,
                                modifier = Modifier.padding(vertical = spacing.large),
                            )
                        }

                    hits.isEmpty() ->
                        item(key = "no-hits") {
                            Text(
                                text = stringResource(R.string.playlist_search_none, search),
                                style = MaterialTheme.typography.bodySmall,
                                color = SonicSteel,
                            )
                        }

                    else ->
                        items(hits, key = { hit -> "${hit.track.id}-${hit.index}" }) { hit ->
                            val track = state.tracks[hit.index]
                            PlaylistTrackRow(
                                track = track,
                                index = hit.index,
                                currentTrackId = currentTrackId,
                                playedIds = playedIds,
                                onPlay = { onPlayTrack(hit.index) },
                                onPlayNext = { onPlayNext(track) },
                                onAddToQueue = { onAddToQueue(track) },
                                onRemove = onRemoveTrack?.let { remove -> { remove(track.id) } },
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailHeader(
    state: OpenedPlaylistUiState,
    canPlay: Boolean,
    search: String,
    onSearchChange: (String) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverThumb(url = state.artworkUrl, seed = state.title, size = 96.dp)
            Spacer(modifier = Modifier.size(spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SonicPaper,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = state.subtitle, style = MaterialTheme.typography.bodySmall, color = SonicSteel)
            }
        }
        Spacer(modifier = Modifier.size(spacing.medium))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onPlayAll, enabled = canPlay, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.size(spacing.small))
                Text(stringResource(R.string.playlist_detail_play_all))
            }
            OutlinedButton(onClick = onShuffle, enabled = canPlay, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Default.Shuffle, contentDescription = null)
                Spacer(modifier = Modifier.size(spacing.small))
                Text(stringResource(R.string.playlist_detail_shuffle))
            }
        }
        // A search box once the list is long enough to need one.
        if (state.tracks.size >= SEARCH_MIN_TRACKS) {
            Spacer(modifier = Modifier.size(spacing.small))
            PillTextField(
                value = search,
                onValueChange = onSearchChange,
                placeholder = stringResource(R.string.playlist_search_hint),
                leadingIcon = Icons.Default.Search,
            )
        }
    }
}

// One track of the opened playlist: tap to play the playlist from here; the menu queues it,
// starts from here or (saved lists made on the device) removes it.
@Composable
private fun PlaylistTrackRow(
    track: TrackItem,
    index: Int,
    currentTrackId: Long?,
    playedIds: Set<Long>,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val playState = trackPlayState(track.id, currentTrackId, playedIds)
    val playLabel = stringResource(R.string.playlist_play_from_here)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .markWhenCurrent(playState)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = playLabel, onClick = onPlay)
                .testTag("$PLAYLIST_TRACK_TAG_PREFIX$index"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { TrackStateIcon(playState) }
        CoverThumb(url = track.artworkUrl, seed = track.title)
        Spacer(modifier = Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = SonicPaper.copy(alpha = playState.textAlpha()),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.labelSmall,
                color = SonicSteel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CompositionLocalProvider(LocalContentColor provides SonicSteel) {
            TrackQueueMenu(
                trackTitle = track.title,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onPlayFromHere = onPlay,
                onRemoveFromPlaylist = onRemove,
            )
        }
    }
}

// From this many tracks on, an opened playlist shows a search box.
internal const val SEARCH_MIN_TRACKS = 6

// For tests: the detail list and each track row (by its place in the whole playlist).
const val PLAYLIST_DETAIL_LIST_TAG = "playlist-detail-list"
const val PLAYLIST_TRACK_TAG_PREFIX = "playlist-track-"
