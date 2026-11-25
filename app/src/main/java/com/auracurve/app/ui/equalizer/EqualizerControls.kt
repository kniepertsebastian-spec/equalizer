package com.auracurve.app.ui.equalizer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.audio.AudioEngineState
import com.auracurve.app.audio.MAX_RETRY_ATTEMPTS
import com.auracurve.app.dsp.HeadphonePower
import com.auracurve.app.ui.components.cyber.CyberOutlineButton
import com.auracurve.app.ui.theme.CyberBrushes
import com.auracurve.app.ui.theme.CyberColors
import com.auracurve.app.ui.theme.CyberDims
import com.auracurve.app.ui.theme.CyberGlow
import com.auracurve.app.ui.theme.PillShape
import com.auracurve.app.ui.theme.SonicNumerals
import com.auracurve.app.ui.theme.cyberGlow
import com.auracurve.app.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlin.math.abs

// docs/STATE_MACHINE.md §5: "Erneuter Versuch in {sekunden}s (Versuch
// {attempt}/5)" - ticks down live so the acceptance criterion ("kein
// dauerhafter Kontrollverlust") is visible to the user, not just true in
// the abstract.
@Composable
internal fun retryingStatusText(state: AudioEngineState.Retrying): String {
    var remainingSeconds by remember(state) { mutableStateOf(secondsUntil(state.nextRetryAtMillis)) }
    LaunchedEffect(state) {
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds = secondsUntil(state.nextRetryAtMillis)
        }
    }
    return stringResource(R.string.retry_countdown, remainingSeconds, state.attempt, MAX_RETRY_ATTEMPTS)
}

internal fun secondsUntil(millis: Long): Long = ((millis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)

// "Kopfhörer-Power": Bass, Dynamik und Loudness im Kopfhörer-Modus einstellbar,
// dazu die Presets "Standard" (altes Verhalten) und "Knall".
@Composable
internal fun HeadphonePowerSection(
    power: HeadphonePower,
    onPowerChanged: (HeadphonePower) -> Unit,
    style: EqualizerDesignStyle,
) {
    val spacing = MaterialTheme.spacing
    Section {
        SectionHeader(title = stringResource(R.string.hp_power_title))
        Text(
            text = stringResource(R.string.hp_power_warning),
            style = MaterialTheme.typography.bodySmall,
            color = style.mutedText,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            CyberOutlineButton(
                text = stringResource(R.string.hp_power_standard),
                onClick = { onPowerChanged(HeadphonePower.DEFAULT) },
                techCorners = false,
                color = CyberColors.cyanText,
            )
            CyberOutlineButton(
                text = stringResource(R.string.hp_power_knall),
                onClick = { onPowerChanged(HeadphonePower.KNALL) },
                techCorners = false,
                color = CyberColors.orchid,
            )
        }
        ValueSlider(
            label = stringResource(R.string.hp_power_bass_name),
            description = null,
            value = power.bassDb,
            onValueChange = { onPowerChanged(power.copy(bassDb = it)) },
            valueRange = 0f..HeadphonePower.MAX_BASS_DB,
            style = style,
        )
        ValueSlider(
            label = stringResource(R.string.hp_power_loudness_name),
            description = null,
            value = power.loudnessMaxDb,
            onValueChange = { onPowerChanged(power.copy(loudnessMaxDb = it)) },
            valueRange = 0f..HeadphonePower.MAX_LOUDNESS_DB,
            valueText = if (power.loudnessMaxDb > 0f) null else stringResource(R.string.module_off),
            style = style,
        )
        SwitchRow(
            title = stringResource(R.string.hp_power_dynamics),
            description = stringResource(R.string.hp_power_dynamics_desc),
            checked = power.easeDynamics,
            onCheckedChange = { onPowerChanged(power.copy(easeDynamics = it)) },
            style = style,
        )
        Text(
            text = stringResource(R.string.hp_power_player_only),
            style = MaterialTheme.typography.bodySmall,
            color = style.mutedText,
        )
    }
}

/** A labelled switch row; the whole row is the touch target. */
@Composable
internal fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    style: EqualizerDesignStyle,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = style.text)
            if (description != null) {
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = style.mutedText)
            }
        }
        // The row handles the toggle, so the switch itself only shows the state.
        GradientSwitch(checked = checked, style = style)
    }
}

