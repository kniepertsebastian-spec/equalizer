package com.auracurve.app.ui.equalizer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.context.SoundContext
import com.auracurve.app.ui.components.hardcore.HcDeviceKey
import com.auracurve.app.ui.theme.LocalHardcoreLook

@Composable
fun SoundContext.displayName(): String =
    stringResource(
        when (this) {
            SoundContext.CAR -> R.string.context_name_car
            SoundContext.BLUETOOTH_SPEAKER -> R.string.context_name_speaker
            SoundContext.SOUNDBAR -> R.string.context_name_soundbar
            SoundContext.IN_EAR -> R.string.context_name_in_ear
        },
    )

private fun SoundContext.icon(): ImageVector =
    when (this) {
        SoundContext.CAR -> Icons.Default.DirectionsCar
        SoundContext.BLUETOOTH_SPEAKER -> Icons.Default.Speaker
        SoundContext.SOUNDBAR -> Icons.Default.Tv
        SoundContext.IN_EAR -> Icons.Default.Headphones
    }

// The listening-context mode switches (car, Bluetooth speaker, soundbar, in-ear): tap one to turn the
// mode on, tap it again to turn it off. A mode, not a preset - it stays on when the
// genre preset changes. Shared by the main screen and the full player.
@Composable
fun ContextModeChips(
    activeContext: SoundContext?,
    onContextModeChanged: (SoundContext?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalHardcoreLook.current) {
        // Four device keys in two rows; the lit lamp and the pressed-in key show the mode that is on.
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SoundContext.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { context ->
                        val selected = activeContext == context
                        HcDeviceKey(
                            text = context.displayName(),
                            icon = context.icon(),
                            selected = selected,
                            onClick = { onContextModeChanged(if (selected) null else context) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        return
    }
    // Scrolls sideways: four chips do not fit one row on narrow phones.
    Row(modifier = modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SoundContext.entries.forEach { context ->
            val selected = activeContext == context
            ChoiceChip(
                text = context.displayName(),
                selected = selected,
                onClick = { onContextModeChanged(if (selected) null else context) },
                // The device's symbol; a check once it is on, so the state does not rest on the fill alone.
                leadingIcon = if (selected) Icons.Default.Check else context.icon(),
            )
        }
    }
}
