package com.hardbasseq.eq.ui.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.dsp.ParametricBand
import com.hardbasseq.eq.dsp.ParametricCurve
import com.hardbasseq.eq.dsp.ParametricFilterBounds
import com.hardbasseq.eq.ui.theme.Spacing
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

// The sliders cover the useful part of the allowed ranges; the bounds themselves stay in ParametricFilterBounds.
private const val MIN_GAIN_SLIDER_DB = -15f
private const val MAX_GAIN_SLIDER_DB = 15f

/** Position 0..1 on a logarithmic scale between [min] and [max], and back. */
internal fun toLogPosition(
    value: Float,
    min: Float,
    max: Float,
): Float = (ln(value.coerceIn(min, max) / min) / ln(max / min)).coerceIn(0f, 1f)

internal fun fromLogPosition(
    position: Float,
    min: Float,
    max: Float,
): Float = min * (max / min).pow(position.coerceIn(0f, 1f))

internal fun formatFrequency(hz: Float): String = if (hz >= 1000f) "%.1f kHz".format(hz / 1000f) else "${hz.roundToInt()} Hz"

// P5-3: free bands - frequency, Q and gain per band. They add to the curve of the sound like the
// correction profile does, so every output follows them (exactly in the built-in player, as far as the
// band layout allows through the system equalizer).
@Composable
internal fun ParametricBandsSection(
    bands: List<ParametricBand>,
    onAdd: () -> Unit,
    onChange: (Int, ParametricBand) -> Unit,
    onRemove: (Int) -> Unit,
    spacing: Spacing,
    style: EqualizerDesignStyle,
) {
    Section {
        SectionHeader(
            title = stringResource(R.string.parametric_title),
            info = stringResource(R.string.parametric_info),
            trailing = {
                TextButton(onClick = onAdd, enabled = bands.size < ParametricCurve.MAX_BANDS) {
                    Text(stringResource(R.string.parametric_add))
                }
            },
        )
        if (bands.isEmpty()) {
            Text(
                text = stringResource(R.string.parametric_empty),
                style = MaterialTheme.typography.bodySmall,
                color = style.mutedText,
            )
        }
        bands.forEachIndexed { index, band ->
            Column(modifier = Modifier.fillMaxWidth().padding(top = spacing.small)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.parametric_band_title, index + 1),
                        style = MaterialTheme.typography.titleSmall,
                        color = style.text,
                        modifier = Modifier.weight(1f),
                    )
                    val toggleDescription = stringResource(R.string.parametric_band_enabled, index + 1)
                    Switch(
                        modifier = Modifier.semantics { contentDescription = toggleDescription },
                        checked = band.enabled,
                        onCheckedChange = { onChange(index, band.copy(enabled = it)) },
                    )
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.parametric_band_remove, index + 1),
                            tint = style.mutedText,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    ValueSlider(
                        label = stringResource(R.string.parametric_frequency),
                        description = null,
                        value =
                            toLogPosition(
                                band.frequencyHz,
                                ParametricFilterBounds.MIN_FREQUENCY_HZ,
                                ParametricFilterBounds.MAX_FREQUENCY_HZ,
                            ),
                        onValueChange = {
                            val hz = fromLogPosition(it, ParametricFilterBounds.MIN_FREQUENCY_HZ, ParametricFilterBounds.MAX_FREQUENCY_HZ)
                            onChange(index, band.copy(frequencyHz = hz))
                        },
                        valueRange = 0f..1f,
                        style = style,
                        valueText = formatFrequency(band.frequencyHz),
                    )
                    ValueSlider(
                        label = stringResource(R.string.parametric_gain),
                        description = null,
                        value = band.gainDb.coerceIn(MIN_GAIN_SLIDER_DB, MAX_GAIN_SLIDER_DB),
                        onValueChange = { onChange(index, band.copy(gainDb = (it * 2).roundToInt() / 2f)) },
                        valueRange = MIN_GAIN_SLIDER_DB..MAX_GAIN_SLIDER_DB,
                        style = style,
                    )
                    ValueSlider(
                        label = stringResource(R.string.parametric_q),
                        description = null,
                        value = toLogPosition(band.q, ParametricFilterBounds.MIN_Q, ParametricFilterBounds.MAX_Q),
                        onValueChange = {
                            val q = fromLogPosition(it, ParametricFilterBounds.MIN_Q, ParametricFilterBounds.MAX_Q)
                            onChange(index, band.copy(q = (q * 10).roundToInt() / 10f))
                        },
                        valueRange = 0f..1f,
                        style = style,
                        valueText = "%.1f".format(band.q),
                    )
                }
            }
        }
    }
}