/** A dB control: name and exact value on one line, the slider below, then what it does. */
@Composable
internal fun ValueSlider(
    label: String,
    description: String?,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    style: EqualizerDesignStyle,
    valueText: String? = null,
) {
    val shown = valueText ?: CurveGeometry.formatGain(value)
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = style.text, modifier = Modifier.weight(1f))
            ValueBadge(text = shown, style = style)
        }
        SonicSlider(
            modifier =
                Modifier.semantics {
                    contentDescription = label
                    stateDescription = shown
                },
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            style = style,
        )
        if (description != null) {
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = style.mutedText)
        }
    }
}

// One band as a slider row: frequency, slider, value. The band selected on the graph is
// marked with an amber frequency and a tonal background.
@Composable
internal fun BandSliderRow(
    band: GraphBand,
    selected: Boolean,
    onGainChange: (Float) -> Unit,
    style: EqualizerDesignStyle,
) {
    val frequency = CurveGeometry.formatFrequency(band.frequencyHz)
    val value = CurveGeometry.formatGain(band.gainDb)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(if (selected) style.surface else Color.Transparent)
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = frequency,
            style = SonicNumerals.value,
            color = if (selected) style.accent else style.mutedText,
            modifier = Modifier.width(64.dp),
        )
        SonicSlider(
            modifier =
                Modifier.weight(1f).semantics {
                    contentDescription = frequency
                    stateDescription = value
                },
            value = band.gainDb,
            onValueChange = onGainChange,
            valueRange = band.minGainDb..band.maxGainDb,
            style = style,
        )
        Text(
            text = value,
            style = SonicNumerals.value,
            color = style.text,
            textAlign = TextAlign.End,
            modifier = Modifier.width(76.dp),
        )
    }
}

/** The exact value as a small pill next to a control's name. */
@Composable
internal fun ValueBadge(
    text: String,
    style: EqualizerDesignStyle,
) {
    Text(
        text = text,
        style = SonicNumerals.value,
        color = style.text,
        modifier =
            Modifier
                .clip(PillShape)
                .background(style.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * Slider in the Cyber look: a dark pill track, the active part as a neon line in the curve's
 * gradient over a soft halo, and a white thumb in a cyan ring with a glow. When the range spans 0
 * (dB gain), the active part grows from 0 to either side, so boost and cut read at a glance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SonicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    style: EqualizerDesignStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    // Read only while drawing, so moving the thumb redraws the track without rebuilding it.
    val shown = rememberUpdatedState(value)
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interaction,
        colors = sonicSliderColors(style),
        thumb = {
            Box(
                modifier =
                    Modifier
                        .size(20.dp)
                        .then(if (enabled) Modifier.cyberGlow(CyberColors.cyan, CyberGlow.thumb, CircleShape) else Modifier)
                        .clip(CircleShape)
                        .background(if (enabled) Color.White else style.mutedText)
                        .then(if (enabled) Modifier.border(2.dp, CyberColors.cyan, CircleShape) else Modifier),
            )
        },
        track = {
            val span = valueRange.endInclusive - valueRange.start
            val origin = if (valueRange.start < 0f && valueRange.endInclusive > 0f) 0f else valueRange.start
            Spacer(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .drawWithCache {
                            val radius = CornerRadius(size.height / 2f)
                            val haloRadius = CornerRadius(size.height)
                            val line = CyberBrushes.curve(0f, size.width)
                            val rim = Stroke(width = 1.dp.toPx())
                            val halo = size.height * 0.6f

                            fun x(v: Float) = if (span <= 0f) 0f else ((v - valueRange.start) / span).coerceIn(0f, 1f) * size.width
                            val a = x(origin)
                            onDrawBehind {
                                drawRoundRect(color = CyberColors.surfaceHigh, cornerRadius = radius)
                                drawRoundRect(color = CyberColors.outline, cornerRadius = radius, alpha = 0.6f, style = rim)
                                val b = x(shown.value)
                                val left = minOf(a, b)
                                val width = abs(b - a)
                                if (width > 0f) {
                                    if (enabled) {
                                        drawRoundRect(
                                            brush = line,
                                            alpha = 0.28f,
                                            topLeft = Offset(left - halo, -halo),
                                            size = Size(width + 2f * halo, size.height + 2f * halo),
                                            cornerRadius = haloRadius,
                                        )
                                    }
                                    drawRoundRect(
                                        brush = line,
                                        alpha = if (enabled) 1f else 0.38f,
                                        topLeft = Offset(left, 0f),
                                        size = Size(width, size.height),
                                        cornerRadius = radius,
                                    )
                                }
                            }
                        },
            )
        },
    )
}

@Composable
internal fun sonicSliderColors(style: EqualizerDesignStyle) =
    SliderDefaults.colors(
        thumbColor = style.accent,
        activeTrackColor = style.accent,
        inactiveTrackColor = style.surfaceVariant,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
    )

// Gives the current output device a name of the user's own ("My headphones"); empty = reset.
@Composable
internal fun RenameRouteDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.route_rename_title)) },
        text = {
            Column {
                Text(stringResource(R.string.route_rename_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// Quick A/B: the original sound plays while the button is held, the EQ again on release. An
// outlined cyan key like CyberOutlineButton (without the tech corners); held, it fills with the
// pill gradient, so the state reads from the fill as well as the words.
@Composable
internal fun HoldToCompareButton(
    onPress: () -> Unit,
    onRelease: () -> Unit,
    @Suppress("UNUSED_PARAMETER") style: EqualizerDesignStyle,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val label = stringResource(R.string.compare_hold)
    val shape = MaterialTheme.shapes.small
    Box(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .semantics {
                    contentDescription = label
                    role = Role.Button
                }.clip(shape)
                .then(
                    if (pressed) {
                        Modifier.background(CyberBrushes.pill).border(CyberDims.activeBorder, CyberBrushes.pillBorder, shape)
                    } else {
                        Modifier
                            .background(CyberColors.cyan.copy(alpha = 0.08f))
                            .border(CyberDims.border, CyberColors.cyan.copy(alpha = 0.7f), shape)
                    },
                ).pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            onPress()
                            try {
                                tryAwaitRelease()
                            } finally {
                                pressed = false
                                onRelease()
                            }
                        },
                    )
                }.padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(if (pressed) R.string.compare_hold_active else R.string.compare_hold),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = if (pressed) Color.White else CyberColors.cyan,
        )
    }
}

