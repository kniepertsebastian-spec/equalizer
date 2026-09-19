package com.hardbasseq.eq.ui.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.audio.EqualizerBandCapabilities
import com.hardbasseq.eq.audio.ProcessingSettings
import com.hardbasseq.eq.dsp.EqualizerInterpolator
import com.hardbasseq.eq.preset.BuiltInPresets
import com.hardbasseq.eq.preset.Preset
import com.hardbasseq.eq.ui.theme.spacing

@Composable
fun EqualizerScreen(
    state: AudioEngineState,
    route: AudioRoute,
    settings: ProcessingSettings,
    bands: List<EqualizerBandCapabilities>,
    activePreset: Preset,
    onMasterToggled: (Boolean) -> Unit,
    onBypassToggled: (Boolean) -> Unit,
    onPresetSelected: (Preset) -> Unit,
    onMacroBassChanged: (Float) -> Unit,
    onMacroPunchChanged: (Float) -> Unit,
    onMacroHaerteChanged: (Float) -> Unit,
    onBandGainChanged: (bandIndex: Int, gainDb: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val headroom = EqualizerInterpolator.calculateHeadroom(settings.bandGainsDb)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        // Top Header Card: Master Switch, Status, Active Route
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "HardBass EQ",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Route: ${route.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.masterEnabled,
                        onCheckedChange = onMasterToggled,
                    )
                }

                Spacer(modifier = Modifier.height(spacing.small))

                // Engine Status Chip
                val (statusText, statusBg) =
                    when (state) {
                        is AudioEngineState.Active -> "Aktiv (Session #${state.sessionId})" to MaterialTheme.colorScheme.primaryContainer
                        is AudioEngineState.Attaching -> "Anbinden... (#${state.sessionId})" to MaterialTheme.colorScheme.surfaceVariant
                        is AudioEngineState.Detached -> "Wartet auf Audio-Session" to MaterialTheme.colorScheme.surfaceVariant
                        is AudioEngineState.LostControl -> "Kontrollverlust" to MaterialTheme.colorScheme.errorContainer
                        is AudioEngineState.Error -> "Fehler: ${state.message}" to MaterialTheme.colorScheme.errorContainer
                        is AudioEngineState.Suspended -> "Pausiert: ${state.reason}" to MaterialTheme.colorScheme.surfaceVariant
                        is AudioEngineState.Unsupported -> "Nicht unterstützt" to MaterialTheme.colorScheme.errorContainer
                    }

                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusBg)
                            .padding(horizontal = spacing.small, vertical = spacing.extraSmall),
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Headroom / Clipping Protection Warning Banner
        AnimatedVisibility(visible = headroom.isClippingRisk && settings.masterEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(
                    modifier = Modifier.padding(spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Clipping Warnung",
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Column {
                        Text(
                            text = "Clipping-Schutz aktiv",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = "Anhebung +${String.format(
                                "%.1f",
                                headroom.maxPositiveGainDb,
                            )} dB. Empfohlene Absenkung: ${String.format("%.1f", headroom.recommendedInputGainDb)} dB.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        // Presets Selector Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                Text(
                    text = "Presets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    BuiltInPresets.all.forEach { preset ->
                        FilterChip(
                            selected = activePreset.id == preset.id,
                            onClick = { onPresetSelected(preset) },
                            label = { Text(preset.name, fontSize = 12.sp) },
                        )
                    }
                }
            }
        }

        // Macro Controls Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                Text(
                    text = "Makro-Regler",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(spacing.small))

                // Bass Macro
                Text(text = "Bass: ${String.format("%+.1f", settings.macroBassDb)} dB", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = settings.macroBassDb,
                    onValueChange = onMacroBassChanged,
                    valueRange = -6f..6f,
                )

                // Punch Macro
                Text(text = "Punch: ${String.format("%+.1f", settings.macroPunchDb)} dB", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = settings.macroPunchDb,
                    onValueChange = onMacroPunchChanged,
                    valueRange = -6f..6f,
                )

                // Härte Macro
                Text(text = "Härte: ${String.format("%+.1f", settings.macroHaerteDb)} dB", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = settings.macroHaerteDb,
                    onValueChange = onMacroHaerteChanged,
                    valueRange = -2f..2f,
                )
            }
        }

        // Dynamic Equalizer Bands Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Grafischer EQ (${bands.size} Bänder)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Bypass", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.width(spacing.extraSmall))
                        Switch(
                            checked = settings.bypass,
                            onCheckedChange = onBypassToggled,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.small))

                bands.forEach { band ->
                    val gainDb = settings.bandGainsDb[band.index] ?: 0f
                    val freqLabel = formatFrequency(band.centerFreqHz)

                    Column(modifier = Modifier.padding(vertical = spacing.extraSmall)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(text = freqLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(text = "${String.format("%+.1f", gainDb)} dB", style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = gainDb,
                            onValueChange = { newGain -> onBandGainChanged(band.index, newGain) },
                            valueRange = band.minGainDb..band.maxGainDb,
                        )
                    }
                }
            }
        }
    }
}

private fun formatFrequency(centerFreqHz: Int): String =
    if (centerFreqHz >= 1000) {
        "${centerFreqHz / 1000} kHz"
    } else {
        "$centerFreqHz Hz"
    }
