package com.hardbasseq.eq.ui.player.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.link.SpotifyImportState
import com.hardbasseq.eq.playlist.PlayerFormat
import com.hardbasseq.eq.playlist.SavedPlaylist
import com.hardbasseq.eq.ui.player.BridgeUiState
import com.hardbasseq.eq.ui.player.EmptyState
import com.hardbasseq.eq.ui.player.ImportUiState
import com.hardbasseq.eq.ui.player.LibraryUiState
import com.hardbasseq.eq.ui.player.PlayerViewModel
import com.hardbasseq.eq.ui.theme.SonicRule
import com.hardbasseq.eq.ui.theme.SonicSteel
import com.hardbasseq.eq.ui.theme.spacing

// The "Playlists" tab of the player screen: importing from a link, the playlists saved on the
// device, matches for a shared YouTube/Spotify link and the signed-in user's SoundCloud library.
fun LazyListScope.playlistsTabItems(
    viewModel: PlayerViewModel,
    playlists: List<SavedPlaylist>,
    importState: ImportUiState,
    pendingImport: SpotifyImportState?,
    bridgeState: BridgeUiState?,
    signedIn: Boolean,
    libraryState: LibraryUiState,
    currentTrackId: Long?,
    playedIds: Set<Long>,
    onNewPlaylist: () -> Unit,
    onMerge: () -> Unit,
) {
    item {
        val spacing = MaterialTheme.spacing
        var linkText by rememberSaveable { mutableStateOf("") }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, SonicRule),
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                Text(
                    stringResource(R.string.playlists_import_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text =
                        stringResource(R.string.playlists_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicSteel,
                )
                Spacer(modifier = Modifier.height(spacing.small))
                OutlinedTextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://soundcloud.com/…") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (importState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    importState.message?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (importState.isError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    SonicSteel
                                },
                            modifier = Modifier.weight(1f).padding(horizontal = spacing.small),
                        )
                    } ?: Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            viewModel.importFromText(linkText)
                            linkText = ""
                        },
                        enabled = linkText.isNotBlank() && !importState.isLoading,
                    ) {
                        Text(stringResource(R.string.playlists_add_button))
                    }
                }
                // A long Spotify import that stopped (app closed, no connection): go on or drop it.
                // While one is running the saved position is only a checkpoint, not a pause.
                pendingImport?.takeIf { !importState.isLoading }?.let { pending ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text =
                                stringResource(
                                    R.string.spotify_import_paused,
                                    pending.title,
                                    pending.nextIndex,
                                    pending.total,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = SonicSteel,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::resumeSpotifyImport) { Text(stringResource(R.string.action_resume)) }
                        TextButton(onClick = viewModel::discardSpotifyImport) { Text(stringResource(R.string.action_discard)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onNewPlaylist() }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                        Spacer(modifier = Modifier.size(spacing.small))
                        Text(stringResource(R.string.playlists_new_button))
                    }
                    if (playlists.size >= 2) {
                        TextButton(onClick = { onMerge() }) { Text(stringResource(R.string.playlists_merge)) }
                    }
                    if (playlists.isNotEmpty()) {
                        TextButton(onClick = { viewModel.syncToSoundCloud(playlists) }, enabled = !importState.isLoading) {
                            Text(stringResource(R.string.playlists_all_to_soundcloud))
                        }
                    }
                }
                if (playlists.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = spacing.small))
                    playlists.forEach { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            onPlay = { viewModel.playPlaylist(playlist) },
                            onPlayTrack = { viewModel.playPlaylist(playlist, it) },
                            onDelete = { viewModel.deletePlaylist(playlist) },
                            onSendToSoundCloud = { viewModel.syncToSoundCloud(listOf(playlist)) },
                            onRemoveTrack = { viewModel.removeFromPlaylist(playlist, it) },
                            onPlayNext = viewModel::playNextFromPlaylist,
                            onAddToQueue = viewModel::addToQueueFromPlaylist,
                            currentTrackId = currentTrackId,
                            playedIds = playedIds,
                        )
                    }
                }
            }
        }
    }
    if (playlists.isEmpty()) {
        item {
            val spacing = MaterialTheme.spacing
            EmptyState(
                title = stringResource(R.string.playlists_empty_title),
                text = stringResource(R.string.playlists_empty_text),
                actionLabel = stringResource(R.string.playlists_new_button),
                onAction = { onNewPlaylist() },
            )
        }
    }
    bridgeState?.let { bridge ->
        item {
            val spacing = MaterialTheme.spacing
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, SonicRule),
            ) {
                Column(modifier = Modifier.padding(spacing.medium)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.bridge_found_title, bridge.label),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::dismissBridge) { Text(stringResource(R.string.action_close)) }
                    }
                    Text(
                        text =
                            if (bridge.startedAutomatically) {
                                stringResource(R.string.bridge_best_playing)
                            } else {
                                stringResource(R.string.bridge_no_sure_match)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = SonicSteel,
                    )
                    bridge.matches.forEach { match ->
                        LibraryRow(
                            title = match.track.title,
                            subtitle =
                                listOf(
                                    match.track.artist,
                                    PlayerFormat.duration(match.track.durationMs),
                                    "${(match.score * 100).toInt()} %",
                                ).joinToString(" · "),
                            onPlay = { viewModel.playBridgeMatch(match) },
                            showCover = true,
                            artworkUrl = match.track.artworkUrl,
                        )
                    }
                }
            }
        }
    }
    if (signedIn) {
        item {
            val spacing = MaterialTheme.spacing
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, SonicRule),
            ) {
                Column(modifier = Modifier.padding(spacing.medium)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.library_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (libraryState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = viewModel::loadLibrary) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.library_reload))
                            }
                        }
                    }
                    libraryState.message?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (libraryState.isError) MaterialTheme.colorScheme.error else SonicSteel,
                        )
                    }
                    LibraryRow(
                        title = stringResource(R.string.library_likes_title),
                        subtitle = stringResource(R.string.library_likes_desc),
                        onPlay = viewModel::playLikedTracks,
                    )
                }
            }
        }
        libraryState.library?.let { library ->
            if (library.own.isNotEmpty()) {
                item {
                    val spacing = MaterialTheme.spacing
                    Text(
                        stringResource(R.string.library_my_playlists),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(library.own, key = { "own-${it.id}" }) { playlist ->
                    LibraryRow(
                        title = playlist.title,
                        subtitle = stringResource(R.string.tracks_count, playlist.trackCount),
                        onPlay = { viewModel.playLibraryPlaylist(playlist) },
                        showCover = true,
                        artworkUrl = playlist.artworkUrl,
                    )
                }
            }
            if (library.liked.isNotEmpty()) {
                item {
                    val spacing = MaterialTheme.spacing
                    Text(
                        stringResource(R.string.library_liked_playlists),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(library.liked, key = { "liked-${it.id}" }) { playlist ->
                    LibraryRow(
                        title = playlist.title,
                        subtitle = stringResource(R.string.tracks_count, playlist.trackCount),
                        onPlay = { viewModel.playLibraryPlaylist(playlist) },
                        showCover = true,
                        artworkUrl = playlist.artworkUrl,
                    )
                }
            }
        }
    }
}
