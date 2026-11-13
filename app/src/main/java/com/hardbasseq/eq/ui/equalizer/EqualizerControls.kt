package com.hardbasseq.eq.ui.equalizer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.MAX_RETRY_ATTEMPTS
import com.hardbasseq.eq.dsp.HeadphonePower
import com.hardbasseq.eq.ui.theme.SonicNumerals
import com.hardbasseq.eq.ui.theme.spacing
import kotlinx.coroutines.delay

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
            OutlinedButton(onClick = { onPowerChanged(HeadphonePower.DEFAULT) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.hp_power_standard))
            }
            Button(onClick = { onPowerChanged(HeadphonePower.KNALL) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.hp_power_knall))
            }
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
        Switch(checked = checked, onCheckedChange = null)
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
            Text(text = shown, style = SonicNumerals.value, color = style.text)
        }
        Slider(
            modifier =
                Modifier.semantics {
                    contentDescription = label
                    stateDescription = shown
                },
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = sonicSliderColors(style),
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
        Slider(
            modifier =
                Modifier.weight(1f).semantics {
                    contentDescription = frequency
                    stateDescription = value
                },
            value = band.gainDb,
            onValueChange = onGainChange,
            valueRange = band.minGainDb..band.maxGainDb,
            colors = sonicSliderColors(style),
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

// Quick A/B: the original sound plays while the button is held, the EQ again on release.
@Composable
internal fun HoldToCompareButton(
    onPress: () -> Unit,
    onRelease: () -> Unit,
    style: EqualizerDesignStyle,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val label = stringResource(R.string.compare_hold)
    Surface(
        modifier =
            modifier
                .heightIn(min = 52.dp)
                .semantics {
                    contentDescription = label
                    role = Role.Button
                }.pointerInput(Unit) {
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
                },
        shape = MaterialTheme.shapes.small,
        color = if (pressed) style.text else style.surfaceVariant,
        border = BorderStroke(1.dp, if (pressed) style.text else style.border),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(if (pressed) R.string.compare_hold_active else R.string.compare_hold),
                style = MaterialTheme.typography.labelLarge,
                color = if (pressed) style.background else style.text,
            )
        }
    }
}
