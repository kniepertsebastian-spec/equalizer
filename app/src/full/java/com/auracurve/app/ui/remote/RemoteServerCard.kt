package com.auracurve.app.ui.remote

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auracurve.app.R
import com.auracurve.app.remote.LocalAddress
import com.auracurve.app.remote.MdnsName
import com.auracurve.app.remote.RemoteServerUiState
import com.auracurve.app.remote.RemoteServerViewModel
import com.auracurve.app.ui.share.QrCode
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
        onStartAtBootChange = viewModel::setStartAtBoot,
        onHostNameChange = viewModel::setHostName,
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
    onStartAtBootChange: (Boolean) -> Unit,
    onHostNameChange: (String) -> Unit,
    onOpenNotificationAccess: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val clipboard = LocalClipboardManager.current
    var editingName by remember { mutableStateOf(false) }
    // The QR code shows the token: only on request, closed as soon as the server is off.
    var showingQr by rememberSaveable { mutableStateOf(false) }
    if (showingQr && (!state.running || state.pairingLink == null)) showingQr = false
    if (showingQr) PairingQrDialog(state = state, onDismiss = { showingQr = false })
    if (editingName) {
        HostNameDialog(
            current = state.hostName,
            onSave = {
                onHostNameChange(it)
                editingName = false
            },
            onDismiss = { editingName = false },
        )
    }
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
                if (address != null) {
                    // 04e (R-19): the fixed name first; the hotspot address changes, the name does not.
                    Text(
                        stringResource(R.string.remote_server_address, state.nameUrl),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("remote_server_name_url"),
                    )
                    Text(
                        stringResource(R.string.remote_server_ip, "$address:${LocalAddress.PORT}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = SonicSteel,
                    )
                } else {
                    Text(stringResource(R.string.remote_server_notification_no_network), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.remote_server_name_label, MdnsName.host(state.hostName)),
                        style = MaterialTheme.typography.bodySmall,
                        color = SonicSteel,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { editingName = true }) { Text(stringResource(R.string.remote_server_change_name)) }
                }
                val bootLabel = stringResource(R.string.remote_server_start_at_boot)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(bootLabel, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.remote_server_start_at_boot_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = SonicSteel,
                        )
                    }
                    Switch(
                        checked = state.startAtBoot,
                        onCheckedChange = onStartAtBootChange,
                        modifier = Modifier.testTag("remote_server_boot_switch").semantics { contentDescription = bootLabel },
                    )
                }
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
                // Up to four buttons: they wrap on a narrow phone instead of squeezing each other.
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (!spotifyAccess) {
                        TextButton(onClick = onOpenNotificationAccess) { Text(stringResource(R.string.remote_server_open_access)) }
                    }
                    TextButton(onClick = onRegenerateToken) { Text(stringResource(R.string.remote_server_new_token)) }
                    state.pairingLink?.let { link ->
                        TextButton(onClick = { clipboard.setText(AnnotatedString(link)) }) {
                            Text(stringResource(R.string.remote_server_copy_link))
                        }
                    }
                    if (state.pairingLink != null) {
                        TextButton(onClick = { showingQr = true }, modifier = Modifier.testTag("remote_server_show_qr")) {
                            Text(stringResource(R.string.remote_server_show_qr))
                        }
                    }
                }
            }
        }
    }
}

// 04e: the name the phone answers to as <name>.local; only a-z, 0-9 and '-', 1 to 30 characters.
@Composable
private fun HostNameDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    val valid = MdnsName.normalize(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remote_server_name_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MdnsName.MAX_LENGTH + 10) },
                    singleLine = true,
                    isError = valid == null,
                    suffix = { Text(".local") },
                    modifier = Modifier.testTag("remote_server_name_field"),
                )
                Text(
                    stringResource(R.string.remote_server_name_rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid == null) MaterialTheme.colorScheme.error else SonicSteel,
                )
                Text(
                    stringResource(R.string.remote_server_name_relink),
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicSteel,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { valid?.let(onSave) }, enabled = valid != null) {
                Text(stringResource(R.string.remote_server_name_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.remote_server_name_cancel)) } },
    )
}

/**
 * The pairing link as a QR code, so the other phone only has to scan it. It contains the token,
 * so the dialog window is secure (FLAG_SECURE: no screenshot, blank in recent apps). It follows
 * the state: a new token shows the new link.
 */
@Composable
private fun PairingQrDialog(
    state: RemoteServerUiState,
    onDismiss: () -> Unit,
) {
    var useIp by rememberSaveable { mutableStateOf(false) }
    val link = (if (useIp) state.ipPairingLink else state.pairingLink) ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        modifier = Modifier.testTag("remote_server_qr_dialog"),
        title = { Text(stringResource(R.string.remote_server_qr_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                QrCode(text = link, description = stringResource(R.string.remote_server_qr_description))
                Text(link, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                val ipLabel = stringResource(R.string.remote_server_qr_use_ip)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ipLabel, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.remote_server_qr_use_ip_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = SonicSteel,
                        )
                    }
                    Switch(
                        checked = useIp,
                        onCheckedChange = { useIp = it },
                        modifier = Modifier.testTag("remote_server_qr_use_ip").semantics { contentDescription = ipLabel },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.remote_server_qr_close)) } },
    )
}