/**
 * The band gains as tiny faders next to the Graphic EQ title, lowest band on the left. Display
 * only (decision E4): the sliders below set them. With no bands, dimmed placeholders stand in,
 * and [active] false (bypass, EQ off) dims the caps, so the state does not rest on color alone.
 */
@Composable
internal fun MiniFaders(
    bands: List<GraphBand>,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val positions =
        remember(bands) {
            bands.sortedBy { it.frequencyHz }.map { band ->
                val span = (band.maxGainDb - band.minGainDb).takeIf { it > 0f } ?: 1f
                ((band.gainDb - band.minGainDb) / span).coerceIn(0f, 1f)
            }
        }
    val count = if (positions.isEmpty()) PLACEHOLDER_FADERS else positions.size
    val shown = rememberUpdatedState(positions)
    val lit = rememberUpdatedState(active)
    Spacer(
        modifier =
            modifier
                .width(FaderPitch * count)
                .height(FaderHeight)
                .drawWithCache {
                    val pitch = size.width / count
                    val track = 1.5.dp.toPx()
                    val cap = Size(minOf(pitch * 0.8f, 7.dp.toPx()), 3.dp.toPx())
                    val corner = CornerRadius(cap.height / 2f)
                    val travel = size.height - cap.height
                    val middle = size.height / 2f
                    val glow = CyberBrushes.curve(0f, size.width)
                    onDrawBehind {
                        val values = shown.value
                        val on = lit.value && values.isNotEmpty()
                        for (i in 0 until count) {
                            val x = pitch * (i + 0.5f)
                            val fraction = values.getOrElse(i) { 0.5f }
                            val top = (1f - fraction) * travel
                            drawLine(CyberColors.outline, Offset(x, 0f), Offset(x, size.height), track)
                            if (on) {
                                // The part between 0 dB and the cap, in the curve's gradient.
                                val capMiddle = top + cap.height / 2f
                                drawLine(glow, Offset(x, middle), Offset(x, capMiddle), track)
                            }
                            drawRoundRect(
                                color = if (on) Color.White else CyberColors.mutedText,
                                topLeft = Offset(x - cap.width / 2f, top),
                                size = cap,
                                cornerRadius = corner,
                                alpha =
                                    if (values.isEmpty()) {
                                        0.35f
                                    } else if (on) {
                                        1f
                                    } else {
                                        0.6f
                                    },
                            )
                        }
                    }
                },
    )
}

private const val PLACEHOLDER_FADERS = 5
private val FaderPitch = 6.dp
private val FaderHeight = 28.dp
