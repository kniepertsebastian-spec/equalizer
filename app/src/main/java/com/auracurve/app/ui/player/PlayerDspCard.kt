package com.auracurve.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.dsp.BassExciterSettings
import com.auracurve.app.dsp.BassMonoSummerSettings
import com.auracurve.app.dsp.PlayerDspSettings
import com.auracurve.app.ui.components.cyber.CyberCard
import com.auracurve.app.ui.equalizer.ChoiceChip
import com.auracurve.app.ui.theme.spacing
import kotlin.math.abs

// "Klang-Feinschliff" of the built-in player: mono bass (recommended, on by default)
// with its crossover frequency, the limiter that keeps the bass boost from clipping, and the
// character of the virtual bass (roadmap 10, P10-3).
@Composable
fun PlayerDspCard(
    settings: PlayerDspSettings,
    onMonoBass: (Boolean) -> Unit,
    onCutoff: (Float) -> Unit,
    onLimiter: (Boolean) -> Unit,
    onVirtualBassCharacter: (Float) -> Unit = {},
) {
    val spacing = MaterialTheme.spacing
    CyberCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            Text(text = stringResource(R.string.dsp_card_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.dsp_card_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DspSwitchRow(
                title = stringResource(R.string.dsp_mono_bass_title),
                subtitle = stringResource(R.string.dsp_mono_bass_desc, settings.monoBassCutoffHz.toInt()),
                checked = settings.monoBassEnabled,
                onCheckedChange = onMonoBass,
            )
            if (settings.monoBassEnabled) {
                val cutoffDescription = stringResource(R.string.dsp_cutoff_description)
                Slider(
                    modifier = Modifier.semantics { contentDescription = cutoffDescription },
                    value = settings.monoBassCutoffHz,
                    onValueChange = onCutoff,
                    valueRange = BassMonoSummerSettings.MIN_CUTOFF_HZ..BassMonoSummerSettings.MAX_CUTOFF_HZ,
                )
            }
            DspSwitchRow(
                title = stringResource(R.string.dsp_limiter_title),
                subtitle = stringResource(R.string.dsp_limiter_desc),
                checked = settings.limiterEnabled,
                onCheckedChange = onLimiter,
            )
            VirtualBassCharacterRow(
                share = settings.virtualBassThirdHarmonicShare,
                onSelect = onVirtualBassCharacter,
            )
        }
    }
}

// The three characters as pills; the one nearest the saved share is selected.
@Composable
private fun VirtualBassCharacterRow(
    share: Float,
    onSelect: (Float) -> Unit,
) {
    val characters =
        listOf(
            BassExciterSettings.WARM_THIRD_HARMONIC_SHARE to R.string.dsp_virtual_bass_warm,
            BassExciterSettings.DEFAULT_THIRD_HARMONIC_SHARE to R.string.dsp_virtual_bass_balanced,
            BassExciterSettings.PUNCH_THIRD_HARMONIC_SHARE to R.string.dsp_virtual_bass_punch,
        )
    val selected = characters.minBy { abs(it.first - share) }.first
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(text = stringResource(R.string.dsp_virtual_bass_character_title), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.dsp_virtual_bass_character_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            characters.forEach { (value, label) ->
                ChoiceChip(
                    text = stringResource(label),
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DspSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            modifier = Modifier.semantics { contentDescription = title },
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
