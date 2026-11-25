package com.auracurve.app.ui.remote

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auracurve.app.R
import com.auracurve.app.remote.DuckDns
import com.auracurve.app.remote.LocalAddress
import com.auracurve.app.remote.MdnsName
import com.auracurve.app.remote.RemoteServerUiState
import com.auracurve.app.remote.RemoteServerViewModel
import com.auracurve.app.ui.components.cyber.CyberCard
import com.auracurve.app.ui.share.QrCode
import com.auracurve.app.ui.theme.SonicSteel
import com.auracurve.app.ui.theme.spacing
import java.text.DateFormat
import java.util.Date

// Flavor "full" (Pixel-Car module 04a): the switch for the local remote-control server, with the
// address and, since module 05, the paired devices and pairing by one-time code. Off by default.
@Composable
fun RemoteServerCard() {
    val viewModel: RemoteServerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 04c (U-1): Spotify is steered through its MediaSession, which needs notification access.
    var spotifyAccess by remember { mutableStateOf(false) }
    // The library opens songs in Spotify from the background, which Android only allows with "Display over other apps".
    var mayOpenSpotify by remember { mutableStateOf(true) }
    val locationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshWifiPermission() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshWifiPermission()
        spotifyAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        mayOpenSpotify = Settings.canDrawOverlays(context)
    }
    RemoteServerCardContent(
        state = state,
        spotifyAccess = spotifyAccess,
        mayOpenSpotify = mayOpenSpotify,
        onEnabledChange = viewModel::setEnabled,
        onStartPairing = viewModel::startPairing,
        onCancelPairing = viewModel::cancelPairing,
        onRemoveDevice = viewModel::removeDevice,
        onRemoveAllDevices = viewModel::removeAllDevices,
        onAllowWifiChange = viewModel::setAllowWifi,
        onStartAtBootChange = viewModel::setStartAtBoot,
        onHostNameChange = viewModel::setHostName,
        onAddTrustedWifi = viewModel::addTrustedWifi,
        onRemoveTrustedWifi = viewModel::removeTrustedWifi,
        onSaveDynDns = viewModel::setDynDns,
        onClearDynDns = viewModel::clearDynDns,
        onAllowWifiNames = { locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
        onOpenNotificationAccess = {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        onAllowOpenSpotify = {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
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
    onStartPairing: () -> Unit,
    onCancelPairing: () -> Unit,
    onRemoveDevice: (String) -> Unit,
    onRemoveAllDevices: () -> Unit,
    onAllowWifiChange: (Boolean) -> Unit,
    onStartAtBootChange: (Boolean) -> Unit,
    onHostNameChange: (String) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    mayOpenSpotify: Boolean = true,
    onAllowOpenSpotify: () -> Unit = {},
    onAddTrustedWifi: (String) -> Unit = {},
    onRemoveTrustedWifi: (String) -> Unit = {},
    onAllowWifiNames: () -> Unit = {},
    onSaveDynDns: (name: String, token: String, onDone: (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onClearDynDns: () -> Unit = {},
) {
    val spacing = MaterialTheme.spacing
    var editingName by remember { mutableStateOf(false) }
    var confirmRemoveAll by remember { mutableStateOf(false) }
    var showAddress by remember { mutableStateOf(false) }
    var editingDynDns by remember { mutableStateOf(false) }
    if (editingDynDns) {
        DynDnsDialog(
            current = state.dynDnsHost?.removeSuffix(DuckDns.SUFFIX).orEmpty(),
            hasToken = state.dynDnsHost != null,
            onSave = onSaveDynDns,
            onDone = { editingDynDns = false },
        )
    }
    // 19 Nov 2026: the address alone for phones already paired; it carries no code and no token.
    if (showAddress && state.running && state.address != null) AddressDialog(state = state, onDismiss = { showAddress = false })
    // Module 05: the code is shown while it is valid; used, expired or the server off closes it.
    if (state.running && state.pairingLink != null) PairingDialog(state = state, onDismiss = onCancelPairing)
    if (confirmRemoveAll) {
        AlertDialog(
            onDismissRequest = { confirmRemoveAll = false },
            title = { Text(stringResource(R.string.remote_server_remove_all_title)) },
            text = { Text(stringResource(R.string.remote_server_remove_all_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoveAll = false
                    onRemoveAllDevices()
                }) { Text(stringResource(R.string.remote_server_remove_all)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveAll = false }) { Text(stringResource(R.string.remote_server_name_cancel)) }
            },
        )
    }
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
    CyberCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
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
                DynDnsSection(state, onEdit = { editingDynDns = true }, onClear = onClearDynDns)
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
                val wifiLabel = stringResource(R.string.remote_server_allow_wifi)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(wifiLabel, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.remote_server_allow_wifi_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = SonicSteel,
                        )
                    }
                    Switch(
                        checked = state.allowWifi,
                        onCheckedChange = onAllowWifiChange,
                        modifier = Modifier.testTag("remote_server_wifi_switch").semantics { contentDescription = wifiLabel },
                    )
                }
                TrustedWifiSection(state, onAddTrustedWifi, onRemoveTrustedWifi, onAllowWifiNames)
                Text(
                    stringResource(R.string.remote_server_devices_label, state.devices.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = SonicSteel,
                )
                if (state.devices.isEmpty()) {
                    Text(stringResource(R.string.remote_server_no_devices), style = MaterialTheme.typography.bodySmall, color = SonicSteel)
                }
                state.devices.forEach { device ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("remote_server_device_${device.id}")) {
                        Text(device.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRemoveDevice(device.id) }) { Text(stringResource(R.string.remote_server_remove_device)) }
                    }
                }
                Text(
                    stringResource(
                        if (spotifyAccess) R.string.remote_server_spotify_access_granted else R.string.remote_server_spotify_access,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicSteel,
                )
                Text(
                    stringResource(
                        if (mayOpenSpotify) R.string.remote_server_open_spotify_granted else R.string.remote_server_open_spotify,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicSteel,
                )
                // Up to five buttons: they wrap on a narrow phone instead of squeezing each other.
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (!spotifyAccess) {
                        TextButton(onClick = onOpenNotificationAccess) { Text(stringResource(R.string.remote_server_open_access)) }
                    }
                    if (!mayOpenSpotify) {
                        TextButton(onClick = onAllowOpenSpotify, modifier = Modifier.testTag("remote_server_allow_open_spotify")) {
                            Text(stringResource(R.string.remote_server_allow_open_spotify))
                        }
                    }
                    if (state.devices.isNotEmpty()) {
                        TextButton(onClick = { confirmRemoveAll = true }) { Text(stringResource(R.string.remote_server_remove_all)) }
                    }
                    if (state.address != null) {
                        TextButton(onClick = { showAddress = true }, modifier = Modifier.testTag("remote_server_address_qr")) {
                            Text(stringResource(R.string.remote_server_address_qr))
                        }
                    }
                    if (state.address != null) {
                        TextButton(onClick = onStartPairing, modifier = Modifier.testTag("remote_server_pair")) {
                            Text(stringResource(R.string.remote_server_pair))
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
 * Module 05: the one-time code, as text to type and as a QR code of the pairing link, so the other
 * phone only has to scan it. Valid five minutes and once; the window is secure anyway (FLAG_SECURE:
 * no screenshot, blank in recent apps). It closes by itself when the code is used or expires.
 */
@Composable
private fun PairingDialog(
    state: RemoteServerUiState,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var useIp by rememberSaveable { mutableStateOf(false) }
    val code = state.pairing ?: return
    val link = (if (useIp) state.ipPairingLink else state.pairingLink) ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        modifier = Modifier.testTag("remote_server_qr_dialog"),
        title = { Text(stringResource(R.string.remote_server_qr_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Text(
                    code.display,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("remote_server_pair_code"),
                )
                Text(stringResource(R.string.remote_server_pair_hint), style = MaterialTheme.typography.bodySmall, color = SonicSteel)
                QrCode(text = link, description = stringResource(R.string.remote_server_qr_description))
                Text(link, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(link)) },
                ) { Text(stringResource(R.string.remote_server_copy_link)) }
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

/**
 * 19 Nov 2026: the web remote's address as a QR code, for phones that are already paired. No code,
 * no token: the phone keeps the key it stored when it was paired, so nothing new is paired and the
 * window needs no FLAG_SECURE. The browser keeps that key per address, so the name (stable across
 * hotspots) is the default; the IP works only for a phone paired by this same IP.
 */
@Composable
private fun AddressDialog(
    state: RemoteServerUiState,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var useIp by rememberSaveable { mutableStateOf(false) }
    val link = (if (useIp) state.ipUrl else state.nameUrl) ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("remote_server_address_dialog"),
        title = { Text(stringResource(R.string.remote_server_address_qr_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Text(stringResource(R.string.remote_server_address_qr_hint), style = MaterialTheme.typography.bodySmall, color = SonicSteel)
                QrCode(text = link, description = stringResource(R.string.remote_server_address_qr_description))
                Text(
                    link,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("remote_server_address_link"),
                )
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(link)) },
                ) { Text(stringResource(R.string.remote_server_copy_link)) }
                val ipLabel = stringResource(R.string.remote_server_qr_use_ip)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ipLabel, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.remote_server_address_qr_use_ip_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = SonicSteel,
                        )
                    }
                    Switch(
                        checked = useIp,
                        onCheckedChange = { useIp = it },
                        modifier = Modifier.testTag("remote_server_address_use_ip").semantics { contentDescription = ipLabel },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.remote_server_qr_close)) } },
    )
}

/**
 * Fixed address via DuckDNS (19 Nov 2026): the name stays when the hotspot address changes, so the
 * browser keeps the pairing. The token is never shown again once saved.
 */
@Composable
private fun DynDnsSection(
    state: RemoteServerUiState,
    onEdit: () -> Unit,
    onClear: () -> Unit,
) {
    val host = state.dynDnsHost
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("remote_server_dyndns")) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (host != null) {
                    stringResource(R.string.remote_server_dyndns_label, host)
                } else {
                    stringResource(R.string.remote_server_dyndns_off)
                },
                style = MaterialTheme.typography.bodySmall,
                color = SonicSteel,
            )
            if (host != null) {
                val status = state.dynDnsStatus
                val ip = status.ip
                Text(
                    when {
                        ip != null && ip == state.address && !status.failed ->
                            stringResource(
                                R.string.remote_server_dyndns_ok,
                                ip,
                                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(status.atMs)),
                            )
                        status.failed -> stringResource(R.string.remote_server_dyndns_failed)
                        else -> stringResource(R.string.remote_server_dyndns_pending)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.failed) MaterialTheme.colorScheme.error else SonicSteel,
                    modifier = Modifier.testTag("remote_server_dyndns_status"),
                )
            } else {
                Text(stringResource(R.string.remote_server_dyndns_desc), style = MaterialTheme.typography.bodySmall, color = SonicSteel)
            }
        }
        if (host != null) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.remote_server_remove_device)) }
        }
        TextButton(onClick = onEdit, modifier = Modifier.testTag("remote_server_dyndns_edit")) {
            Text(stringResource(if (host != null) R.string.remote_server_change_name else R.string.remote_server_dyndns_set_up))
        }
    }
}

