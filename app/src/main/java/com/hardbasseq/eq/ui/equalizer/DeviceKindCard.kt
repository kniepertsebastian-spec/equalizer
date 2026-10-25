package com.hardbasseq.eq.ui.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.DeviceKind

/** Asks once what an unrecognised Bluetooth output is; the answer is remembered for that device. */
@Composable
internal fun DeviceKindCard(
    deviceName: String,
    style: EqualizerDesignStyle,
    onAnswer: (DeviceKind) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NeonPanel(style = style, modifier = modifier) {
        Text(
            text = stringResource(R.string.device_kind_question, deviceName),
            style = MaterialTheme.typography.titleSmall,
            color = style.text,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                DeviceKind.HEADPHONES to R.string.device_kind_headphones,
                DeviceKind.CAR to R.string.device_kind_car,
                DeviceKind.SPEAKER to R.string.device_kind_speaker,
            ).forEach { (kind, label) ->
                OutlinedButton(
                    onClick = { onAnswer(kind) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text(stringResource(label))
                }
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.device_kind_later))
        }
    }
}
