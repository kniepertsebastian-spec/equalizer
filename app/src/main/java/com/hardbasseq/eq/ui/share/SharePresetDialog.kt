package com.hardbasseq.eq.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.data.preset.PresetShareCodec

/** Shows the share link of a preset as a QR code, with buttons to send or copy the link. */
@Composable
fun SharePresetDialog(
    presetName: String,
    link: String,
    onShareLink: () -> Unit,
    onCopyLink: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fitsQr = link.length <= PresetShareCodec.QR_FRIENDLY_MAX_CHARS
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_preset_title, presetName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (fitsQr) {
                    QrCode(text = link, description = stringResource(R.string.share_preset_qr_description, presetName))
                    Text(
                        text = stringResource(R.string.share_preset_qr_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.share_preset_too_long_for_qr),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCopyLink) { Text(stringResource(R.string.action_copy)) }
                TextButton(onClick = onShareLink) { Text(stringResource(R.string.share_preset_send)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
