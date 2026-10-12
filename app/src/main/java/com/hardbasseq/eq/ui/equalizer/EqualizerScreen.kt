package com.hardbasseq.eq.ui.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.AudioDeviceType
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.audio.AudioSession
import com.hardbasseq.eq.audio.EqualizerBandCapabilities
import com.hardbasseq.eq.audio.MAX_RETRY_ATTEMPTS
import com.hardbasseq.eq.audio.ProcessingSettings
import com.hardbasseq.eq.autoeq.AutoEqCatalogEntry
import com.hardbasseq.eq.context.SoundContext
import com.hardbasseq.eq.correction.CorrectionProfile
import com.hardbasseq.eq.dsp.CurveComposer
import com.hardbasseq.eq.dsp.HeadphoneComfortCurve
import com.hardbasseq.eq.dsp.HeadphonePower
import com.hardbasseq.eq.dsp.HeadroomCalculator
import com.hardbasseq.eq.dsp.HeadroomWarningLevel
import com.hardbasseq.eq.dsp.HeadroomWarningLevelCalculator
import com.hardbasseq.eq.dsp.LoudnessCompensationCurve
import com.hardbasseq.eq.dsp.SubsonicFilterCurve
import com.hardbasseq.eq.dsp.VolumeLevelMapper
import com.hardbasseq.eq.preset.BuiltInContextPresets
import com.hardbasseq.eq.preset.GenrePreset
import com.hardbasseq.eq.preset.Preset
import com.hardbasseq.eq.preset.PresetDesign
import com.hardbasseq.eq.preset.PresetIntensity
import com.hardbasseq.eq.preset.SoundGoal
import com.hardbasseq.eq.preset.TargetPoint
import com.hardbasseq.eq.ui.eq.EqStatus
import com.hardbasseq.eq.ui.eq.EqStatusKind
import com.hardbasseq.eq.ui.eq.EqStatusLine
import com.hardbasseq.eq.ui.main.CompareSide
import com.hardbasseq.eq.ui.main.CompareState
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
    customPresets: List<Preset>,
    genrePresets: List<GenrePreset>,
    intensities: List<PresetIntensity>,
    selectedGenreIntensity: Pair<GenrePreset, PresetIntensity>?,
    activeCorrectionProfile: CorrectionProfile,
    allCorrectionProfiles: List<CorrectionProfile>,
    suggestedCorrectionProfile: AutoEqCatalogEntry?,
    effectiveHeadphoneAcoustics: Boolean,
    onOpenServices: () -> Unit,
    onRenameRoute: (String) -> Unit = {},
    activeGoal: SoundGoal,
    onGoalSelected: (SoundGoal) -> Unit,
    pathLabel: String?,
    compare: CompareState?,
    onStartCompare: () -> Unit,
    onCompareSide: (CompareSide) -> Unit,
    onEndCompare: () -> Unit,
    headphonePower: HeadphonePower,
    activeContext: SoundContext?,
    currentLevelDb: Float,
    isDirty: Boolean,
    onMasterToggled: (Boolean) -> Unit,
    onOpenSourcePicker: () -> Unit,
    onBypassToggled: (Boolean) -> Unit,
    onPresetSelected: (Preset) -> Unit,
    onGenreIntensitySelected: (GenrePreset, PresetIntensity) -> Unit,
    onCorrectionProfileSelected: (CorrectionProfile) -> Unit,
    onImportCorrectionProfileRequested: () -> Unit,
    onExportCorrectionProfile: (CorrectionProfile) -> Unit,
    onAcceptSuggestedCorrectionProfile: () -> Unit,
    onDismissSuggestedCorrectionProfile: () -> Unit,
    onHeadphoneAcousticsChanged: (Boolean) -> Unit,
    onHeadphonePowerChanged: (HeadphonePower) -> Unit,
    onContextModeChanged: (SoundContext?) -> Unit,
    onLoudnessCompensationChanged: (Boolean) -> Unit,
    onSubsonicFilterChanged: (Boolean) -> Unit,
    onVirtualBassChanged: (Boolean) -> Unit,
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
    audioSessions: List<AudioSession> = emptyList(),
    activeAudioSession: AudioSession? = null,
    onSelectAudioSession: (AudioSession) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val design = PresetDesign.forPreset(activePreset)
    val style = styleFor(design)
    // M3/M4: headroom from the combined correction+voicing curve, not just the
    // highest of the discrete post-mapping band gains - see HeadroomCalculator.
    // Mirrors MainViewModel.recalculateBandGains()'s own combination (including
    // the HeadphoneComfortCurve and LoudnessCompensationCurve fold-ins while
    // effectiveHeadphoneAcoustics is on) so the banner here always agrees with
    // the inputGainDb the engine actually applied.
    val headphoneCurve = if (effectiveHeadphoneAcoustics) HeadphoneComfortCurve.curve(headphonePower.bassDb) else emptyList()
    val loudnessCurve =
        if (effectiveHeadphoneAcoustics) {
            LoudnessCompensationCurve.forLevel(currentLevelDb = currentLevelDb, maxBoostDb = headphonePower.loudnessMaxDb)
        } else {
            emptyList()
        }
    // Subsonic high-pass + preset loudness compensation, same as MainViewModel.contextCurve().
    val contextCurve =
        CurveComposer.combine(
            listOf(
                SubsonicFilterCurve.forCutoff(settings.subsonicCutoffHz),
                if (effectiveHeadphoneAcoustics) {
                    emptyList()
                } else {
                    VolumeLevelMapper.compensationCurve(currentLevelDb, settings.loudnessMaxBoostDb)
                },
            ),
        )
    val combinedCurve =
        CurveComposer.combine(
            listOf(
                activeCorrectionProfile.curve,
                activePreset.targetCurve,
                headphoneCurve,
                loudnessCurve,
                activeGoal.curve,
                activeContext?.let { BuiltInContextPresets.defaultFor(it).targetCurve }.orEmpty(),
                contextCurve,
            ),
        )
    val headroom =
        HeadroomCalculator.fromCombinedCurve(
            combinedCurve = combinedCurve,
            macroBassDb = settings.macroBassDb,
            macroPunchDb = settings.macroPunchDb,
            macroHaerteDb = settings.macroHaerteDb,
        )

    val warningLevel = HeadroomWarningLevelCalculator.fromHeadroom(headroom)
    val clippingRisk = headroom.isClippingRisk && settings.masterEnabled
    val statusKind = EqStatus.kind(state, settings)
    // The hero draws what the EQ is actually set to: the applied band gains (preset,
    // macros and manual band edits included). Before the device has reported its bands
    // there are none, so it falls back to the combined target curve.
    val bandPoints =
        bands
            .sortedBy { it.centerFreqHz }
            .map { band -> TargetPoint(band.centerFreqHz.toFloat(), settings.bandGainsDb[band.index] ?: 0f) }
    val curvePoints = if (bandPoints.isEmpty()) combinedCurve else bandPoints
    val curvePeak = CurveGeometry.strongestChange(curvePoints)
    val curveDescription =
        if (curvePeak != null) {
            stringResource(
                R.string.eq_curve_description,
                String.format("%+.1f", curvePeak.gainDb),
                CurveGeometry.formatFrequency(curvePeak.frequencyHz),
            )
        } else {
            stringResource(R.string.eq_curve_flat)
        }

    MaterialTheme(colorScheme = style.colorScheme) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .neonBackdrop(style),
        ) {
            style.backgroundRes?.let { backgroundRes ->
                // The genre artwork stays as a faint texture; the glow and the curve carry the look.
                Image(
                    painter = painterResource(backgroundRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.12f,
                    modifier = Modifier.matchParentSize(),
                )
            }
            CompositionLocalProvider(LocalContentColor provides style.text) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    // Header: route, app name, genre, source picker and the master switch.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            NeonLabel(text = stringResource(R.string.route_label, route.name), color = style.secondaryAccent)
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = style.text,
                            )
                            NeonLabel(text = style.name, color = style.accent)
                        }
                        // Independent of the master switch - that one only offers the source
                        // picker when flipped on with nothing attached yet (see
                        // MainViewModel.setMasterEnabled).
                        IconButton(onClick = onOpenSourcePicker) {
                            Icon(
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = stringResource(R.string.eq_pick_source),
                                tint = style.mutedText,
                            )
                        }
                        NeonPowerButton(
                            checked = settings.masterEnabled,
                            onCheckedChange = onMasterToggled,
                            description = stringResource(R.string.eq_master_switch),
                            style = style,
                        )
                    }

                    // Engine status - docs/STATE_MACHINE.md §5 UI-status mapping.
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                        EqStatusLine(
                            kind = statusKind,
                            pathLabel = pathLabel,
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(R.string.eq_playback_only_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = style.mutedText,
                        )
                        AudioSourceList(
                            sessions = audioSessions,
                            activeSession = activeAudioSession,
                            onSelect = onSelectAudioSession,
                        )
                        // "Fehler: konkrete nächste Handlung anbieten" (roadmap-2026.md M1) -
                        // resets the retry budget and re-attempts the last known session.
                        AnimatedVisibility(visible = state is AudioEngineState.Error) {
                            OutlinedButton(onClick = onRetryAttach) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }

                    // Hero: the curve, its limiting tag and the Original/EQ comparison.
                    NeonPanel(style = style, glow = true, contentPadding = 14.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                NeonLabel(text = stringResource(R.string.compare_current_preset), color = style.mutedText)
                                Text(
                                    text =
                                        if (isDirty) {
                                            stringResource(R.string.compare_preset_custom, activePreset.name)
                                        } else {
                                            activePreset.name
                                        },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = style.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (settings.masterEnabled) {
                                when (warningLevel) {
                                    HeadroomWarningLevel.HEAVY_LIMITING ->
                                        NeonTag(text = stringResource(R.string.tag_limiting_heavy), color = NeonHotColor)
                                    HeadroomWarningLevel.OCCASIONAL_LIMITING ->
                                        NeonTag(text = stringResource(R.string.tag_limiting_sometimes), color = LimiterAmber)
                                    HeadroomWarningLevel.SUFFICIENT -> Unit
                                }
                            }
                            if (isDirty) {
                                TextButton(onClick = onResetToActivePreset) { Text(stringResource(R.string.compare_reset)) }
                            }
                        }
                        NeonCurve(
                            points = curvePoints,
                            nodes = bandPoints,
                            style = style,
                            hot = settings.masterEnabled && warningLevel == HeadroomWarningLevel.HEAVY_LIMITING,
                            description = curveDescription,
                            modifier = Modifier.padding(vertical = spacing.small),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NeonChip(
                                text = stringResource(R.string.compare_original),
                                selected = compare?.side == CompareSide.ORIGINAL,
                                onClick = { onCompareSide(CompareSide.ORIGINAL) },
                                style = style,
                            )
                            NeonChip(
                                text = stringResource(R.string.compare_eq),
                                selected = compare == null || compare.side == CompareSide.EQ,
                                onClick = { if (compare == null) onStartCompare() else onCompareSide(CompareSide.EQ) },
                                style = style,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (compare != null) {
                                TextButton(onClick = onEndCompare) { Text(stringResource(R.string.compare_done)) }
                            }
                        }
                        HoldToCompareButton(
                            modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
                            onPress = { onCompareSide(CompareSide.ORIGINAL) },
                            onRelease = onEndCompare,
                            style = style,
                        )
                        Text(
                            text =
                                if (compare == null) {
                                    stringResource(R.string.compare_hint_idle)
                                } else {
                                    stringResource(R.string.compare_hint_active, String.format("%+.1f", compare.eqLevelDeltaDb))
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = style.mutedText,
                            modifier = Modifier.padding(top = spacing.small),
                        )
                    }

                    // M4: applied input gain, limiter status and threshold, warning levels and
                    // MBC transparency - always visible, as LED chains plus the exact values.
                    // All of it is derived from the settings; nothing here is a measurement.
                    NeonPanel(style = style) {
                        NeonSectionHeader(
                            title = stringResource(R.string.meters_title),
                            style = style,
                            info = stringResource(R.string.signal_explain),
                        )
                        if (statusKind != EqStatusKind.ACTIVE) {
                            Text(
                                text = stringResource(R.string.signal_not_confirmed),
                                style = MaterialTheme.typography.bodySmall,
                                color = style.mutedText,
                            )
                            Spacer(modifier = Modifier.height(spacing.small))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            LedMeter(
                                label = stringResource(R.string.meter_boost),
                                value = String.format("%+.1f", headroom.maxPositiveGainDb.coerceAtLeast(0f)),
                                fraction = headroom.maxPositiveGainDb / METER_RANGE_DB,
                                scale = LedScale.LEVEL,
                                style = style,
                            )
                            LedMeter(
                                label = stringResource(R.string.meter_input),
                                value = String.format("%+.1f", settings.inputGainDb),
                                fraction = -settings.inputGainDb / METER_RANGE_DB,
                                scale = LedScale.REDUCTION,
                                style = style,
                            )
                            LedMeter(
                                label = stringResource(R.string.meter_limit),
                                value =
                                    stringResource(
                                        when (warningLevel) {
                                            HeadroomWarningLevel.SUFFICIENT -> R.string.meter_limit_low
                                            HeadroomWarningLevel.OCCASIONAL_LIMITING -> R.string.meter_limit_some
                                            HeadroomWarningLevel.HEAVY_LIMITING -> R.string.meter_limit_heavy
                                        },
                                    ),
                                fraction =
                                    when (warningLevel) {
                                        HeadroomWarningLevel.SUFFICIENT -> 0.07f
                                        HeadroomWarningLevel.OCCASIONAL_LIMITING -> 0.5f
                                        HeadroomWarningLevel.HEAVY_LIMITING -> 1f
                                    },
                                scale = LedScale.HOT,
                                style = style,
                            )
                        }
                        Spacer(modifier = Modifier.height(spacing.small))
                        Text(
                            text =
                                if (settings.limiterEnabled) {
                                    stringResource(R.string.signal_limiter_on, String.format("%.1f", settings.limiterThresholdDb))
                                } else {
                                    stringResource(R.string.signal_limiter_off)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = style.text,
                        )
                        Text(
                            text =
                                if (settings.mbcEnabled) {
                                    stringResource(R.string.signal_mbc_on)
                                } else {
                                    stringResource(R.string.signal_mbc_off)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = style.mutedText,
                        )
                        val (warningText, warningColor) =
                            when (warningLevel) {
                                HeadroomWarningLevel.SUFFICIENT ->
                                    stringResource(R.string.headroom_ok) to style.mutedText
                                HeadroomWarningLevel.OCCASIONAL_LIMITING ->
                                    stringResource(R.string.headroom_limiter_sometimes) to LimiterAmber
                                HeadroomWarningLevel.HEAVY_LIMITING ->
                                    stringResource(R.string.headroom_limiter_heavy) to NeonHotColor
                            }
                        Text(
                            text = warningText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = warningColor,
                        )
                        Text(
                            text =
                                if (clippingRisk) {
                                    stringResource(
                                        R.string.clipping_danger,
                                        String.format("%.1f", headroom.maxPositiveGainDb),
                                        String.format("%.1f", headroom.recommendedInputGainDb),
                                    )
                                } else if (settings.limiterEnabled) {
                                    stringResource(R.string.clipping_protected, String.format("%+.1f", settings.inputGainDb))
                                } else {
                                    stringResource(R.string.clipping_limiter_off, String.format("%+.1f", settings.inputGainDb))
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (clippingRisk) NeonHotColor else style.mutedText,
                        )
                        Text(
                            text = stringResource(R.string.meters_estimate_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = style.mutedText,
                            modifier = Modifier.padding(top = spacing.extraSmall),
                        )
                    }

                    // No-session guidance: the foreground service listens for new sessions in
                    // the background, but a player that was already running before it started
                    // (or, per the M0 spike, some devices) never announces one. Explain what to
                    // try instead of leaving the user staring at "waiting for an audio session".
                    // Detached and Listening both mean "no session" - see AudioEngineState.
                    AnimatedVisibility(visible = state is AudioEngineState.Detached || state is AudioEngineState.Listening) {
                        NeonPanel(style = style) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = stringResource(R.string.hint_icon),
                                    tint = style.secondaryAccent,
                                )
                                Spacer(modifier = Modifier.width(spacing.small))
                                Column {
                                    Text(
                                        text = stringResource(R.string.no_session_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = style.text,
                                    )
                                    Text(
                                        text = stringResource(R.string.no_session_text),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = style.mutedText,
                                    )
                                }
                            }
                        }
                    }

                    // Genre x intensity: genre cards with their own mini curve, then the
                    // intensity as one stepped slider.
                    NeonPanel(style = style) {
                        NeonSectionHeader(
                            title = stringResource(R.string.genre_title),
                            style = style,
                            trailing = {
                                // M2: only once a band/macro edit has actually moved away from
                                // what activePreset alone would produce.
                                AnimatedVisibility(visible = isDirty) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = onResetToActivePreset) {
                                            Text(stringResource(R.string.compare_reset))
                                        }
                                        TextButton(onClick = onSaveAsNewRequest) {
                                            Text(stringResource(R.string.action_save))
                                        }
                                    }
                                }
                            },
                        )
                        GenreCards(
                            genres = genrePresets,
                            selected = selectedGenreIntensity?.first,
                            onSelect = { genre ->
                                // Keep the chosen intensity when the new genre has one, otherwise
                                // land on MODERATE - picking another genre should not silently
                                // reset an already chosen "Aggressive".
                                val intensity =
                                    selectedGenreIntensity?.second?.takeIf { genre.allowsIntensity } ?: PresetIntensity.MODERATE
                                onGenreIntensitySelected(genre, intensity)
                            },
                            style = style,
                        )
                        Spacer(modifier = Modifier.height(spacing.medium))
                        val selectedGenre = selectedGenreIntensity?.first
                        val selectedIntensity = selectedGenreIntensity?.second
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            NeonLabel(
                                text = stringResource(R.string.intensity_title),
                                color = style.mutedText,
                                modifier = Modifier.weight(1f),
                            )
                            if (selectedGenre != null && selectedIntensity != null && selectedGenre.allowsIntensity) {
                                NeonLabel(text = selectedIntensity.displayName, color = style.accent)
                            }
                        }
                        IntensityStepper(
                            labels = intensities.map { it.displayName },
                            selectedIndex = selectedIntensity?.let { intensities.indexOf(it) }?.takeIf { it >= 0 },
                            enabled = selectedGenre?.allowsIntensity == true,
                            onSelect = { index -> selectedGenre?.let { onGenreIntensitySelected(it, intensities[index]) } },
                            style = style,
                        )
                        if (intensities.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = intensities.first().displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = style.mutedText,
                                )
                                Text(
                                    text = intensities.last().displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = style.mutedText,
                                )
                            }
                        }
                        if (customPresets.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(spacing.medium))
                            NeonLabel(text = stringResource(R.string.presets_custom), color = style.accent)
                            Spacer(modifier = Modifier.height(spacing.small))
                            PresetGrid(
                                presets = customPresets,
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

                    SoundGoalCard(active = activeGoal, onSelect = onGoalSelected, style = style)

                    // Listening context (car / Bluetooth speaker) plus the three stages it
                    // uses as tiles. A mode is an overlay on the active genre preset
                    // (MainViewModel.activeContext), so it stays on when the genre changes; a
                    // detected car/speaker route turns it on by itself.
                    NeonPanel(style = style) {
                        NeonSectionHeader(
                            title = stringResource(R.string.context_title),
                            style = style,
                            info =
                                listOf(
                                    stringResource(R.string.context_default_desc),
                                    stringResource(R.string.context_loudness_desc),
                                    if (settings.subsonicCutoffHz > 0f) {
                                        stringResource(R.string.context_subsonic_on, settings.subsonicCutoffHz.toInt())
                                    } else {
                                        stringResource(R.string.context_subsonic_off)
                                    },
                                    stringResource(R.string.context_virtual_bass_desc),
                                ).joinToString("\n"),
                        )
                        val detectedRoute =
                            when (route.type) {
                                AudioDeviceType.CAR -> stringResource(R.string.context_car_detected, route.name)
                                AudioDeviceType.BLUETOOTH_SPEAKER -> stringResource(R.string.context_speaker_detected, route.name)
                                else -> null
                            }
                        if (detectedRoute != null) {
                            NeonLabel(text = detectedRoute, color = style.secondaryAccent)
                        }
                        Spacer(modifier = Modifier.height(spacing.small))
                        ContextModeChips(
                            activeContext = activeContext,
                            onContextModeChanged = onContextModeChanged,
                            accent = style.accent,
                            border = style.border,
                        )
                        Spacer(modifier = Modifier.height(spacing.medium))
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                            SignalModuleTile(
                                title = stringResource(R.string.module_loudness),
                                detail = stringResource(if (settings.loudnessMaxBoostDb > 0f) R.string.module_on else R.string.module_off),
                                checked = settings.loudnessMaxBoostDb > 0f,
                                onCheckedChange = onLoudnessCompensationChanged,
                                style = style,
                                modifier = Modifier.weight(1f),
                            )
                            SignalModuleTile(
                                title = stringResource(R.string.module_subsonic),
                                detail =
                                    if (settings.subsonicCutoffHz > 0f) {
                                        "< ${settings.subsonicCutoffHz.toInt()} Hz"
                                    } else {
                                        stringResource(R.string.module_off)
                                    },
                                checked = settings.subsonicCutoffHz > 0f,
                                onCheckedChange = onSubsonicFilterChanged,
                                style = style,
                                modifier = Modifier.weight(1f),
                            )
                            SignalModuleTile(
                                title = stringResource(R.string.module_virtual_bass),
                                detail = stringResource(if (settings.virtualBassMix > 0f) R.string.module_on else R.string.module_off),
                                checked = settings.virtualBassMix > 0f,
                                onCheckedChange = onVirtualBassChanged,
                                style = style,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // Sound profiles: which preset and correction belong to this output.
                    NeonPanel(style = style) {
                        NeonSectionHeader(
                            title = stringResource(R.string.profiles_title),
                            style = style,
                            info =
                                stringResource(R.string.profiles_intro) + "\n" +
                                    stringResource(
                                        R.string.profiles_saved_for_output,
                                        activePreset.name,
                                        if (activeCorrectionProfile.id == "correction_none") {
                                            stringResource(R.string.profiles_correction_none)
                                        } else {
                                            activeCorrectionProfile.localizedName()
                                        },
                                    ),
                        )
                        Text(
                            text = stringResource(R.string.profiles_output, route.name),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = style.text,
                        )
                        var showRenameRoute by rememberSaveable { mutableStateOf(false) }
                        Row {
                            TextButton(onClick = onOpenServices) { Text(stringResource(R.string.profiles_open_services)) }
                            TextButton(onClick = { showRenameRoute = true }) { Text(stringResource(R.string.route_rename)) }
                        }
                        if (showRenameRoute) {
                            RenameRouteDialog(
                                currentName = route.name,
                                onConfirm = {
                                    showRenameRoute = false
                                    onRenameRoute(it)
                                },
                                onDismiss = { showRenameRoute = false },
                            )
                        }
                    }

                    // M3 "Mein Kopfhörer": which headphone/speaker correction curve is combined
                    // with the voicing preset.
                    NeonPanel(style = style) {
                        NeonSectionHeader(
                            title = stringResource(R.string.correction_title),
                            style = style,
                            trailing = {
                                // M5: opens the SAF file picker; parsing and preview happen in
                                // MainViewModel once MainScreen has read the picked file.
                                TextButton(onClick = onImportCorrectionProfileRequested) {
                                    Text(stringResource(R.string.action_import))
                                }
                            },
                        )

                        // A suggestion only, never applied silently - see
                        // MainViewModel.updateSuggestedCorrectionProfile()/AutoEqCatalogMatcher.
                        AnimatedVisibility(visible = suggestedCorrectionProfile != null) {
                            if (suggestedCorrectionProfile != null) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = spacing.small)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(style.accent.copy(alpha = 0.12f))
                                            .padding(spacing.small),
                                ) {
                                    Text(
                                        text = stringResource(R.string.correction_detected, suggestedCorrectionProfile.displayName),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = style.text,
                                    )
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.correction_suggestion,
                                                localizedSourceLabel(suggestedCorrectionProfile.profile.sourceLabel),
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = style.mutedText,
                                    )
                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                        TextButton(onClick = onDismissSuggestedCorrectionProfile) {
                                            Text(stringResource(R.string.action_not_now))
                                        }
                                        TextButton(onClick = onAcceptSuggestedCorrectionProfile) {
                                            Text(stringResource(R.string.action_apply))
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

                        // Folds HeadphoneComfortCurve into the applied band-EQ curve
                        // (MainViewModel.combinedCurve()) - a fixed bass/presence tilt, not a
                        // literal crossfeed simulation, which the session-attached system
                        // effects cannot run. Defaults from the route type until overridden here.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = spacing.small)) {
                                Text(
                                    text = stringResource(R.string.headphone_mode_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = style.text,
                                )
                                Text(
                                    text = stringResource(R.string.headphone_mode_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = style.mutedText,
                                )
                            }
                            val headphoneModeDescription = stringResource(R.string.headphone_mode_title)
                            Switch(
                                modifier = Modifier.semantics { contentDescription = headphoneModeDescription },
                                checked = effectiveHeadphoneAcoustics,
                                onCheckedChange = onHeadphoneAcousticsChanged,
                            )
                        }
                    }

                    if (effectiveHeadphoneAcoustics) {
                        HeadphonePowerCard(
                            power = headphonePower,
                            onPowerChanged = onHeadphonePowerChanged,
                            style = style,
                        )
                    }

                    var fineTuneOpen by rememberSaveable { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.fine_tune_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = style.text,
                            )
                            Text(
                                text = stringResource(R.string.fine_tune_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = style.mutedText,
                            )
                        }
                        TextButton(onClick = { fineTuneOpen = !fineTuneOpen }) {
                            Text(stringResource(if (fineTuneOpen) R.string.fine_tune_hide else R.string.fine_tune_show))
                        }
                    }
                    if (fineTuneOpen) {
                        NeonPanel(style = style) {
                            Text(
                                text = if (design == PresetDesign.GABBER) "EARLY HARDCORE" else stringResource(R.string.macro_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = style.accent,
                            )
                            Spacer(modifier = Modifier.height(spacing.small))

                            val bassLabel = stringResource(R.string.macro_bass_label, String.format("%+.1f", settings.macroBassDb))
                            Text(text = bassLabel, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = stringResource(R.string.macro_bass_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = style.mutedText,
                            )
                            Slider(
                                modifier = Modifier.semantics { contentDescription = bassLabel },
                                value = settings.macroBassDb,
                                onValueChange = onMacroBassChanged,
                                valueRange = -6f..6f,
                                colors = SliderDefaults.colors(thumbColor = style.accent, activeTrackColor = style.accent),
                            )

                            val punchLabel = stringResource(R.string.macro_punch_label, String.format("%+.1f", settings.macroPunchDb))
                            Text(text = punchLabel, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = stringResource(R.string.macro_punch_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = style.mutedText,
                            )
                            Slider(
                                modifier = Modifier.semantics { contentDescription = punchLabel },
                                value = settings.macroPunchDb,
                                onValueChange = onMacroPunchChanged,
                                valueRange = -6f..6f,
                                colors =
                                    SliderDefaults.colors(
                                        thumbColor = style.secondaryAccent,
                                        activeTrackColor = style.secondaryAccent,
                                    ),
                            )

                            val haerteLabel =
                                stringResource(R.string.macro_haerte_label, String.format("%+.1f", settings.macroHaerteDb))
                            Text(text = haerteLabel, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = stringResource(R.string.macro_haerte_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = style.mutedText,
                            )
                            Slider(
                                modifier = Modifier.semantics { contentDescription = haerteLabel },
                                value = settings.macroHaerteDb,
                                onValueChange = onMacroHaerteChanged,
                                valueRange = -2f..2f,
                                colors = SliderDefaults.colors(thumbColor = style.accent, activeTrackColor = style.accent),
                            )
                        }

                        NeonPanel(style = style) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.bands_title, bands.size),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = stringResource(R.string.bypass_label), style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.width(spacing.extraSmall))
                                    val bypassDescription = stringResource(R.string.bypass_label)
                                    Switch(
                                        modifier = Modifier.semantics { contentDescription = bypassDescription },
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
                                        Text(
                                            text = freqLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(text = "${String.format("%+.1f", gainDb)} dB", style = MaterialTheme.typography.bodySmall)
                                    }
                                    val bandColor = style.bandColors[band.index % style.bandColors.size]
                                    Slider(
                                        modifier = Modifier.semantics { contentDescription = freqLabel },
                                        value = gainDb,
                                        onValueChange = { newGain -> onBandGainChanged(band.index, newGain) },
                                        valueRange = band.minGainDb..band.maxGainDb,
                                        colors = SliderDefaults.colors(thumbColor = bandColor, activeTrackColor = bandColor),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Size of the LED meters' scale: 12 dB fills a chain.
private const val METER_RANGE_DB = 12f

// Amber for "the limiter works now and then", between the calm and the red state.
private val LimiterAmber = Color(0xFFFFB020)

// Genre picker: one card per genre with its own curve, scrolling sideways. Replaces the
// dropdown so every genre is visible and one tap away.
@Composable
private fun GenreCards(
    genres: List<GenrePreset>,
    selected: GenrePreset?,
    onSelect: (GenrePreset) -> Unit,
    style: EqualizerDesignStyle,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        genres.forEach { genre ->
            val isSelected = genre.id == selected?.id
            Surface(
                onClick = { onSelect(genre) },
                modifier =
                    Modifier
                        .width(128.dp)
                        .height(92.dp)
                        .semantics { contentDescription = genre.displayName },
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) style.accent.copy(alpha = 0.14f) else style.text.copy(alpha = 0.04f),
                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) style.accent else style.border.copy(alpha = 0.6f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    MiniCurve(
                        points = genre.base.targetCurve,
                        color = if (isSelected) style.accent else style.secondaryAccent,
                        modifier = Modifier.fillMaxWidth().height(30.dp),
                    )
                    Text(
                        text = genre.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = style.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                            text = profile.localizedName(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                        )
                        Text(
                            text = profile.localizedSource(),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor,
                        )
                        profile.deviceName?.let { device ->
                            Text(
                                text = stringResource(R.string.profile_for_device, device),
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor,
                            )
                        }
                    }
                    // M5 "Import, Export und Teilen ... integrieren" - only for
                    // imported/custom profiles, mirroring PresetCard's own
                    // built-in-vs-custom distinction (built-ins have nothing
                    // device-specific worth exporting).
                    if (!profile.builtIn) {
                        IconButton(
                            onClick = { onExportProfile(profile) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.profile_export, profile.localizedName()),
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
                        contentDescription = stringResource(R.string.preset_more_actions, preset.name),
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
                        text = { Text(stringResource(R.string.preset_duplicate)) },
                        onClick = {
                            showMenu = false
                            onDuplicate()
                        },
                    )
                    if (!preset.metadata.builtIn) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.preset_rename)) },
                            onClick = {
                                showMenu = false
                                onRenameRequest()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.preset_delete)) },
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
    return stringResource(R.string.retry_countdown, remainingSeconds, state.attempt, MAX_RETRY_ATTEMPTS)
}

private fun secondsUntil(millis: Long): Long = ((millis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)

// "Kopfhörer-Power": Bass, Dynamik und Loudness im Kopfhörer-Modus einstellbar,
// dazu die Presets "Standard" (altes Verhalten) und "Knall".
@Composable
private fun HeadphonePowerCard(
    power: HeadphonePower,
    onPowerChanged: (HeadphonePower) -> Unit,
    style: EqualizerDesignStyle,
) {
    val spacing = MaterialTheme.spacing
    NeonPanel(style = style) {
        Column {
            Text(
                text = stringResource(R.string.hp_power_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text =
                    stringResource(R.string.hp_power_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(spacing.small))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                OutlinedButton(onClick = { onPowerChanged(HeadphonePower.DEFAULT) }) { Text(stringResource(R.string.hp_power_standard)) }
                Button(onClick = { onPowerChanged(HeadphonePower.KNALL) }) { Text(stringResource(R.string.hp_power_knall)) }
            }
            Spacer(modifier = Modifier.height(spacing.small))
            val bassLabel = stringResource(R.string.hp_power_bass, "%.1f".format(power.bassDb))
            Text(text = bassLabel, style = MaterialTheme.typography.bodyMedium)
            Slider(
                modifier = Modifier.semantics { contentDescription = bassLabel },
                value = power.bassDb,
                onValueChange = { onPowerChanged(power.copy(bassDb = it)) },
                valueRange = 0f..HeadphonePower.MAX_BASS_DB,
            )
            val loudnessLabel =
                if (power.loudnessMaxDb > 0f) {
                    stringResource(R.string.hp_power_loudness_on, "%.1f".format(power.loudnessMaxDb))
                } else {
                    stringResource(R.string.hp_power_loudness_off)
                }
            Text(text = loudnessLabel, style = MaterialTheme.typography.bodyMedium)
            Slider(
                modifier = Modifier.semantics { contentDescription = loudnessLabel },
                value = power.loudnessMaxDb,
                onValueChange = { onPowerChanged(power.copy(loudnessMaxDb = it)) },
                valueRange = 0f..HeadphonePower.MAX_LOUDNESS_DB,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.hp_power_dynamics), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(R.string.hp_power_dynamics_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val dynamicsDescription = stringResource(R.string.hp_power_dynamics)
                Switch(
                    modifier = Modifier.semantics { contentDescription = dynamicsDescription },
                    checked = power.easeDynamics,
                    onCheckedChange = { onPowerChanged(power.copy(easeDynamics = it)) },
                )
            }
            Text(
                text = stringResource(R.string.hp_power_player_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Preset name, reset, and the Original/EQ comparison. While comparing, both sides are matched in
// loudness (the louder one is turned down by an estimate), so a mere volume difference does not
// pass for a better sound.
// Gives the current output device a name of the user's own ("My headphones"); empty = reset.
@Composable
private fun RenameRouteDialog(
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
private fun HoldToCompareButton(
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
                .heightIn(min = 48.dp)
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
        shape = RoundedCornerShape(style.cardCorner),
        color = if (pressed) style.accent else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, style.border),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(if (pressed) R.string.compare_hold_active else R.string.compare_hold),
                color = if (pressed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