@Composable
private fun DynDnsDialog(
    current: String,
    hasToken: Boolean,
    onSave: (name: String, token: String, onDone: (Boolean) -> Unit) -> Unit,
    onDone: () -> Unit,
) {
    var name by remember { mutableStateOf(current) }
    var token by remember { mutableStateOf("") }
    var rejected by remember { mutableStateOf(false) }
    val validName = DuckDns.normalizeName(name)
    // Changing only the name keeps the stored token.
    val validToken = if (token.isBlank()) hasToken else DuckDns.normalizeToken(token) != null
    AlertDialog(
        onDismissRequest = onDone,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        modifier = Modifier.testTag("remote_server_dyndns_dialog"),
        title = { Text(stringResource(R.string.remote_server_dyndns_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Text(stringResource(R.string.remote_server_dyndns_help), style = MaterialTheme.typography.bodySmall, color = SonicSteel)
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(MAX_DYNDNS_INPUT)
                        rejected = false
                    },
                    singleLine = true,
                    isError = name.isNotEmpty() && validName == null,
                    label = { Text(stringResource(R.string.remote_server_dyndns_name)) },
                    suffix = { Text(DuckDns.SUFFIX) },
                    modifier = Modifier.testTag("remote_server_dyndns_name"),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it.take(MAX_DYNDNS_INPUT)
                        rejected = false
                    },
                    singleLine = true,
                    isError = token.isNotEmpty() && !validToken,
                    label = { Text(stringResource(R.string.remote_server_dyndns_token)) },
                    placeholder = { if (hasToken) Text(stringResource(R.string.remote_server_dyndns_token_kept)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.testTag("remote_server_dyndns_token"),
                )
                if (rejected) {
                    Text(
                        stringResource(R.string.remote_server_dyndns_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, token) { ok -> if (ok) onDone() else rejected = true } },
                enabled = validName != null && validToken,
            ) { Text(stringResource(R.string.remote_server_name_save)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.remote_server_name_cancel)) } },
    )
}

