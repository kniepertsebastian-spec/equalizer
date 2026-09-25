package com.hardbasseq.eq.ui.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.audio.EqualizerBandCapabilities
import com.hardbasseq.eq.audio.MAX_RETRY_ATTEMPTS
import com.hardbasseq.eq.audio.ProcessingSettings
import com.hardbasseq.eq.dsp.EqualizerInterpolator
import com.hardbasseq.eq.preset.Preset
import com.hardbasseq.eq.ui.theme.HardBassCardBorder
import com.hardbasseq.eq.ui.theme.Spacing
import com.hardbasseq.eq.ui.theme.spacing
import kotlinx.coroutines.delay

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun EqualizerScreen(
    state: AudioEngineState,
    route: AudioRoute,
    settings: ProcessingSettings,
    bands: List<EqualizerBandCapabilities>,
    activePreset: Preset,
    allPresets: List<Preset>,
    isDirty: Boolean,
    onMasterToggled: (Boolean) -> Unit,
    onOpenSourcePicker: () -> Unit,
    onBypassToggled: (Boolean) -> Unit,
    onPresetSelected: (Preset) -> Unit,
    onResetToActivePreset: () -> Unit,
    onSaveAsNewRequest: () -> Unit,
    onDuplicatePreset: (Preset) -> Unit,
    onRenamePresetRequest: (Preset) -> Unit,
    onDeletePresetRequest: (Preset) -> Unit,
    onMacroBassChanged: (Float) -> Unit,
    onMacroPunchChanged: (Float) -> Unit,
    onMacroHaerteChanged: (Float) -> Unit,
    onBandGainChanged: (bandIndex: Int, gainDb: Float) -> Unit,
    onRetryAttach: () -> Unit,
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
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, HardBassCardBorder),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Independent of the master switch below - that one only
                        // offers the source picker when flipped on with nothing
                        // attached yet (see MainViewModel.setMasterEnabled), so
                        // switching/reopening a source with the EQ already on, or
                        // something else already attached, needed disabling and
                        // re-enabling the master bar just to see the dialog again.
                        IconButton(onClick = onOpenSourcePicker) {
                            Icon(
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = "Quelle wählen",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.masterEnabled,
                            onCheckedChange = onMasterToggled,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.small))

                // Engine Status Chip - docs/STATE_MACHINE.md §5 UI-status mapping.
                // Detached/Listening/Attaching/LostControl/Retrying all share the
                // "Wartet" category (surfaceVariant); Unsupported and Error get
                // their own distinct categories so all 4 required statuses are
                // visually distinguishable.
                val (statusText, statusBg) =
                    when (state) {
                        is AudioEngineState.Active -> "Aktiv (Session #${state.sessionId})" to MaterialTheme.colorScheme.primaryContainer
                        is AudioEngineState.Attaching -> "Anbinden... (#${state.sessionId})" to MaterialTheme.colorScheme.surfaceVariant
                        is AudioEngineState.Detached -> "Startet…" to MaterialTheme.colorScheme.surfaceVariant
                        is AudioEngineState.Listening -> "Wartet auf Audio-Session" to MaterialTheme.colorScheme.surfaceVariant
                        is AudioEngineState.LostControl ->
                            "Verbindung verloren, versuche erneut…" to MaterialTheme.colorScheme.surfaceVariant
                        is AudioEngineState.Retrying -> retryingStatusText(state) to MaterialTheme.colorScheme.surfaceVariant
                        is AudioEngineState.Unsupported ->
                            "Nicht unterstützt: ${state.reason}" to MaterialTheme.colorScheme.tertiaryContainer
                        is AudioEngineState.Error -> "Fehler: ${state.message}" to MaterialTheme.colorScheme.errorContainer
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

                // "Fehler: konkrete nächste Handlung anbieten" (roadmap-2026.md M1) -
                // resets the retry budget and re-attempts the last known session.
                AnimatedVisibility(visible = state is AudioEngineState.Error) {
                    OutlinedButton(
                        onClick = onRetryAttach,
                        modifier = Modifier.padding(top = spacing.extraSmall),
                    ) {
                        Text("Erneut versuchen")
                    }
                }
            }
        }

        // Headroom / Clipping Protection Warning Banner
        AnimatedVisibility(visible = headroom.isClippingRisk && settings.masterEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                border = BorderStroke(1.dp, HardBassCardBorder),
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

        // No-session guidance: AudioSessionForegroundService now listens for the
        // OPEN_AUDIO_EFFECT_CONTROL_SESSION broadcast in the background, independent
        // of whether this screen is open, so the "app wasn't running yet" case is
        // covered. But that broadcast only fires once per player session and still
        // won't arrive at all for a session that was already open before the service
        // started (e.g. right after install) or - per the M0 spike's unresolved
        // finding (roadmap.md Session 4) - possibly not at all on some devices. Explain
        // what to try instead of leaving the user staring at "Wartet auf Audio-Session".
        // Detached and Listening both mean "no session" - see AudioEngineState.
        AnimatedVisibility(visible = state is AudioEngineState.Detached || state is AudioEngineState.Listening) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, HardBassCardBorder),
            ) {
                Row(
                    modifier = Modifier.padding(spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Hinweis",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Column {
                        Text(
                            text = "Keine Audio-Session gefunden",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text =
                                "HardBass EQ lauscht jetzt auch im Hintergrund auf neue Sessions. " +
                                    "Läuft ein Player wie Spotify oder SoundCloud aber schon seit vor der " +
                                    "Installation bzw. dem letzten Neustart, hilft meist: Titel pausieren " +
                                    "und erneut abspielen, zum nächsten Titel springen, oder die Player-App " +
                                    "einmal schließen und neu starten.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Presets Selector Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, HardBassCardBorder),
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Presets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    // M2: "manuelle Änderung eines Presets automatisch als Custom
                    // markieren" - only shown once a band/macro edit has actually
                    // moved away from what activePreset alone would produce.
                    AnimatedVisibility(visible = isDirty) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onResetToActivePreset) {
                                Text("Zurücksetzen")
                            }
                            TextButton(onClick = onSaveAsNewRequest) {
                                Text("Speichern")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(spacing.small))
                PresetGrid(
                    presets = allPresets,
                    activePresetId = activePreset.id,
                    onPresetSelected = onPresetSelected,
                    onDuplicatePreset = onDuplicatePreset,
                    onRenamePresetRequest = onRenamePresetRequest,
                    onDeletePresetRequest = onDeletePresetRequest,
                    spacing = spacing,
                )
            }
        }

        // Macro Controls Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, HardBassCardBorder),
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
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, HardBassCardBorder),
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

