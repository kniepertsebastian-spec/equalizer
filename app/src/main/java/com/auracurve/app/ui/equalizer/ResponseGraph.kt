package com.auracurve.app.ui.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.preset.TargetPoint
import com.auracurve.app.ui.components.cyber.CyberRoundButton
import com.auracurve.app.ui.components.cyber.SpectrumEqGraph
import com.auracurve.app.ui.components.cyber.SpectrumMode
import com.auracurve.app.ui.theme.CyberColors
import com.auracurve.app.ui.theme.SonicNumerals

/** One adjustable EQ band as the graph shows it: what the device reported, plus its gain. */
internal data class GraphBand(
    val index: Int,
    val frequencyHz: Float,
    val gainDb: Float,
    val minGainDb: Float,
    val maxGainDb: Float,
)

/** Step of the − and + buttons and of TalkBack's adjust gesture. */
internal const val GAIN_STEP_DB = 0.5f

private val GraphHeight = 248.dp

/**
 * The equalizer's frequency response graph and its main control.
 *
 * Frequency runs logarithmically from 20 Hz to 20 kHz, gain on a fixed scale from the
 * device's band range. The points are the gains the bands are really set to; the line
 * between them is a smooth interpolation for reading the shape, not a measured or
 * computed filter response - Android's system equalizer does not report its filter
 * shapes. Dragging anywhere picks the nearest band and moves it up or down.
 *
 * Drawn by [SpectrumEqGraph] in the Cyber look; gestures, haptics, TalkBack targets, folding
 * and axes are the same as before. [spectrum] chooses the synthetic bars under the curve
 * (decision E2: a visualisation shaped by the curve, never a measurement); [emphasis] is the
 * Device tab's stronger curve.
 */
@Composable
internal fun ResponseGraph(
    curve: List<TargetPoint>,
    bands: List<GraphBand>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    onGainChange: (bandIndex: Int, gainDb: Float) -> Unit,
    processing: Boolean,
    stateLabel: String?,
    hotFrequencyHz: Float?,
    description: String,
    modifier: Modifier = Modifier,
    // Shorter on short screens (EqualizerLayout.CompactGraphHeight); the drawing scales with it.
    height: Dp = GraphHeight,
    // A short help line drawn into the plot's top left, so it needs no row of its own.
    hint: String? = null,
    // Folded while the options below are scrolled: a flat overview without points or gestures.
    collapsed: Boolean = false,
    @Suppress("UNUSED_PARAMETER") style: EqualizerDesignStyle = SonicStyle,
    spectrum: SpectrumMode = SpectrumMode.STATIC,
    emphasis: Boolean = false,
) {
    SpectrumEqGraph(
        curve = curve,
        bands = bands,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        onGainChange = onGainChange,
        processing = processing,
        stateLabel = stateLabel,
        hotFrequencyHz = hotFrequencyHz,
        description = description,
        modifier = modifier,
        height = height,
        hint = hint,
        collapsed = collapsed,
        spectrum = spectrum,
        emphasis = emphasis,
    )
}

/**
 * The selected band's frequency and gain in large figures, with − and + for steps of
 * [GAIN_STEP_DB] - the precise way to set a value that a finger on the graph only roughs in.
 */
@Composable
internal fun BandReadout(
    band: GraphBand?,
    onGainChange: (bandIndex: Int, gainDb: Float) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (band == null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = style.mutedText,
                modifier = Modifier.weight(1f),
            )
            return@Row
        }
        val frequency = CurveGeometry.formatFrequency(band.frequencyHz)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = frequency, style = SonicNumerals.value, color = CyberColors.cyanText)
            Text(text = CurveGeometry.formatGain(band.gainDb), style = SonicNumerals.readout, color = style.text)
        }
        StepButton(
            icon = { Icon(Icons.Default.Remove, contentDescription = null) },
            description = stringResource(R.string.graph_band_lower, frequency),
            enabled = band.gainDb > band.minGainDb,
            onClick = { onGainChange(band.index, (band.gainDb - GAIN_STEP_DB).coerceAtLeast(band.minGainDb)) },
            style = style,
        )
        StepButton(
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            description = stringResource(R.string.graph_band_raise, frequency),
            enabled = band.gainDb < band.maxGainDb,
            onClick = { onGainChange(band.index, (band.gainDb + GAIN_STEP_DB).coerceAtMost(band.maxGainDb)) },
            style = style,
        )
    }
}

// A − or + key: the round glass key of the player's transport, 48 dp.
@Composable
private fun StepButton(
    icon: @Composable () -> Unit,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") style: EqualizerDesignStyle,
    size: Dp = 48.dp,
) {
    CyberRoundButton(
        onClick = onClick,
        enabled = enabled,
        size = size,
        modifier = Modifier.semantics { contentDescription = description },
        content = icon,
    )
}
