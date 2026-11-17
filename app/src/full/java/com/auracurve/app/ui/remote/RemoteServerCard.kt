package com.auracurve.app.ui.remote

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auracurve.app.R
import com.auracurve.app.remote.LocalAddress
import com.auracurve.app.remote.RemoteServerUiState
import com.auracurve.app.remote.RemoteServerViewModel
import com.auracurve.app.ui.theme.SonicRule
import com.auracurve.app.ui.theme.SonicSteel
import com.auracurve.app.ui.theme.spacing

// Flavor "full" (Pixel-Car module 04a): the switch for the local remote-control server, with the
// address, the token and the pairing link the web remote understands. Off by default.
@Composable
fun RemoteServerCard() {
    val viewModel: RemoteServerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 04c (U-1): Spotify is steered through its MediaSession, which needs notification access.
    var spotifyAccess by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        spotifyAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    RemoteServerCardContent(
        state = state,
        spotifyAccess = spotifyAccess,
        onEnabledChange = viewModel::setEnabled,
        onRegenerateToken = viewModel::regenerateToken,
        onOpenNotificationAccess = {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
    )
}

@Composable
internal fun RemoteServerCardContent(
    state: RemoteServerUiState,
    spotifyAccess: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onRegenerateToken: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val clipboard = LocalClipboardManager.current
    val switchLabel = stringResource(R.string.remote_server_title)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, SonicRule),
    ) {
        Column(modifier = Modifier.padding(spacing.medium), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(switchLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(if (state.running) R.string.remote_server_status_on else R.string.remote_server_status_off),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.running) MaterialTheme.colorScheme.primary else SonicSteel,
                    )
                }
                Switch(
                    checked = state.running,
                    onCheckedChange = onEnabledChange,
                    enabled = state.available,
                    modifier = Modifier.testTag("remote_server_switch").semantics { contentDescription = switchLabel },
                )
            }
            Text(
                stringResource(if (state.available) R.string.remote_server_description else R.string.remote_server_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = SonicSteel,
            )
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (state.running) {
                val address = state.address
                Text(
                    if (address != null) {
                        stringResource(R.string.remote_server_address, "$address:${LocalAddress.PORT}")
                    } else {
                        stringResource(R.string.remote_server_notification_no_network)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.token?.let { token ->
                    Text(
                        stringResource(R.string.remote_server_token_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = SonicSteel,
                    )
                    Text(token, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                state.pairingLink?.let { link ->
                    Text(
                        stringResource(R.string.remote_server_link_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = SonicSteel,
                    )
                    Text(link, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                Text(
                    stringResource(
                        if (spotifyAccess) R.string.remote_server_spotify_access_granted else R.string.remote_server_spotify_access,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicSteel,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (!spotifyAccess) {
                        TextButton(onClick = onOpenNotificationAccess) { Text(stringResource(R.string.remote_server_open_access)) }
                    }
                    TextButton(onClick = onRegenerateToken) { Text(stringResource(R.string.remote_server_new_token)) }
                    state.pairingLink?.let { link ->
                        TextButton(onClick = { clipboard.setText(AnnotatedString(link)) }) {
                            Text(stringResource(R.string.remote_server_copy_link))
                        }
                    }
                }
            }
        }
    }
}