// Three-per-row grid of icon presets, matching the target design. Static/chunked
// rather than LazyVerticalGrid since the built-in preset list is small and fixed.
@Composable
private fun PresetGrid(
    presets: List<Preset>,
    activePresetId: String,
    onPresetSelected: (Preset) -> Unit,
    onDuplicatePreset: (Preset) -> Unit,
    onRenamePresetRequest: (Preset) -> Unit,
    onDeletePresetRequest: (Preset) -> Unit,
    spacing: Spacing,
) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        presets.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                row.forEach { preset ->
                    PresetCard(
                        preset = preset,
                        selected = preset.id == activePresetId,
                        onClick = { onPresetSelected(preset) },
                        onDuplicate = { onDuplicatePreset(preset) },
                        onRenameRequest = { onRenamePresetRequest(preset) },
                        onDeleteRequest = { onDeletePresetRequest(preset) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: Preset,
    selected: Boolean,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onRenameRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else HardBassCardBorder
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
    ) {
        Row(
            modifier =
                Modifier.fillMaxSize().padding(
                    start = spacing.small,
                    end = spacing.extraSmall,
                    top = spacing.extraSmall,
                    bottom = spacing.extraSmall,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = presetIcon(preset.id),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(spacing.extraSmall))
            Text(
                text = preset.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Weitere Aktionen für ${preset.name}",
                        tint = contentColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
                // "Duplizieren" works on any preset, built-in included - it's the
                // way to turn one into an editable starting point. "Umbenennen"/
                // "Löschen" only make sense for custom presets: "Built-ins bleiben
                // unveränderlich" (M2 acceptance criterion).
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Duplizieren") },
                        onClick = {
                            showMenu = false
                            onDuplicate()
                        },
                    )
                    if (!preset.metadata.builtIn) {
                        DropdownMenuItem(
                            text = { Text("Umbenennen") },
                            onClick = {
                                showMenu = false
                                onRenameRequest()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Löschen") },
                            onClick = {
                                showMenu = false
                                onDeleteRequest()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun presetIcon(presetId: String): ImageVector =
    when (presetId) {
        "builtin_clean_punch" -> Icons.Filled.SportsMma
        "builtin_deep_rumble" -> Icons.Filled.Waves
        "builtin_kick_attack" -> Icons.Filled.Bolt
        "builtin_balanced" -> Icons.Filled.Balance
        "builtin_flat" -> Icons.Filled.Remove
        "builtin_raw_power" -> Icons.Filled.PowerSettingsNew
        "builtin_fast_attack" -> Icons.Filled.Speed
        "builtin_maximum_distortion" -> Icons.Filled.Block
        "builtin_final_smash" -> Icons.Filled.Whatshot
        else -> Icons.Filled.Equalizer
    }

private fun formatFrequency(centerFreqHz: Int): String =
    if (centerFreqHz >= 1000) {
        "${centerFreqHz / 1000} kHz"
    } else {
        "$centerFreqHz Hz"
    }

// docs/STATE_MACHINE.md §5: "Erneuter Versuch in {sekunden}s (Versuch
// {attempt}/5)" - ticks down live so the acceptance criterion ("kein
// dauerhafter Kontrollverlust") is visible to the user, not just true in
// the abstract.
@Composable
private fun retryingStatusText(state: AudioEngineState.Retrying): String {
    var remainingSeconds by remember(state) { mutableStateOf(secondsUntil(state.nextRetryAtMillis)) }
    LaunchedEffect(state) {
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds = secondsUntil(state.nextRetryAtMillis)
        }
    }
    return "Erneuter Versuch in ${remainingSeconds}s (Versuch ${state.attempt}/$MAX_RETRY_ATTEMPTS)"
}

private fun secondsUntil(millis: Long): Long = ((millis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
