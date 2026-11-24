package com.auracurve.app.ui.player.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.playlist.SavedPlaylist
import com.auracurve.app.ui.player.CoverThumb
import com.auracurve.app.ui.player.PlayKey
import com.auracurve.app.ui.player.RoundControlButton
import com.auracurve.app.ui.theme.SonicPaper
import com.auracurve.app.ui.theme.SonicSteel

// One entry of the SoundCloud library. With [onOpen] a tap on the row opens the list (roadmap 17)
// and only the round play button plays it right away; without it (search matches) the whole
// row plays.
@Composable
internal fun LibraryRow(
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
    showCover: Boolean = false,
    artworkUrl: String? = null,
    menu: (@Composable () -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
) {
    val openLabel = stringResource(R.string.playlist_detail_open, title)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .then(
                    if (onOpen != null) {
                        Modifier.clickable(onClickLabel = openLabel, onClick = onOpen)
                    } else {
                        Modifier.clickable(onClick = onPlay)
                    },
                ).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCover) {
            CoverThumb(url = artworkUrl, seed = title, size = 48.dp)
            Spacer(modifier = Modifier.size(12.dp))
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
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = SonicSteel, maxLines = 1)
        }
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            RoundControlButton(onClick = onPlay, enabled = true, size = 40.dp) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.player_play))
            }
        }
        CompositionLocalProvider(LocalContentColor provides SonicSteel) { menu?.invoke() }
    }
}

// A saved playlist: tap the row to open it in the detail view (roadmap 17); play, delete and
// sending to SoundCloud stay on the row.
@Composable
internal fun PlaylistRow(
    playlist: SavedPlaylist,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    // Null hides the action (the "store" flavor has no SoundCloud account to send to).
    onSendToSoundCloud: (() -> Unit)?,
) {
    val openLabel = stringResource(R.string.playlist_detail_open, playlist.title)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = openLabel, onClick = onOpen)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverThumb(url = playlist.tracks.firstOrNull()?.artworkUrl, seed = playlist.title, size = 48.dp)
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
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
        if (onSendToSoundCloud != null) {
            IconButton(onClick = onSendToSoundCloud, enabled = playlist.tracks.isNotEmpty()) {
                Icon(Icons.Default.CloudUpload, contentDescription = stringResource(R.string.playlist_to_soundcloud), tint = SonicSteel)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.playlist_delete), tint = SonicSteel)
        }
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            PlayKey(onClick = onPlay, enabled = playlist.tracks.isNotEmpty(), size = 40.dp, glow = false) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.playlist_play))
            }
        }
    }
}
