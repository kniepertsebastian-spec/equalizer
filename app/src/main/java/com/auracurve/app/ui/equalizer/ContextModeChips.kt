package com.auracurve.app.ui.equalizer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.context.SoundContext

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

// The listening-context mode switches (car, Bluetooth speaker, soundbar, in-ear): tap one to turn the
// mode on, tap it again to turn it off. A mode, not a preset - it stays on when the
// genre preset changes. Shared by the main screen and the full player.
@Composable
fun ContextModeChips(
    activeContext: SoundContext?,
    onContextModeChanged: (SoundContext?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Scrolls sideways: four chips do not fit one row on narrow phones.
    Row(modifier = modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SoundContext.entries.forEach { context ->
            val selected = activeContext == context
            ChoiceChip(
                text = context.displayName(),
                selected = selected,
                onClick = { onContextModeChanged(if (selected) null else context) },
            )
        }
    }
}
