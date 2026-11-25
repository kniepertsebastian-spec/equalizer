package com.auracurve.app.ui.player.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.playlist.SavedPlaylist
import com.auracurve.app.ui.components.cyber.rememberPulse
import com.auracurve.app.ui.player.CoverThumb
import com.auracurve.app.ui.player.PlayKey
import com.auracurve.app.ui.theme.CyberColors
import com.auracurve.app.ui.theme.SonicPaper
import com.auracurve.app.ui.theme.SonicSignal
import com.auracurve.app.ui.theme.SonicSteel
import com.auracurve.app.ui.theme.glassSurface

// One entry of the SoundCloud library, as a glass row. With [onOpen] a tap on the row opens the
// list (roadmap 17) and only the round play key plays it right away; without it (search matches)
// the whole row plays.
@Composable
internal fun LibraryRow(
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
    showCover: Boolean = false,
    artworkUrl: String? = null,
    menu: (@Composable () -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    // Radio: the first part of the detail line ("Live"), shown as a green label with a dot in
    // front of [subtitle]; read out as one line, the same text as before.
    liveLabel: String? = null,
    // This row is what plays now: a pulse ring around its play key.
    playing: Boolean = false,
    // A featured entry (the likes): its play key glows.
    featured: Boolean = false,
) {
    val openLabel = stringResource(R.string.playlist_detail_open, title)
    GlassRow {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .then(
                        if (onOpen != null) {
                            Modifier.clickable(onClickLabel = openLabel, onClick = onOpen)
                        } else {
                            Modifier.clickable(onClick = onPlay)
                        },
                    ).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showCover) {
                CoverThumb(url = artworkUrl, seed = title, size = 40.dp)
                Spacer(modifier = Modifier.size(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                RowTitle(title)
                if (liveLabel != null) {
                    LiveDetail(liveLabel, subtitle)
                } else {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = SonicSteel, maxLines = 1)
                }
            }
        }
        Box(
            modifier = Modifier.size(48.dp).then(if (playing) Modifier.pulseRing() else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            PlayKey(onClick = onPlay, enabled = true, size = 44.dp, glow = featured, playing = playing) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.player_play))
            }
        }
        CompositionLocalProvider(LocalContentColor provides SonicSteel) { menu?.invoke() }
    }
}

// A saved playlist as a glass row: tap the row to open it in the detail view (roadmap 17); play,
// delete and sending to SoundCloud stay on the row.
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
    GlassRow {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = openLabel, onClick = onOpen)
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverThumb(url = playlist.tracks.firstOrNull()?.artworkUrl, seed = playlist.title, size = 40.dp)
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                RowTitle(playlist.title)
                Text(
                    text =
                        stringResource(R.string.playlist_row_count, playlist.tracks.size) +
                            if (playlist.soundCloudId != null) stringResource(R.string.playlist_row_on_soundcloud) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicSteel,
                )
            }
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
            PlayKey(onClick = onPlay, enabled = playlist.tracks.isNotEmpty(), size = 44.dp, glow = false) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.playlist_play))
            }
        }
    }
}

// The glass under a list row. It does not clip, so a play key's halo may reach past its edge.
@Composable
private fun GlassRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .glassSurface(MaterialTheme.shapes.small)
                .padding(start = 8.dp, top = 2.dp, bottom = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RowTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = SonicPaper,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// "● LIVE · DE · MP3 192 kbit/s": the live part as a green label with a dot (a real state, so
// green; the dot and the capitals say it without color too), the rest as before. TalkBack reads
// the original line, not the capitals.
@Composable
private fun LiveDetail(
    live: String,
    rest: String,
) {
    val spoken = if (rest.isBlank()) live else "$live · $rest"
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(SonicSignal))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = live.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = SonicSignal,
            maxLines = 1,
        )
        if (rest.isNotBlank()) {
            Text(
                text = " · $rest",
                style = MaterialTheme.typography.bodySmall,
                color = SonicSteel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// A ring that breathes around the play key of what plays now, just outside the key; steady while
// animations are off. The shape says "on air" too, not only the color.
@Composable
private fun Modifier.pulseRing(): Modifier {
    val pulse = rememberPulse(running = true, low = 0.45f, high = 1f, periodMs = 1600)
    return drawWithCache {
        val stroke = Stroke(width = 2.dp.toPx())
        val radius = size.minDimension / 2f + 1.dp.toPx()
        onDrawBehind { drawCircle(CyberColors.cyan, radius, alpha = pulse?.value ?: 1f, style = stroke) }
    }
}
