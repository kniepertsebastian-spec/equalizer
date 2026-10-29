package com.hardbasseq.eq.ui.equalizer

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.AudioSession

// Human-readable name of the app behind a session; falls back to the package name.
fun appLabelFor(
    context: Context,
    packageName: String,
): String? =
    try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: Exception) {
        packageName.takeIf { it.isNotBlank() && it != "unknown" }
    }

private const val SPOTIFY_PACKAGE = "com.spotify.music"

// Every app currently holding an audio session. The user picks which one the EQ is bound to,
// instead of the first one that happened to open (e.g. a forgotten YouTube Music).
@Composable
fun AudioSourceList(
    sessions: List<AudioSession>,
    activeSession: AudioSession?,
    onSelect: (AudioSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) return
    val context = LocalContext.current
    val unknown = stringResource(R.string.audio_sources_unknown_app)
    var spotifyHelpOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(stringResource(R.string.audio_sources_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.audio_sources_hint), style = MaterialTheme.typography.bodySmall)
        sessions.forEach { session ->
            val selected = session.sessionId == activeSession?.sessionId
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(session) }
                        .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = selected, onClick = { onSelect(session) })
                Column(modifier = Modifier.weight(1f)) {
                    Text(appLabelFor(context, session.packageName) ?: unknown, style = MaterialTheme.typography.bodyMedium)
                    if (selected) {
                        Text(stringResource(R.string.audio_sources_selected), style = MaterialTheme.typography.bodySmall)
                    }
                    if (session.packageName == SPOTIFY_PACKAGE) {
                        Text(
                            stringResource(R.string.spotify_eq_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = SonicStyle.accent,
                        )
                    }
                }
                // HardBass EQ cannot reach into Spotify's own settings, so the most it can do
                // is say clearly that the two equalizers stack, and where to switch Spotify's off.
                if (session.packageName == SPOTIFY_PACKAGE) {
                    IconButton(onClick = { spotifyHelpOpen = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = stringResource(R.string.spotify_eq_help_open),
                            tint = SonicStyle.accent,
                        )
                    }
                }
            }
        }
    }

    if (spotifyHelpOpen) {
        AlertDialog(
            onDismissRequest = { spotifyHelpOpen = false },
            title = { Text(stringResource(R.string.spotify_eq_help_title)) },
            text = { Text(stringResource(R.string.spotify_eq_help_text)) },
            confirmButton = {
                TextButton(onClick = { spotifyHelpOpen = false }) {
                    Text(stringResource(R.string.spotify_eq_help_done))
                }
            },
        )
    }
}
