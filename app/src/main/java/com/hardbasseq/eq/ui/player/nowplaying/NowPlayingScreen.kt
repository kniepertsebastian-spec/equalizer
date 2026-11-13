package com.hardbasseq.eq.ui.player.nowplaying

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.AudioDeviceType
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.playlist.PlayerFormat
import com.hardbasseq.eq.preset.TargetPoint
import com.hardbasseq.eq.ui.eq.EqStatusKind
import com.hardbasseq.eq.ui.eq.EqStatusLamp
import com.hardbasseq.eq.ui.equalizer.SonicStyle
import com.hardbasseq.eq.ui.player.ArtworkFrame
import com.hardbasseq.eq.ui.player.CoverImage
import com.hardbasseq.eq.ui.player.PlayKey
import com.hardbasseq.eq.ui.player.PlayerSoundChip
import com.hardbasseq.eq.ui.player.RepeatButton
import com.hardbasseq.eq.ui.player.ShuffleButton
import com.hardbasseq.eq.ui.player.common.PreviewTag
import com.hardbasseq.eq.ui.player.isProblem
import com.hardbasseq.eq.ui.player.noticeText
import com.hardbasseq.eq.ui.theme.spacing
import com.soundcloud.equalizer.player.playback.PlayerNotice

// What "Now playing" needs besides the track: the sound that is applied and where it goes.
data class NowPlayingContext(
    val eqStatus: EqStatusKind,
    val soundName: String,
    val soundCurve: List<TargetPoint>,
    val contextName: String?,
    val route: AudioRoute,
    val queueCount: Int,
    val signedIn: Boolean,
)

class NowPlayingActions(
    val onCollapse: () -> Unit,
    val onDrag: (Float) -> Unit,
    val onDragStopped: (Float) -> Unit,
    val onTogglePlayback: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeat: () -> Unit,
    val onAddToPlaylist: () -> Unit,
    val onOpenSound: () -> Unit,
    val onOpenQueue: () -> Unit,
)

// "Now playing" full screen: pulled up from the mini player, dragged down (on the header or the
// cover) or closed with the arrow/back to collapse it again. Portrait stacks cover and controls;
// landscape puts them side by side. The EQ curve chip and its lamp stay the link to the sound.
@Composable
fun NowPlayingScreen(
    ui: NowPlayingUi,
    context: NowPlayingContext,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val dragModifier =
        Modifier.draggable(
            orientation = Orientation.Vertical,
            // Down is a positive delta on screen and closes the sheet (negative for the sheet).
            state = rememberDraggableState { delta -> actions.onDrag(-delta) },
            onDragStopped = { velocity -> actions.onDragStopped(-velocity) },
        )
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = spacing.large),
        ) {
            val width = maxWidth
            val height = maxHeight
            val landscape = width > height
            if (landscape) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.large)) {
                    Column(modifier = Modifier.weight(1f).then(dragModifier)) {
                        Header(onCollapse = actions.onCollapse, onAddToPlaylist = actions.onAddToPlaylist)
                        Cover(url = ui.artworkUrl, size = min(height - 72.dp, width / 2))
                    }
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        Spacer(modifier = Modifier.height(spacing.medium))
                        Controls(ui, context, actions)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Column(modifier = dragModifier, horizontalAlignment = Alignment.CenterHorizontally) {
                        Header(onCollapse = actions.onCollapse, onAddToPlaylist = actions.onAddToPlaylist)
                        Spacer(modifier = Modifier.height(spacing.small))
                        // At most 42 % of the height, so title and transport fit below on a small phone.
                        Cover(url = ui.artworkUrl, size = min(width, height * COVER_HEIGHT_SHARE))
                    }
                    Spacer(modifier = Modifier.height(spacing.large))
                    Controls(ui, context, actions)
                    Spacer(modifier = Modifier.height(spacing.medium))
                }
            }
        }
    }
}

private const val COVER_HEIGHT_SHARE = 0.42f

@Composable
private fun Header(
    onCollapse: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCollapse) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.now_playing_collapse))
        }
        Text(
            text = stringResource(R.string.now_playing_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { heading() },
            maxLines = 1,
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.now_playing_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.queue_add_to_playlist)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onAddToPlaylist()
                    },
                )
            }
        }
    }
}

@Composable
private fun Cover(
    url: String?,
    size: Dp,
) {
    val description = stringResource(R.string.artwork_description)
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ArtworkFrame(modifier = Modifier.size(size)) {
            CoverImage(
                url = PlayerFormat.largeArtwork(url),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium)
                        .semantics { contentDescription = description },
                placeholderIconSize = 64.dp,
            )
        }
    }
}

