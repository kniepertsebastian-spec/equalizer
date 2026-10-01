package com.hardbasseq.eq.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.dsp.BassMonoSummerSettings
import com.hardbasseq.eq.dsp.PlayerDspSettings
import com.hardbasseq.eq.ui.theme.HardBassCardBorder
import com.hardbasseq.eq.ui.theme.spacing

// "Klang-Feinschliff" of the built-in player: mono bass (recommended, on by default)
// with its crossover frequency, and the limiter that keeps the bass boost from clipping.
@Composable
fun PlayerDspCard(
    settings: PlayerDspSettings,
    onMonoBass: (Boolean) -> Unit,
    onCutoff: (Float) -> Unit,
    onLimiter: (Boolean) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, HardBassCardBorder),
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            Text(text = "Klang-Feinschliff", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Wirkt nur im eingebauten Player, nicht in Spotify & Co.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DspSwitchRow(
                title = "Mono-Bass (empfohlen)",
                subtitle = "Tiefbass unter ${settings.monoBassCutoffHz.toInt()} Hz in der Mitte - straffer, vor allem mit In-Ears",
                checked = settings.monoBassEnabled,
                onCheckedChange = onMonoBass,
            )
            if (settings.monoBassEnabled) {
                Slider(
                    value = settings.monoBassCutoffHz,
                    onValueChange = onCutoff,
                    valueRange = BassMonoSummerSettings.MIN_CUTOFF_HZ..BassMonoSummerSettings.MAX_CUTOFF_HZ,
                )
            }
            DspSwitchRow(
                title = "Limiter (empfohlen)",
                subtitle = "Verhindert Übersteuern durch Bass-Boost und Virtual Bass",
                checked = settings.limiterEnabled,
                onCheckedChange = onLimiter,
            )
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
