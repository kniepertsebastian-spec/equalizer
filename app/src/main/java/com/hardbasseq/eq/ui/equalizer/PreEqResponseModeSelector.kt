package com.hardbasseq.eq.ui.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.dsp.PreEqResponseMode

/**
 * Selector for the high-resolution EQ response mode: SMOOTH, BALANCED, or PRECISE.
 * Controls how aggressively the 34-band pre-EQ responds to adjustments.
 */
@Composable
internal fun PreEqResponseModeSelector(
    currentMode: PreEqResponseMode,
    active: Boolean,
    onModeChange: (PreEqResponseMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = active) {
        val style = SonicStyle
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = style.surface,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.high_res_eq_response_mode_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = style.text,
                )
                Text(
                    text = stringResource(R.string.high_res_eq_response_mode_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = style.mutedText,
                )
                Spacer(modifier = Modifier.height(12.dp))

                // SMOOTH option
                ResponseModeOption(
                    mode = PreEqResponseMode.SMOOTH,
                    title = stringResource(R.string.response_mode_smooth),
                    description = stringResource(R.string.response_mode_smooth_desc),
                    selected = currentMode == PreEqResponseMode.SMOOTH,
                    onClick = { onModeChange(PreEqResponseMode.SMOOTH) },
                )

                Spacer(modifier = Modifier.height(8.dp))

                // BALANCED option (recommended)
                ResponseModeOption(
                    mode = PreEqResponseMode.BALANCED,
                    title = stringResource(R.string.response_mode_balanced),
                    description = stringResource(R.string.response_mode_balanced_desc),
                    selected = currentMode == PreEqResponseMode.BALANCED,
                    onClick = { onModeChange(PreEqResponseMode.BALANCED) },
                )

                Spacer(modifier = Modifier.height(8.dp))

                // PRECISE option
                ResponseModeOption(
                    mode = PreEqResponseMode.PRECISE,
                    title = stringResource(R.string.response_mode_precise),
                    description = stringResource(R.string.response_mode_precise_desc),
                    selected = currentMode == PreEqResponseMode.PRECISE,
                    onClick = { onModeChange(PreEqResponseMode.PRECISE) },
                )
            }
        }
    }
}

@Composable
private fun ResponseModeOption(
    mode: PreEqResponseMode,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = SonicStyle
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick,
                    role = Role.RadioButton,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (selected) "Selected" else "Not selected",
            tint = if (selected) style.accent else style.mutedText,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) style.text else style.mutedText,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = style.mutedText,
            )
        }
    }
}