@Composable
private fun Controls(
    ui: NowPlayingUi,
    context: NowPlayingContext,
    actions: NowPlayingActions,
) {
    val spacing = MaterialTheme.spacing
    val style = SonicStyle
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = ui.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = style.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = ui.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = style.mutedText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (ui.isPreview) {
            Spacer(modifier = Modifier.height(spacing.extraSmall))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PreviewTag()
                Spacer(modifier = Modifier.width(spacing.small))
                Text(
                    text =
                        stringResource(
                            if (context.signedIn) R.string.preview_hint_account else R.string.preview_hint_sign_in,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = style.mutedText,
                )
            }
        }
        ui.notice?.let { notice -> NoticeRow(notice, showRetry = ui.showRetry, onRetry = actions.onTogglePlayback) }
        Spacer(modifier = Modifier.height(spacing.small))
        SeekBar(
            positionMs = ui.positionMs,
            durationMs = ui.durationMs,
            enabled = ui.seekEnabled,
            onSeek = actions.onSeek,
        )
        Transport(ui, actions)
        Spacer(modifier = Modifier.height(spacing.medium))
        OutputLine(context.route)
        Spacer(modifier = Modifier.height(spacing.small))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            Box(modifier = Modifier.weight(1f)) {
                PlayerSoundChip(
                    soundName = context.soundName,
                    curve = context.soundCurve,
                    contextName = context.contextName,
                    onClick = actions.onOpenSound,
                )
                // The lamp in the chip's corner: whether the EQ really processes the signal.
                EqStatusLamp(kind = context.eqStatus, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            val queueLabel = pluralStringResource(R.plurals.queue_open, context.queueCount, context.queueCount)
            TextButton(
                onClick = actions.onOpenQueue,
                modifier = Modifier.semantics { contentDescription = queueLabel },
            ) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                Spacer(modifier = Modifier.width(spacing.extraSmall))
                Text(text = context.queueCount.toString())
            }
        }
    }
}

@Composable
private fun Transport(
    ui: NowPlayingUi,
    actions: NowPlayingActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ShuffleButton(enabled = ui.controlsEnabled, on = ui.shuffle, onToggle = actions.onToggleShuffle)
        IconButton(onClick = actions.onPrevious, enabled = ui.controlsEnabled, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.player_previous), modifier = Modifier.size(32.dp))
        }
        PlayKey(onClick = actions.onTogglePlayback, enabled = ui.controlsEnabled) {
            if (ui.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), color = SonicStyle.background)
            } else {
                Icon(
                    imageVector = if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (ui.isPlaying) R.string.player_pause else R.string.player_play),
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        IconButton(onClick = actions.onNext, enabled = ui.controlsEnabled, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.player_next), modifier = Modifier.size(32.dp))
        }
        RepeatButton(enabled = ui.controlsEnabled, setting = ui.repeat, onCycle = actions.onCycleRepeat)
    }
}

// The player's message with a symbol (not only color); "Try again" when playback stopped and
// waits for the user.
@Composable
private fun NoticeRow(
    notice: PlayerNotice,
    showRetry: Boolean,
    onRetry: () -> Unit,
) {
    val color = if (notice.isProblem()) MaterialTheme.colorScheme.error else SonicStyle.mutedText
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (notice == PlayerNotice.WaitingForNetwork) Icons.Default.CloudOff else Icons.Default.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = noticeText(notice),
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.weight(1f),
        )
        if (showRetry) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.now_playing_try_again)) }
        }
    }
}

// Where the sound goes, as text with a symbol per route type. Display only.
@Composable
private fun OutputLine(route: AudioRoute) {
    val muted = SonicStyle.mutedText
    Row(modifier = Modifier.widthIn(max = 480.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(route.type.icon(), contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.now_playing_output, route.name),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun AudioDeviceType.icon(): ImageVector =
    when (this) {
        AudioDeviceType.SPEAKER -> Icons.Default.Smartphone
        AudioDeviceType.WIRED_HEADPHONES -> Icons.Default.Headphones
        AudioDeviceType.BLUETOOTH -> Icons.Default.Bluetooth
        AudioDeviceType.CAR -> Icons.Default.DirectionsCar
        AudioDeviceType.BLUETOOTH_SPEAKER -> Icons.Default.Speaker
        AudioDeviceType.USB -> Icons.Default.Usb
        AudioDeviceType.UNKNOWN -> Icons.Default.Speaker
    }
