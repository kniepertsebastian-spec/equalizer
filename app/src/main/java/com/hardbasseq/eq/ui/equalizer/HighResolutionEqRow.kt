package com.hardbasseq.eq.ui.equalizer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R

/**
 * Switch between the classic 5-band system equalizer and the 34-band DynamicsProcessing
 * pre-EQ. The line below the title says what is really running, so a device that refused
 * the pre-EQ does not look like it is on.
 */
@Composable
internal fun HighResolutionEqRow(
    checked: Boolean,
    active: Boolean,
    style: EqualizerDesignStyle,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state =
        when {
            active -> R.string.high_res_eq_active
            checked -> R.string.high_res_eq_unavailable
            else -> R.string.high_res_eq_classic
        }
    NeonPanel(style = style, modifier = modifier, contentPadding = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.high_res_eq_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = style.text,
                )
                Text(
                    text = stringResource(state),
                    style = MaterialTheme.typography.labelSmall,
                    color = style.mutedText,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
