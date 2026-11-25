package com.auracurve.app.ui.player.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.auracurve.app.R
import com.auracurve.app.audio.AudioDeviceType
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.player.playback.PlayerNotice
import com.auracurve.app.playlist.PlayerFormat
import com.auracurve.app.preset.TargetPoint
import com.auracurve.app.ui.components.cyber.CyberCard
import com.auracurve.app.ui.components.cyber.CyberPlayKey
import com.auracurve.app.ui.components.cyber.CyberRoundButton
import com.auracurve.app.ui.components.cyber.RadialVisualizer
import com.auracurve.app.ui.components.cyber.WaveformSeekbar
import com.auracurve.app.ui.components.cyber.WaveformShape
import com.auracurve.app.ui.eq.EqStatusKind
import com.auracurve.app.ui.eq.EqStatusLamp
import com.auracurve.app.ui.equalizer.LinkButton
import com.auracurve.app.ui.player.CoverImage
import com.auracurve.app.ui.player.PlayerSoundChip
import com.auracurve.app.ui.player.ProgressRingCover
import com.auracurve.app.ui.player.RepeatButton
import com.auracurve.app.ui.player.ShuffleButton
import com.auracurve.app.ui.player.common.PreviewTag
import com.auracurve.app.ui.player.isProblem
import com.auracurve.app.ui.player.noticeText
import com.auracurve.app.ui.theme.CyberColors
import com.auracurve.app.ui.theme.cyberBackdrop
import com.auracurve.app.ui.theme.spacing

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
    // The track's seed for the crown and the waveform: the same track always looks the same.
    val seed = remember(ui.title, ui.artist) { WaveformShape.seedOf(ui.title + "\u0000" + ui.artist) }
    Surface(modifier = modifier.fillMaxSize(), color = CyberColors.void, contentColor = CyberColors.text) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .cyberBackdrop(strongTop = true)
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
                        Cover(ui = ui, seed = seed, size = min(height - 72.dp, width / 2))
                    }
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        Spacer(modifier = Modifier.height(spacing.medium))
                        Controls(ui, context, actions, seed)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Column(modifier = dragModifier, horizontalAlignment = Alignment.CenterHorizontally) {
                        Header(onCollapse = actions.onCollapse, onAddToPlaylist = actions.onAddToPlaylist)
                        Spacer(modifier = Modifier.height(spacing.small))
                        // At most 42 % of the height, so title and transport fit below on a small phone.
                        Cover(ui = ui, seed = seed, size = min(width, height * COVER_HEIGHT_SHARE))
                    }
                    Spacer(modifier = Modifier.height(spacing.large))
                    Controls(ui, context, actions, seed)
                    Spacer(modifier = Modifier.height(spacing.medium))
                }
            }
        }
    }
}

private const val COVER_HEIGHT_SHARE = 0.42f

// The cover with its progress ring takes this share of the crown; the crown's own neon ring sits
// just outside it.
private const val COVER_SHARE = 0.62f
private const val CROWN_INNER = 0.66f

@Composable
private fun Header(
    onCollapse: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CyberRoundButton(onClick = onCollapse, size = 44.dp) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.now_playing_collapse))
        }
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            TechLine(mirrored = false, modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.now_playing_title).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = CyberColors.cyan,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() },
                maxLines = 1,
            )
            TechLine(mirrored = true, modifier = Modifier.weight(1f))
        }
        Box {
            CyberRoundButton(onClick = { menuOpen = true }, size = 44.dp) {
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

// The thin circuit lines either side of the title, brighter toward it. Decoration only.
@Composable
private fun TechLine(
    mirrored: Boolean,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier =
            modifier.height(24.dp).drawWithCache {
                val middle = size.height / 2f
                val step = 5.dp.toPx()
                val knee = size.width * 0.55f

                // Drawn for the left side; the right one is its mirror image.
                fun x(value: Float) = if (mirrored) size.width - value else value
                val path =
                    Path().apply {
                        moveTo(x(0f), middle - step)
                        lineTo(x(knee), middle - step)
                        lineTo(x(knee + step), middle)
                        lineTo(x(size.width), middle)
                    }
                val faded = CyberColors.cyan.copy(alpha = 0.1f)
                val bright = CyberColors.cyan.copy(alpha = 0.7f)
                val brush = Brush.horizontalGradient(if (mirrored) listOf(bright, faded) else listOf(faded, bright))
                val stroke = Stroke(width = 1.dp.toPx())
                val dot = Offset(x(knee), middle - step)
                onDrawBehind {
                    drawPath(path, brush, style = stroke)
                    drawCircle(bright, 1.5.dp.toPx(), dot)
                }
            },
    )
}

@Composable
private fun Cover(
    ui: NowPlayingUi,
    seed: Long,
    size: Dp,
) {
    val description = stringResource(R.string.artwork_description)
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            // The crown of light: decoration from the track's seed, not a measurement of the audio.
            RadialVisualizer(
                playing = ui.isPlaying,
                seed = seed,
                loading = ui.isLoading,
                modifier = Modifier.fillMaxSize(),
                innerRadiusFraction = CROWN_INNER,
            )
            // Round cover; the ring around it is the playback position (display only).
            ProgressRingCover(progress = ui.progress, modifier = Modifier.size(size * COVER_SHARE)) {
                CoverImage(
                    url = PlayerFormat.largeArtwork(ui.artworkUrl),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .semantics { contentDescription = description },
                    placeholderIconSize = 56.dp,
                )
            }
        }
    }
}

