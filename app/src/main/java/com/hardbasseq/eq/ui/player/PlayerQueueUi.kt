package com.hardbasseq.eq.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hardbasseq.eq.R
import com.hardbasseq.eq.ui.theme.SonicAmber
import com.hardbasseq.eq.ui.theme.SonicSteel
import com.soundcloud.equalizer.player.playback.PlayerNotice
import com.soundcloud.equalizer.player.playback.RepeatSetting

// A cover from SoundCloud, loaded and cached by Coil (memory and disk, see
// HardBassEqApplication.newImageLoader), so scrolling a list or reopening the player does not
// download it again. Until it is there (or when there is none) a note stands in for it.
@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    placeholderIconSize: Dp = 20.dp,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.MusicNote, contentDescription = null, tint = SonicSteel, modifier = Modifier.size(placeholderIconSize))
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// The small cover at the start of a list row.
@Composable
fun CoverThumb(url: String?) {
    CoverImage(url = url, modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small))
}

// "⋮" on a track outside the queue (search result, playlist entry): put it into the queue. A
// playlist entry also offers playing the playlist from there and, for playlists made on the
// device, removing it.
@Composable
fun TrackQueueMenu(
    trackTitle: String,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayFromHere: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.track_more_actions, trackTitle))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            onPlayFromHere?.let { play ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_play_from_here)) },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    onClick = {
                        open = false
                        play()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.queue_play_next)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                onClick = {
                    open = false
                    onPlayNext()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.queue_add)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                onClick = {
                    open = false
                    onAddToQueue()
                },
            )
            onRemoveFromPlaylist?.let { remove ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_remove_track)) },
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    onClick = {
                        open = false
                        remove()
                    },
                )
            }
        }
    }
}

// "⋮" on a queue entry: reorder, remove, or keep it in a playlist.
@Composable
fun QueueEntryMenu(
    trackTitle: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.track_more_actions, trackTitle))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.queue_move_up)) },
                leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                enabled = canMoveUp,
                onClick = {
                    open = false
                    onMoveUp()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.queue_move_down)) },
                leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                enabled = canMoveDown,
                onClick = {
                    open = false
                    onMoveDown()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.queue_remove)) },
                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                onClick = {
                    open = false
                    onRemove()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.queue_add_to_playlist)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                onClick = {
                    open = false
                    onAddToPlaylist()
                },
            )
        }
    }
}

// Shuffle on/off. On is amber (the selected-control color); the state is also spoken.
@Composable
fun ShuffleButton(
    enabled: Boolean,
    on: Boolean,
    onToggle: () -> Unit,
) {
    val label = stringResource(R.string.player_shuffle)
    val state = stringResource(if (on) R.string.state_on else R.string.state_off)
    IconButton(
        onClick = onToggle,
        enabled = enabled,
        modifier =
            Modifier.semantics {
                contentDescription = label
                stateDescription = state
            },
    ) {
        Icon(Icons.Default.Shuffle, contentDescription = null, tint = if (on) SonicAmber else SonicSteel)
    }
}

// Repeat: off -> whole queue -> this track -> off. "This track" also changes the symbol, not
// only the color.
@Composable
fun RepeatButton(
    enabled: Boolean,
    setting: RepeatSetting,
    onCycle: () -> Unit,
) {
    val label = stringResource(R.string.player_repeat)
    val state =
        stringResource(
            when (setting) {
                RepeatSetting.OFF -> R.string.player_repeat_off
                RepeatSetting.ALL -> R.string.player_repeat_all
                RepeatSetting.ONE -> R.string.player_repeat_one
            },
        )
    IconButton(
        onClick = onCycle,
        enabled = enabled,
        modifier =
            Modifier.semantics {
                contentDescription = label
                stateDescription = state
            },
    ) {
        Icon(
            imageVector = if (setting == RepeatSetting.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
            contentDescription = null,
            tint = if (setting == RepeatSetting.OFF) SonicSteel else SonicAmber,
        )
    }
}

// What the player has to say right now (it used to be a toast from the service).
@Composable
fun noticeText(notice: PlayerNotice): String =
    when (notice) {
        PlayerNotice.WaitingForNetwork -> stringResource(R.string.notice_waiting_for_network)
        is PlayerNotice.NotPlayable -> stringResource(R.string.notice_not_playable, notice.title)
        PlayerNotice.NetworkGaveUp -> stringResource(R.string.notice_network_gave_up)
        PlayerNotice.TooManyUnplayable -> stringResource(R.string.notice_too_many_unplayable)
    }

// Waiting for the network is a passing state; the others need the user.
fun PlayerNotice.isProblem(): Boolean = this != PlayerNotice.WaitingForNetwork
