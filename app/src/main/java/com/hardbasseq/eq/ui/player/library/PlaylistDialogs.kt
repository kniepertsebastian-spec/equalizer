package com.hardbasseq.eq.ui.player.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.playlist.SavedPlaylist
import com.soundcloud.equalizer.player.model.TrackItem

// Opens the playlist dialog: with a track it adds that track somewhere, without one it
// only makes a new playlist.
internal data class PlaylistDialogRequest(
    val track: TrackItem?,
)

// Pick two or more playlists and a name: one new playlist with all their tracks, each once.
@Composable
internal fun MergePlaylistsDialog(
    playlists: List<SavedPlaylist>,
    onMerge: (List<SavedPlaylist>, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var chosenIds by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.merge_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.merge_name_hint)) },
                    singleLine = true,
                )
                playlists.forEach { playlist ->
                    val checked = playlist.id in chosenIds
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { chosenIds = if (checked) chosenIds - playlist.id else chosenIds + playlist.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(
                            text = "${playlist.title} (${playlist.tracks.size})",
                            modifier = Modifier.padding(start = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onMerge(playlists.filter { it.id in chosenIds }, name) },
                enabled = chosenIds.size >= 2 && name.isNotBlank(),
            ) { Text(stringResource(R.string.playlists_merge)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun PlaylistDialog(
    track: TrackItem?,
    playlists: List<SavedPlaylist>,
    onAddTo: (SavedPlaylist) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    // Only playlists made on the device take tracks: imported ones mirror their link.
    val local = playlists.filter { it.sourceUrl == null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (track !=
                    null
                ) {
                    stringResource(R.string.playlist_dialog_add_title)
                } else {
                    stringResource(R.string.playlists_new_button)
                },
            )
        },
        text = {
            Column {
                if (track != null) {
                    Text(text = track.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    local.forEach { playlist ->
                        TextButton(onClick = { onAddTo(playlist) }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${playlist.title} (${playlist.tracks.size})",
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    HorizontalDivider()
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.merge_name_hint)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text(if (track != null) stringResource(R.string.playlist_create_and_add) else stringResource(R.string.action_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
