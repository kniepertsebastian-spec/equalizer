package com.hardbasseq.eq.ui.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.context.SoundContext

@Composable
fun SoundContext.displayName(): String =
    stringResource(
        when (this) {
            SoundContext.CAR -> R.string.context_name_car
            SoundContext.BLUETOOTH_SPEAKER -> R.string.context_name_speaker
        },
    )

// The listening-context mode switches ("Auto", "Bluetooth-Box"): tap one to turn the
// mode on, tap it again to turn it off. A mode, not a preset - it stays on when the
// genre preset changes. Shared by the main screen and the full player.
@Composable
fun ContextModeChips(
    activeContext: SoundContext?,
    onContextModeChanged: (SoundContext?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
