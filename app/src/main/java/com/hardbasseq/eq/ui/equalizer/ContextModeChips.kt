package com.hardbasseq.eq.ui.equalizer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    accent: Color,
    border: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SoundContext.entries.forEach { context ->
            val selected = activeContext == context
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, if (selected) accent else border),
            ) {
                TextButton(onClick = { onContextModeChanged(if (selected) null else context) }) {
                    Text(
                        text = context.displayName(),
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
