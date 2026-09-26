package com.hardbasseq.eq.ui.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.audio.EqualizerBandCapabilities
import com.hardbasseq.eq.audio.MAX_RETRY_ATTEMPTS
import com.hardbasseq.eq.audio.ProcessingSettings
import com.hardbasseq.eq.autoeq.AutoEqCatalogEntry
import com.hardbasseq.eq.correction.CorrectionProfile
import com.hardbasseq.eq.dsp.CurveComposer
import com.hardbasseq.eq.dsp.HeadroomCalculator
import com.hardbasseq.eq.dsp.HeadroomWarningLevel
import com.hardbasseq.eq.dsp.HeadroomWarningLevelCalculator
import com.hardbasseq.eq.preset.Preset
import com.hardbasseq.eq.preset.PresetDesign
import com.hardbasseq.eq.ui.theme.Spacing
import com.hardbasseq.eq.ui.theme.spacing
import kotlinx.coroutines.delay

@Composable
fun EqualizerScreen(
    state: AudioEngineState,
    route: AudioRoute,
    settings: ProcessingSettings,
    bands: List<EqualizerBandCapabilities>,
    activePreset: Preset,
    allPresets: List<Preset>,
    activeCorrectionProfile: CorrectionProfile,
    allCorrectionProfiles: List<CorrectionProfile>,
    suggestedCorrectionProfile: AutoEqCatalogEntry?,
    effectiveHeadphoneAcoustics: Boolean,
    isDirty: Boolean,
    onMasterToggled: (Boolean) -> Unit,
    onOpenSourcePicker: () -> Unit,
    onBypassToggled: (Boolean) -> Unit,
    onPresetSelected: (Preset) -> Unit,
    onCorrectionProfileSelected: (CorrectionProfile) -> Unit,
    onImportCorrectionProfileRequested: () -> Unit,
    onExportCorrectionProfile: (CorrectionProfile) -> Unit,
    onAcceptSuggestedCorrectionProfile: () -> Unit,
    onDismissSuggestedCorrectionProfile: () -> Unit,
    onHeadphoneAcousticsChanged: (Boolean) -> Unit,
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
    val design = PresetDesign.forPreset(activePreset)
    val style = styleFor(design)
    // M3/M4: headroom from the combined correction+voicing curve, not just the
    // highest of the discrete post-mapping band gains - see HeadroomCalculator.
    // Mirrors MainViewModel.recalculateBandGains()'s own combination so the banner
    // here always agrees with the inputGainDb the engine actually applied.
    val combinedCurve = CurveComposer.combine(activeCorrectionProfile.curve, activePreset.targetCurve)
    val headroom =
        HeadroomCalculator.fromCombinedCurve(
            combinedCurve = combinedCurve,
            macroBassDb = settings.macroBassDb,
            macroPunchDb = settings.macroPunchDb,
            macroHaerteDb = settings.macroHaerteDb,
        )

    MaterialTheme(colorScheme = style.colorScheme) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(style.background, style.backgroundEnd))),
        ) {
            style.backgroundRes?.let { backgroundRes ->
                Image(
                    painter = painterResource(backgroundRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                // Top Header Card: Master Switch, Status, Active Route
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(style.cardCorner),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, style.border),
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
                                Text(
                                    text = style.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = style.accent,
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
                        val statusColors = MaterialTheme.colorScheme
                        val (statusText, statusBg) =
                            when (state) {
                                is AudioEngineState.Active -> "Aktiv (Session #${state.sessionId})" to statusColors.primaryContainer
                                is AudioEngineState.Attaching -> "Anbinden... (#${state.sessionId})" to statusColors.surfaceVariant
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

                // M4 "Angewandten Input-Gain permanent anzeigen" / "Limiter-Status und
                // verwendeten Threshold anzeigen" / "Warnstufen definieren" / MBC-
                // Transparenz. Always visible (unlike the clipping banner above), since M4
                // explicitly asks for the applied input gain to be shown *permanently*,
                // not just when there's a clipping risk.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(style.cardCorner),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, style.border),
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Text(
                            text = "Signal & Sicherheit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(spacing.small))

                        Text(
                            text = "Input-Gain: ${String.format("%+.1f", settings.inputGainDb)} dB",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text =
                                if (settings.limiterEnabled) {
                                    "Limiter: aktiv, Threshold ${String.format("%.1f", settings.limiterThresholdDb)} dB"
                                } else {
                                    "Limiter: deaktiviert"
                                },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text =
                                if (settings.mbcEnabled) {
                                    "Mehrband-Kompressor: aktiv (Wirkung geschätzt, nicht gemessen)"
                                } else {
                                    "Mehrband-Kompressor: deaktiviert"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(spacing.extraSmall))

                        val warningLevel = HeadroomWarningLevelCalculator.fromHeadroom(headroom)
                        val (warningText, warningColor) =
                            when (warningLevel) {
                                HeadroomWarningLevel.SUFFICIENT ->
                                    "Ausreichend Headroom" to MaterialTheme.colorScheme.onSurfaceVariant
                                HeadroomWarningLevel.OCCASIONAL_LIMITING ->
                                    "Limiter arbeitet voraussichtlich gelegentlich (geschätzt)" to MaterialTheme.colorScheme.tertiary
                                HeadroomWarningLevel.HEAVY_LIMITING ->
                                    "Voraussichtlich dauerhaft starke Begrenzung (geschätzt)" to MaterialTheme.colorScheme.error
                            }
                        Text(
                            text = warningText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = warningColor,
                        )
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
                        shape = RoundedCornerShape(style.cardCorner),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, style.border),
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

                // M3 "Mein Kopfhörer" Card: which headphone/speaker correction curve is
                // combined with the voicing preset below (Klangstil card).
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(style.cardCorner),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, style.border),
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Mein Kopfhörer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            // M5 "Vorhandenen AutoEQ-Parser in einen vollständigen
                            // Importflow integrieren" - opens the SAF file picker; the
                            // actual parsing/preview happens back in MainViewModel once
                            // the picked file's text is read (MainScreen owns the
                            // ContentResolver access this needs).
                            TextButton(onClick = onImportCorrectionProfileRequested) {
                                Text("Importieren")
                            }
                        }
                        Spacer(modifier = Modifier.height(spacing.small))

                        // Chat feature (not a roadmap-2026.md milestone): a
                        // suggestion only, never applied silently - see
                        // MainViewModel.updateSuggestedCorrectionProfile()/
                        // AutoEqCatalogMatcher for why (name matching is never
                        // certain enough for a quiet auto-import).
                        AnimatedVisibility(visible = suggestedCorrectionProfile != null) {
                            if (suggestedCorrectionProfile != null) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .padding(spacing.small),
                                ) {
                                    Text(
                                        text = "Erkanntes Gerät: ${suggestedCorrectionProfile.displayName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Text(
                                        text =
                                            "Passendes AutoEQ-Korrekturprofil verfügbar " +
                                                "(${suggestedCorrectionProfile.profile.sourceLabel}).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                        TextButton(onClick = onDismissSuggestedCorrectionProfile) {
                                            Text("Nicht jetzt")
                                        }
                                        TextButton(onClick = onAcceptSuggestedCorrectionProfile) {
                                            Text("Übernehmen")
                                        }
                                    }
                                }
                            }
                        }

                        CorrectionProfileRow(
                            profiles = allCorrectionProfiles,
                            activeProfileId = activeCorrectionProfile.id,
                            onProfileSelected = onCorrectionProfileSelected,
                            onExportProfile = onExportCorrectionProfile,
                            spacing = spacing,
                            style = style,
                        )

                        Spacer(modifier = Modifier.height(spacing.small))

                        // Chat feature (item 1 of "setz alle Punkte um"): gates
                        // headphone-only DSP (Crossfeed - item 5) that would
                        // actively hurt a proper stereo speaker image if left on.
                        // Defaults from the route type (MainViewModel.
                        // effectiveHeadphoneAcoustics) until explicitly overridden
                        // here.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Kopfhörer-Modus",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(
                                checked = effectiveHeadphoneAcoustics,
                                onCheckedChange = onHeadphoneAcousticsChanged,
                            )
                        }
                    }
                }

                // Klangstil (Voicing) Selector Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(style.cardCorner),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, style.border),
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Presets: ${style.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = style.accent,
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
                            style = style,
                            design = design,
                        )
                    }
                }

                // Macro Controls Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(style.cardCorner),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, style.border),
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Text(
                            text = if (design == PresetDesign.GABBER) "EARLY HARDCORE" else "Makro-Regler",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = style.accent,
                        )
                        Spacer(modifier = Modifier.height(spacing.small))

                        // Bass Macro
                        Text(text = "Bass: ${String.format("%+.1f", settings.macroBassDb)} dB", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = settings.macroBassDb,
                            onValueChange = onMacroBassChanged,
                            valueRange = -6f..6f,
                            colors = SliderDefaults.colors(thumbColor = style.accent, activeTrackColor = style.accent),
                        )

                        // Punch Macro
                        Text(
                            text = "Punch: ${String.format("%+.1f", settings.macroPunchDb)} dB",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Slider(
                            value = settings.macroPunchDb,
                            onValueChange = onMacroPunchChanged,
                            valueRange = -6f..6f,
                            colors = SliderDefaults.colors(thumbColor = style.secondaryAccent, activeTrackColor = style.secondaryAccent),
                        )

                        // Härte Macro
                        Text(
                            text = "Härte: ${String.format("%+.1f", settings.macroHaerteDb)} dB",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Slider(
                            value = settings.macroHaerteDb,
                            onValueChange = onMacroHaerteChanged,
                            valueRange = -2f..2f,
                            colors = SliderDefaults.colors(thumbColor = style.accent, activeTrackColor = style.accent),
                        )
                    }
                }

                // Dynamic Equalizer Bands Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(style.cardCorner),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, style.border),
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
                                val bandColor = style.bandColors[band.index % style.bandColors.size]
                                Slider(
                                    value = gainDb,
                                    onValueChange = { newGain -> onBandGainChanged(band.index, newGain) },
                                    valueRange = band.minGainDb..band.maxGainDb,
                                    colors = SliderDefaults.colors(thumbColor = bandColor, activeTrackColor = bandColor),
                                )
                            }
                        }
                        EqualizerCurve(bands = bands, gains = settings.bandGainsDb, style = style)
                    }
                }
                val clippingRisk = headroom.isClippingRisk && settings.masterEnabled
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(style.cardCorner),
                    colors = CardDefaults.cardColors(containerColor = style.warningBackground),
                    border = BorderStroke(1.dp, if (clippingRisk) style.accent else style.border),
                ) {
                    Row(
                        modifier = Modifier.padding(spacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (clippingRisk) Icons.Default.Warning else Icons.Default.Info,
                            contentDescription = null,
                            tint = style.warningText,
                        )
                        Spacer(modifier = Modifier.width(spacing.small))
                        Column {
                            Text(
                                text =
                                    if (clippingRisk) {
                                        when (design) {
                                            PresetDesign.TERRORCORE -> "DANGER!"
                                            PresetDesign.UPTEMPO_HARDCORE -> "WARNING! SYSTEM OVERLOAD!"
                                            PresetDesign.HARD_DANCE -> "ATTENTION!"
                                            else -> "WARNING!"
                                        }
                                    } else {
                                        "SAFETY FIRST!"
                                    },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = style.warningText,
                            )
                            Text(
                                text =
                                    if (clippingRisk) {
                                        "Clipping-Gefahr: +${String.format("%.1f", headroom.maxPositiveGainDb)} dB. " +
                                            "Empfohlene Absenkung: ${String.format("%.1f", headroom.recommendedInputGainDb)} dB."
                                    } else if (settings.limiterEnabled) {
                                        "Clipping-Schutz bereit. Input-Gain: ${String.format("%+.1f", settings.inputGainDb)} dB."
                                    } else {
                                        "Limiter deaktiviert. Input-Gain: ${String.format("%+.1f", settings.inputGainDb)} dB."
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = style.warningText,
                            )
                        }
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
    style: EqualizerDesignStyle,
    design: PresetDesign,
) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        presets.chunked(3).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                row.forEachIndexed { columnIndex, preset ->
                    PresetCard(
                        preset = preset,
                        selected = preset.id == activePresetId,
                        onClick = { onPresetSelected(preset) },
                        onDuplicate = { onDuplicatePreset(preset) },
                        onRenameRequest = { onRenamePresetRequest(preset) },
                        onDeleteRequest = { onDeletePresetRequest(preset) },
                        modifier = Modifier.weight(1f),
                        style = style,
                        design = design,
                        index = rowIndex * 3 + columnIndex,
                    )
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// Horizontally scrollable row of selectable chips - simpler than PresetGrid's
// icon cards since a correction profile is just a name + source label, no icon.
// M5 (AutoEQ import) is what actually populates this beyond BuiltInCorrectionProfiles.None.
@Composable
private fun CorrectionProfileRow(
    profiles: List<CorrectionProfile>,
    activeProfileId: String,
    onProfileSelected: (CorrectionProfile) -> Unit,
    onExportProfile: (CorrectionProfile) -> Unit,
    spacing: Spacing,
    style: EqualizerDesignStyle,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        profiles.forEach { profile ->
            val selected = profile.id == activeProfileId
            val borderColor = if (selected) MaterialTheme.colorScheme.primary else style.border
            val containerColor =
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

            Surface(
                onClick = { onProfileSelected(profile) },
                shape = RoundedCornerShape(14.dp),
                color = containerColor,
                border = BorderStroke(1.dp, borderColor),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.small, vertical = spacing.extraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                        )
                        Text(
                            text = profile.sourceLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor,
                        )
                    }
                    // M5 "Import, Export und Teilen ... integrieren" - only for
                    // imported/custom profiles, mirroring PresetCard's own
                    // built-in-vs-custom distinction (built-ins have nothing
                    // device-specific worth exporting).
                    if (!profile.builtIn) {
                        IconButton(
                            onClick = { onExportProfile(profile) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Exportieren: ${profile.name}",
                                tint = contentColor,
                            )
                        }
                    }
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
    style: EqualizerDesignStyle,
    design: PresetDesign,
    index: Int,
) {
    val spacing = MaterialTheme.spacing
    val borderColor = if (selected) style.accent else style.border
    val containerColor =
        when {
            selected -> style.accent
            design == PresetDesign.HARD_DANCE -> style.bandColors[index % style.bandColors.size]
            else -> style.surfaceVariant
        }
    val contentColor =
        when {
            selected || design == PresetDesign.HARD_DANCE -> style.background
            else -> style.mutedText
        }
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(style.presetCorner),
        color = containerColor,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
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
                color = contentColor,
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

@Composable
private fun EqualizerCurve(
    bands: List<EqualizerBandCapabilities>,
    gains: Map<Int, Float>,
    style: EqualizerDesignStyle,
) {
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(style.presetCorner))
                .background(style.surfaceVariant),
    ) {
        val middle = size.height / 2f
        listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
            drawLine(
                color = style.border.copy(alpha = 0.55f),
                start = Offset(0f, size.height * fraction),
                end = Offset(size.width, size.height * fraction),
                strokeWidth = 1.dp.toPx(),
            )
        }

        val path = Path().apply { moveTo(0f, middle) }
        val count = bands.size.coerceAtLeast(1)
        bands.forEachIndexed { position, band ->
            val maximum = maxOf(kotlin.math.abs(band.minGainDb), kotlin.math.abs(band.maxGainDb)).coerceAtLeast(1f)
            val gain = gains[band.index] ?: 0f
            val x = size.width * (position + 1) / (count + 1)
            val y = middle - (gain / maximum).coerceIn(-1f, 1f) * size.height * 0.36f
            path.lineTo(x, y)
        }
        path.lineTo(size.width, middle)
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(listOf(style.accent, style.secondaryAccent, style.accent)),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
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
