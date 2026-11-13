package com.hardbasseq.eq.ui.player.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.playlist.PlaylistSearch
import com.hardbasseq.eq.playlist.SavedPlaylist
import com.hardbasseq.eq.playlist.SavedTrack
import com.hardbasseq.eq.ui.player.CoverThumb
import com.hardbasseq.eq.ui.player.TrackQueueMenu
import com.hardbasseq.eq.ui.player.TrackStateIcon
import com.hardbasseq.eq.ui.player.markWhenCurrent
import com.hardbasseq.eq.ui.player.textAlpha
import com.hardbasseq.eq.ui.player.trackPlayState
import com.hardbasseq.eq.ui.theme.SonicPaper
import com.hardbasseq.eq.ui.theme.SonicSteel
import com.hardbasseq.eq.ui.theme.spacing

// One tappable entry of the SoundCloud library: tap anywhere (or the play button) to
// load its tracks and play them as the queue.
@Composable
internal fun LibraryRow(
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
    showCover: Boolean = false,
    artworkUrl: String? = null,
    menu: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onPlay)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCover) {
            CoverThumb(url = artworkUrl)
            Spacer(modifier = Modifier.size(MaterialTheme.spacing.small))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = SonicPaper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = SonicSteel)
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.player_play))
        }
        menu?.invoke()
    }
}

// A saved playlist: tap the name to show its tracks. Tracks of playlists made on the
// device can be removed there; imported ones are refreshed by importing the link again.
@Composable
internal fun PlaylistRow(
    playlist: SavedPlaylist,
    onPlay: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onDelete: () -> Unit,
    onSendToSoundCloud: () -> Unit,
    onRemoveTrack: (Long) -> Unit,
    onPlayNext: (SavedTrack) -> Unit,
    onAddToQueue: (SavedTrack) -> Unit,
    currentTrackId: Long?,
    playedIds: Set<Long>,
) {
    var expanded by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expanded = !expanded },
            ) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SonicPaper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        stringResource(R.string.playlist_row_count, playlist.tracks.size) +
                            if (playlist.soundCloudId != null) stringResource(R.string.playlist_row_on_soundcloud) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicSteel,
                )
            }
            IconButton(onClick = onSendToSoundCloud, enabled = playlist.tracks.isNotEmpty()) {
                Icon(Icons.Default.CloudUpload, contentDescription = stringResource(R.string.playlist_to_soundcloud))
            }
            IconButton(onClick = onPlay, enabled = playlist.tracks.isNotEmpty()) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.playlist_play))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.playlist_delete))
            }
        }
        if (expanded) {
            if (playlist.tracks.isEmpty()) {
                Text(
                    text = stringResource(R.string.playlist_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicSteel,
                )
            }
            // A search box once the list is long enough to need one.
            if (playlist.tracks.size >= SEARCH_MIN_TRACKS) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.playlist_search_hint)) },
                    singleLine = true,
                )
            }
            val hits = PlaylistSearch.filter(playlist.tracks, search)
            if (hits.isEmpty() && playlist.tracks.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.playlist_search_none, search),
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicSteel,
                )
            }
            hits.forEach { hit ->
                val track = hit.track
                val playState = trackPlayState(track.id, currentTrackId, playedIds)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp)
                            .markWhenCurrent(playState)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPlayTrack(hit.index) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { TrackStateIcon(playState) }
                    CoverThumb(url = track.artworkUrl)
                    Spacer(modifier = Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodySmall,
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
                    TrackQueueMenu(
                        trackTitle = track.title,
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onPlayFromHere = { onPlayTrack(hit.index) },
                        onRemoveFromPlaylist = if (playlist.sourceUrl == null) ({ onRemoveTrack(track.id) }) else null,
                    )
                }
            }
        }
    }
}

// From this many tracks on, an opened playlist shows a search box.
private const val SEARCH_MIN_TRACKS = 6
