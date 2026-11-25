package com.auracurve.app.ui.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.audio.DeviceKind
import com.auracurve.app.ui.components.cyber.CyberOutlineButton

/** Asks once what an unrecognised Bluetooth output is; the answer is remembered for that device. */
@Composable
internal fun DeviceKindCard(
    deviceName: String,
    onAnswer: (DeviceKind) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Notice(modifier = modifier) {
        Text(
            text = stringResource(R.string.device_kind_question, deviceName),
            style = MaterialTheme.typography.titleSmall,
            color = SonicStyle.text,
        )
        // Four answers: two rows of two, so the labels stay readable on narrow phones.
        listOf(
            listOf(DeviceKind.HEADPHONES to R.string.device_kind_headphones, DeviceKind.CAR to R.string.device_kind_car),
            listOf(DeviceKind.SPEAKER to R.string.device_kind_speaker, DeviceKind.SOUNDBAR to R.string.device_kind_soundbar),
        ).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (kind, label) ->
                    CyberOutlineButton(
                        text = stringResource(label),
                        onClick = { onAnswer(kind) },
                        modifier = Modifier.weight(1f),
                        techCorners = false,
                    )
                }
            }
        }
        LinkButton(text = stringResource(R.string.device_kind_later), onClick = onDismiss)
    }
}