private const val MAX_DYNDNS_INPUT = 80

/**
 * Trusted Wi-Fi names (19 Nov 2026): in the car the Pixel joins the hotspot of the driver's phone
 * for internet; the remote control must reach it there like in its own hotspot.
 */
@Composable
private fun TrustedWifiSection(
    state: RemoteServerUiState,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAllowNames: () -> Unit,
) {
    Text(
        stringResource(R.string.remote_server_trusted_wifi_label, state.trustedWifi.size),
        style = MaterialTheme.typography.labelMedium,
        color = SonicSteel,
    )
    Text(stringResource(R.string.remote_server_trusted_wifi_desc), style = MaterialTheme.typography.bodySmall, color = SonicSteel)
    state.trustedWifi.forEach { name ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("remote_server_trusted_wifi_$name")) {
            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onRemove(name) }) { Text(stringResource(R.string.remote_server_remove_device)) }
        }
    }
    when {
        !state.wifiNamePermission ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.remote_server_trusted_wifi_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicSteel,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAllowNames) { Text(stringResource(R.string.remote_server_trusted_wifi_allow)) }
            }
        state.canAddCurrentWifi ->
            TextButton(
                onClick = { state.currentWifi?.let(onAdd) },
                modifier = Modifier.testTag("remote_server_add_current_wifi"),
            ) { Text(stringResource(R.string.remote_server_trusted_wifi_add, state.currentWifi.orEmpty())) }
        state.currentWifi == null ->
            Text(stringResource(R.string.remote_server_trusted_wifi_none), style = MaterialTheme.typography.bodySmall, color = SonicSteel)
    }
}