@Composable
private fun Controls(
    ui: NowPlayingUi,
    context: NowPlayingContext,
    actions: NowPlayingActions,
    seed: Long,
) {
    val spacing = MaterialTheme.spacing
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ui.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = CyberColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = ui.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyberColors.cyanText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(spacing.small))
            CyberRoundButton(onClick = actions.onAddToPlaylist, size = 44.dp) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.queue_add_to_playlist))
            }
        }
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
                    color = CyberColors.mutedText,
                )
            }
        }
        ui.notice?.let { notice -> NoticeRow(notice, showRetry = ui.showRetry, onRetry = actions.onTogglePlayback) }
        Spacer(modifier = Modifier.height(spacing.small))
        WaveformSeekbar(
            positionMs = ui.positionMs,
            durationMs = ui.durationMs,
            enabled = ui.seekEnabled,
            onSeek = actions.onSeek,
            seed = seed,
            playing = ui.isPlaying,
        )
        Transport(ui, actions)
        Spacer(modifier = Modifier.height(spacing.medium))
        OutputLine(context.route)
        Spacer(modifier = Modifier.height(spacing.small))
        SoundCard(context, actions)
    }
}

// The applied sound in a HUD frame: its curve, name and context with the EQ lamp, and apart from
// it the queue with its length. Two targets, as before: the sound opens the equalizer, the count
// the queue.
@Composable
private fun SoundCard(
    context: NowPlayingContext,
    actions: NowPlayingActions,
) {
    val queueLabel = pluralStringResource(R.plurals.queue_open, context.queueCount, context.queueCount)
    CyberCard(hud = true, shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                PlayerSoundChip(
                    soundName = context.soundName,
                    curve = context.soundCurve,
                    contextName = context.contextName,
                    onClick = actions.onOpenSound,
                )
                // The lamp in the chip's corner: whether the EQ really processes the signal.
                EqStatusLamp(kind = context.eqStatus, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
            }
            Spacer(modifier = Modifier.width(1.dp).height(32.dp).background(CyberColors.outline))
            Row(
                modifier =
                    Modifier
                        .heightIn(min = 56.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(role = Role.Button, onClick = actions.onOpenQueue)
                        .semantics { contentDescription = queueLabel }
                        .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = CyberColors.cyanText)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = context.queueCount.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = CyberColors.cyanText,
                )
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
        CyberRoundButton(onClick = actions.onPrevious, enabled = ui.controlsEnabled) {
            Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.player_previous), modifier = Modifier.size(28.dp))
        }
        CyberPlayKey(playing = ui.isPlaying, enabled = ui.controlsEnabled, onClick = actions.onTogglePlayback) {
            if (ui.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Color.White)
            } else {
                Icon(
                    imageVector = if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (ui.isPlaying) R.string.player_pause else R.string.player_play),
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        CyberRoundButton(onClick = actions.onNext, enabled = ui.controlsEnabled) {
            Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.player_next), modifier = Modifier.size(28.dp))
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
    val color = if (notice.isProblem()) MaterialTheme.colorScheme.error else CyberColors.mutedText
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
            LinkButton(text = stringResource(R.string.now_playing_try_again), onClick = onRetry)
        }
    }
}

// Where the sound goes, as text with a symbol per route type. Display only.
@Composable
private fun OutputLine(route: AudioRoute) {
    val muted = CyberColors.mutedText
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
        AudioDeviceType.SOUNDBAR -> Icons.Default.Speaker
        AudioDeviceType.USB -> Icons.Default.Usb
        AudioDeviceType.UNKNOWN -> Icons.Default.Speaker
    }
