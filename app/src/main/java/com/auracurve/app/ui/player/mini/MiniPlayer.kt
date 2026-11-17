package com.auracurve.app.ui.player.mini

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.player.playback.PlayerNotice
import com.auracurve.app.ui.eq.EqStatusKind
import com.auracurve.app.ui.eq.EqStatusLamp
import com.auracurve.app.ui.equalizer.SonicStyle
import com.auracurve.app.ui.equalizer.lineBrush
import com.auracurve.app.ui.player.CoverImage
import com.auracurve.app.ui.player.PlayKey
import com.auracurve.app.ui.player.common.PreviewTag
import com.auracurve.app.ui.player.isProblem
import com.auracurve.app.ui.player.noticeText
import com.auracurve.app.ui.player.nowplaying.NowPlayingUi
import com.auracurve.app.ui.theme.spacing

// The mini player: docked above the navigation bar in both tabs, as long as something is
// loaded. Tap or pull it up for "Now playing"; play/pause and next work without leaving the
// page. The lamp is the EQ status (same shapes as EqStatusLine) and opens the sound sheet.
@Composable
fun MiniPlayer(
    ui: NowPlayingUi,
    eqStatus: EqStatusKind,
    onExpand: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragStopped: (Float) -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onOpenSound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val description = stringResource(R.string.mini_player_description, ui.title, ui.artist)
    val state = stringResource(if (ui.isPlaying) R.string.state_playing else R.string.state_paused)
    val playPauseLabel = stringResource(if (ui.isPlaying) R.string.player_pause else R.string.player_play)
    val nextLabel = stringResource(R.string.player_next)
    val openLabel = stringResource(R.string.player_open_full)
    // A floating card with a little room around it, so it reads as one control above the tabs.
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.small, vertical = 6.dp)
                .clip(MaterialTheme.shapes.large)
                .draggable(
                    orientation = Orientation.Vertical,
                    // Up is a negative delta on screen; the sheet counts pulling up as positive.
                    state = rememberDraggableState { delta -> onDrag(-delta) },
                    onDragStopped = { velocity -> onDragStopped(-velocity) },
                ).clickable(onClickLabel = openLabel, onClick = onExpand)
                .semantics {
                    contentDescription = description
                    stateDescription = state
                    customActions =
                        listOf(
                            CustomAccessibilityAction(playPauseLabel) {
                                onTogglePlayback()
                                true
                            },
                            CustomAccessibilityAction(nextLabel) {
                                onNext()
                                true
                            },
                        )
                },
        color = SonicStyle.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column {
            // The playback position as the top edge: also the hint that there is more above.
            ProgressLine(progress = if (ui.durationMs > 0) ui.progress else 0f)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(start = spacing.small, end = spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(url = ui.artworkUrl, modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.medium))
                Spacer(modifier = Modifier.width(spacing.small))
                Column(modifier = Modifier.weight(1f).padding(vertical = spacing.small)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ui.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (ui.isPreview) {
                            Spacer(modifier = Modifier.width(spacing.extraSmall))
                            PreviewTag()
                        }
                    }
                    MiniSubtitle(ui)
                }
                // The EQ lamp is its own target: 48 dp, opens the sound sheet.
                val lampLabel = stringResource(eqStatus.labelRes)
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClickLabel = stringResource(R.string.sound_sheet_open), onClick = onOpenSound)
                            .semantics { contentDescription = lampLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    EqStatusLamp(kind = eqStatus)
                }
                // 44 dp key inside a 48 dp touch target.
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    PlayKey(onClick = onTogglePlayback, enabled = true, size = 44.dp) {
                        if (ui.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(
                                imageVector = if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = playPauseLabel,
                            )
                        }
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = nextLabel)
                }
            }
        }
    }
}

// The playback position as a thin gradient line along the card's top edge: also the hint that
// there is more above.
@Composable
private fun ProgressLine(progress: Float) {
    val style = SonicStyle
    Canvas(modifier = Modifier.fillMaxWidth().height(3.dp)) {
        drawRect(color = style.surfaceVariant)
        val width = size.width * progress.coerceIn(0f, 1f)
        if (width > 0f) {
            drawRect(brush = style.lineBrush(0f, size.width), size = Size(width, size.height))
        }
    }
}

// Artist, or what the player has to say (no connection, skipped track) with a symbol, so the
// state does not hang on the color alone.
@Composable
private fun MiniSubtitle(ui: NowPlayingUi) {
    val notice = ui.notice
    if (notice == null) {
        Text(
            text = ui.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    val color = if (notice.isProblem()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (notice == PlayerNotice.WaitingForNetwork) Icons.Default.CloudOff else Icons.Default.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = noticeText(notice),
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
