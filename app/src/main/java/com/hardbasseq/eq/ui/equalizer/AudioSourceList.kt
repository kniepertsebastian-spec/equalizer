package com.hardbasseq.eq.ui.equalizer

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                Column {
                    Text(appLabelFor(context, session.packageName) ?: unknown, style = MaterialTheme.typography.bodyMedium)
                    if (selected) {
                        Text(stringResource(R.string.audio_sources_selected), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
