package com.hardbasseq.eq.ui.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Equalizer
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.AudioDeviceType
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.audio.AudioSession
import com.hardbasseq.eq.audio.DeviceKind
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
import com.hardbasseq.eq.dsp.VirtualBassApproxCurve
import com.hardbasseq.eq.dsp.VolumeLevelMapper
import com.hardbasseq.eq.preset.BuiltInContextPresets
import com.hardbasseq.eq.preset.GenrePreset
import com.hardbasseq.eq.preset.Preset
import com.hardbasseq.eq.preset.PresetIntensity
import com.hardbasseq.eq.preset.SoundGoal
import com.hardbasseq.eq.preset.TargetPoint
import com.hardbasseq.eq.ui.eq.EqStatus
import com.hardbasseq.eq.ui.eq.EqStatusKind
import com.hardbasseq.eq.ui.eq.EqStatusLine
import com.hardbasseq.eq.ui.main.CompareSide
import com.hardbasseq.eq.ui.main.CompareState
import com.hardbasseq.eq.ui.theme.SonicNumerals
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
    isOwnPlayerSession: Boolean = true,
    deviceKindPrompt: AudioRoute? = null,
    onAnswerDeviceKind: (DeviceKind) -> Unit = {},
    onDismissDeviceKindPrompt: () -> Unit = {},
    preEqActive: Boolean = false,
    onHighResolutionEqChanged: ((Boolean) -> Unit)? = null,
    onSelectAudioSession: (AudioSession) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val style = SonicStyle
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
                if (!isOwnPlayerSession && preEqActive) VirtualBassApproxCurve.forMix(settings.virtualBassMix) else emptyList(),
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

    var selectedBand by rememberSaveable { mutableStateOf<Int?>(null) }
    val graphBands =
        bands.map { band ->
            GraphBand(
                index = band.index,
                frequencyHz = band.centerFreqHz.toFloat(),
                gainDb = settings.bandGainsDb[band.index] ?: 0f,
                minGainDb = band.minGainDb,
                maxGainDb = band.maxGainDb,
            )
        }
    val processing = settings.masterEnabled && !settings.bypass
    val heavyLimiting = settings.masterEnabled && warningLevel == HeadroomWarningLevel.HEAVY_LIMITING

    CompositionLocalProvider(LocalContentColor provides style.text) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(style.background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.medium, vertical = spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
            // Header: what sound is set, for which output, and the master switch.
            Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Caption(text = stringResource(R.string.route_label, route.name), color = style.mutedText)
                        Text(
                            text =
                                if (isDirty) {
                                    stringResource(R.string.compare_preset_custom, activePreset.name)
                                } else {
                                    activePreset.name
                                },
                            style = MaterialTheme.typography.headlineSmall,
                            color = style.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                    PowerKey(
                        checked = settings.masterEnabled,
                        onCheckedChange = onMasterToggled,
                        description = stringResource(R.string.eq_master_switch),
                    )
                }

                // Engine status - docs/STATE_MACHINE.md §5 UI-status mapping.
                EqStatusLine(
                    kind = statusKind,
                    pathLabel = pathLabel,
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                // "Fehler: konkrete nächste Handlung anbieten" (roadmap-2026.md M1) -
                // resets the retry budget and re-attempts the last known session.
                AnimatedVisibility(visible = state is AudioEngineState.Error) {
                    OutlinedButton(onClick = onRetryAttach, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
                // No-session guidance: the foreground service listens for new sessions in
                // the background, but a player that was already running before it started
                // (or, per the M0 spike, some devices) never announces one. Explain what to
                // try instead of leaving the user staring at "waiting for an audio session".
                // Detached and Listening both mean "no session" - see AudioEngineState.
                AnimatedVisibility(visible = state is AudioEngineState.Detached || state is AudioEngineState.Listening) {
                    Notice {
                        Text(
                            text = stringResource(R.string.no_session_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = style.text,
                        )
                        Text(
                            text = stringResource(R.string.no_session_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = style.mutedText,
                        )
                    }
                }
                AudioSourceList(
                    sessions = audioSessions,
                    activeSession = activeAudioSession,
                    onSelect = onSelectAudioSession,
                )
                Text(
                    text = stringResource(R.string.eq_playback_only_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = style.mutedText,
                )
                onHighResolutionEqChanged?.let { onChanged ->
                    HighResolutionEqRow(
                        checked = settings.highResolutionEq,
                        active = preEqActive,
                        onCheckedChange = onChanged,
                    )
                }
            }

            deviceKindPrompt?.let { prompt ->
                DeviceKindCard(
                    deviceName = prompt.name,
                    onAnswer = onAnswerDeviceKind,
                    onDismiss = onDismissDeviceKindPrompt,
                )
            }

            // Hero: the response graph is the main control. Drag a point, or select it and
            // step it with − and +.
            Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    if (settings.masterEnabled) {
                        when (warningLevel) {
                            HeadroomWarningLevel.HEAVY_LIMITING ->
                                StatusTag(text = stringResource(R.string.tag_limiting_heavy), color = style.hot)
                            HeadroomWarningLevel.OCCASIONAL_LIMITING ->
                                StatusTag(text = stringResource(R.string.tag_limiting_sometimes), color = style.caution)
                            HeadroomWarningLevel.SUFFICIENT -> Unit
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // M2: only once a band/macro edit has actually moved away from what
                    // activePreset alone would produce.
                    AnimatedVisibility(visible = isDirty) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onResetToActivePreset) { Text(stringResource(R.string.compare_reset)) }
                            TextButton(onClick = onSaveAsNewRequest) { Text(stringResource(R.string.action_save)) }
                        }
                    }
                }
                ResponseGraph(
                    curve = curvePoints,
                    bands = graphBands,
                    selectedIndex = selectedBand,
                    onSelect = { selectedBand = it },
                    onGainChange = onBandGainChanged,
                    processing = processing,
                    stateLabel =
                        when {
                            !settings.masterEnabled -> stringResource(R.string.graph_state_off)
                            settings.bypass -> stringResource(R.string.graph_state_bypass)
                            else -> null
                        },
                    hotFrequencyHz = curvePeak?.takeIf { heavyLimiting && it.gainDb > 0f }?.frequencyHz,
                    description = curveDescription,
                )
                BandReadout(
                    band = graphBands.firstOrNull { it.index == selectedBand },
                    onGainChange = onBandGainChanged,
                    hint = stringResource(if (graphBands.isEmpty()) R.string.graph_hint_no_bands else R.string.graph_hint_select),
                )
                Text(
                    text = stringResource(R.string.graph_note_interpolated),
                    style = MaterialTheme.typography.bodySmall,
                    color = style.mutedText,
                )
            }

            // Original/EQ comparison. While comparing, both sides are matched in loudness (the
            // louder one is turned down by an estimate), so a mere volume difference does not
            // pass for a better sound.
            Section {
                SectionHeader(title = stringResource(R.string.compare_title))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChoiceChip(
                        text = stringResource(R.string.compare_original),
                        selected = compare?.side == CompareSide.ORIGINAL,
                        onClick = { onCompareSide(CompareSide.ORIGINAL) },
                    )
                    ChoiceChip(
                        text = stringResource(R.string.compare_eq),
                        selected = compare == null || compare.side == CompareSide.EQ,
                        onClick = { if (compare == null) onStartCompare() else onCompareSide(CompareSide.EQ) },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (compare != null) {
                        TextButton(onClick = onEndCompare) { Text(stringResource(R.string.compare_done)) }
                    }
                }
                HoldToCompareButton(
                    modifier = Modifier.fillMaxWidth(),
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
                )
            }

            // Genre x intensity: one choice per genre with its own mini curve, then the
            // intensity as one stepped slider.
            Section {
                SectionHeader(title = stringResource(R.string.genre_title))
                GenreChoices(
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
                val selectedGenre = selectedGenreIntensity?.first
                val selectedIntensity = selectedGenreIntensity?.second
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.intensity_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = style.text,
                        modifier = Modifier.weight(1f),
                    )
                    if (selectedGenre != null && selectedIntensity != null && selectedGenre.allowsIntensity) {
                        Text(text = selectedIntensity.displayName, style = SonicNumerals.value, color = style.accent)
                    }
                }
                IntensityStepper(
                    labels = intensities.map { it.displayName },
                    selectedIndex = selectedIntensity?.let { intensities.indexOf(it) }?.takeIf { it >= 0 },
                    enabled = selectedGenre?.allowsIntensity == true,
                    onSelect = { index -> selectedGenre?.let { onGenreIntensitySelected(it, intensities[index]) } },
                )
                if (intensities.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Caption(text = intensities.first().displayName, color = style.mutedText)
                        Caption(text = intensities.last().displayName, color = style.mutedText)
                    }
                }
                if (customPresets.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.presets_custom),
                        style = MaterialTheme.typography.titleSmall,
                        color = style.text,
                        modifier = Modifier.padding(top = spacing.small),
                    )
                    PresetGrid(
                        presets = customPresets,
                        activePresetId = activePreset.id,
                        onPresetSelected = onPresetSelected,
                        onDuplicatePreset = onDuplicatePreset,
                        onRenamePresetRequest = onRenamePresetRequest,
                        onDeletePresetRequest = onDeletePresetRequest,
                        spacing = spacing,
                        style = style,
                    )
                }
            }

            SoundGoalCard(active = activeGoal, onSelect = onGoalSelected)

            // Listening context (car / Bluetooth speaker) plus the three stages it uses as
            // tiles. A mode is an overlay on the active genre preset
            // (MainViewModel.activeContext), so it stays on when the genre changes; a
            // detected car/speaker route turns it on by itself.
            Section {
                SectionHeader(
                    title = stringResource(R.string.context_title),
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
                    Text(text = detectedRoute, style = MaterialTheme.typography.bodySmall, color = style.mutedText)
                }
                ContextModeChips(
                    activeContext = activeContext,
                    onContextModeChanged = onContextModeChanged,
                )
                Row(
                    modifier = Modifier.padding(top = spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    SignalModuleTile(
                        title = stringResource(R.string.module_loudness),
                        detail = stringResource(if (settings.loudnessMaxBoostDb > 0f) R.string.module_on else R.string.module_off),
                        checked = settings.loudnessMaxBoostDb > 0f,
                        onCheckedChange = onLoudnessCompensationChanged,
                        modifier = Modifier.weight(1f),
                    )
                    SignalModuleTile(
                        title = stringResource(R.string.module_subsonic),
                        detail =
                            if (settings.subsonicCutoffHz > 0f) {
                                // The 5-band system EQ can only lower its lowest band, not cut like a high-pass.
                                val prefix = if (preEqActive) "<" else "≈"
                                "$prefix ${settings.subsonicCutoffHz.toInt()} Hz"
                            } else {
                                stringResource(R.string.module_off)
                            },
                        checked = settings.subsonicCutoffHz > 0f,
                        onCheckedChange = onSubsonicFilterChanged,
                        modifier = Modifier.weight(1f),
                    )
                    SignalModuleTile(
                        title = stringResource(R.string.module_virtual_bass),
                        detail =
                            stringResource(
                                when {
                                    settings.virtualBassMix <= 0f -> R.string.module_off
                                    !isOwnPlayerSession && preEqActive -> R.string.module_approx
                                    !isOwnPlayerSession -> R.string.module_player_only
                                    else -> R.string.module_on
                                },
                            ),
                        checked = settings.virtualBassMix > 0f,
                        onCheckedChange = onVirtualBassChanged,
                        modifier = Modifier.weight(1f).alpha(if (isOwnPlayerSession) 1f else 0.6f),
                    )
                }
            }

            // M4: applied input gain, limiter status and threshold, warning levels and MBC
            // transparency - always visible, as segment meters plus the exact values. All of
            // it is derived from the settings; nothing here is a measurement.
            Section {
                SectionHeader(
                    title = stringResource(R.string.meters_title),
                    info = stringResource(R.string.signal_explain),
                )
                if (statusKind != EqStatusKind.ACTIVE) {
                    Text(
                        text = stringResource(R.string.signal_not_confirmed),
                        style = MaterialTheme.typography.bodySmall,
                        color = style.mutedText,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.small),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    LedMeter(
                        label = stringResource(R.string.meter_boost),
                        value = CurveGeometry.formatGain(headroom.maxPositiveGainDb.coerceAtLeast(0f)),
                        fraction = headroom.maxPositiveGainDb / METER_RANGE_DB,
                        scale = LedScale.LEVEL,
                    )
                    LedMeter(
                        label = stringResource(R.string.meter_input),
                        value = CurveGeometry.formatGain(settings.inputGainDb),
                        fraction = -settings.inputGainDb / METER_RANGE_DB,
                        scale = LedScale.REDUCTION,
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
                    )
                }
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
                            stringResource(R.string.headroom_limiter_sometimes) to style.caution
                        HeadroomWarningLevel.HEAVY_LIMITING ->
                            stringResource(R.string.headroom_limiter_heavy) to style.hot
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
                    color = if (clippingRisk) style.hot else style.mutedText,
                )
                Text(
                    text = stringResource(R.string.meters_estimate_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = style.mutedText,
                )
            }

            // Sound profiles: which preset and correction belong to this output.
            Section {
                SectionHeader(
                    title = stringResource(R.string.profiles_title),
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
                    style = MaterialTheme.typography.bodyMedium,
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

            // M3 "Mein Kopfhörer": which headphone/speaker correction curve is combined with
            // the voicing preset.
            Section {
                SectionHeader(
                    title = stringResource(R.string.correction_title),
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
                        Notice {
                            Text(
                                text = stringResource(R.string.correction_detected, suggestedCorrectionProfile.displayName),
                                style = MaterialTheme.typography.titleSmall,
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

                // Folds HeadphoneComfortCurve into the applied band-EQ curve
                // (MainViewModel.combinedCurve()) - a fixed bass/presence tilt, not a literal
                // crossfeed simulation, which the session-attached system effects cannot run.
                // Defaults from the route type until overridden here.
                SwitchRow(
                    title = stringResource(R.string.headphone_mode_title),
                    description = stringResource(R.string.headphone_mode_desc),
                    checked = effectiveHeadphoneAcoustics,
                    onCheckedChange = onHeadphoneAcousticsChanged,
                    style = style,
                )
            }

            if (effectiveHeadphoneAcoustics) {
                HeadphonePowerSection(
                    power = headphonePower,
                    onPowerChanged = onHeadphonePowerChanged,
                    style = style,
                )
            }

            var fineTuneOpen by rememberSaveable { mutableStateOf(false) }
            Section {
                SectionHeader(
                    title = stringResource(R.string.fine_tune_title),
                    trailing = {
                        TextButton(onClick = { fineTuneOpen = !fineTuneOpen }) {
                            Text(stringResource(if (fineTuneOpen) R.string.fine_tune_hide else R.string.fine_tune_show))
                        }
                    },
                )
                Text(
                    text = stringResource(R.string.fine_tune_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = style.mutedText,
                )
                AnimatedVisibility(visible = fineTuneOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
                        Text(
                            text = stringResource(R.string.macro_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = style.text,
                            modifier = Modifier.padding(top = spacing.small),
                        )
                        ValueSlider(
                            label = stringResource(R.string.macro_bass_name),
                            description = stringResource(R.string.macro_bass_desc),
                            value = settings.macroBassDb,
                            onValueChange = onMacroBassChanged,
                            valueRange = -6f..6f,
                            style = style,
                        )
                        ValueSlider(
                            label = stringResource(R.string.macro_punch_name),
                            description = stringResource(R.string.macro_punch_desc),
                            value = settings.macroPunchDb,
                            onValueChange = onMacroPunchChanged,
                            valueRange = -6f..6f,
                            style = style,
                        )
                        ValueSlider(
                            label = stringResource(R.string.macro_haerte_name),
                            description = stringResource(R.string.macro_haerte_desc),
                            value = settings.macroHaerteDb,
                            onValueChange = onMacroHaerteChanged,
                            valueRange = -2f..2f,
                            style = style,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.bands_title, bands.size),
                                style = MaterialTheme.typography.titleSmall,
                                color = style.text,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(R.string.bypass_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = style.text,
                            )
                            Spacer(modifier = Modifier.width(spacing.small))
                            val bypassDescription = stringResource(R.string.bypass_label)
                            Switch(
                                modifier = Modifier.semantics { contentDescription = bypassDescription },
                                checked = settings.bypass,
                                onCheckedChange = onBypassToggled,
                            )
                        }
                        // The same bands as the graph, as sliders: the selected one is marked in
                        // both places, and moving a slider selects its point.
                        Column {
                            graphBands.sortedBy { it.frequencyHz }.forEach { band ->
                                BandSliderRow(
                                    band = band,
                                    selected = band.index == selectedBand,
                                    onGainChange = { gain ->
                                        selectedBand = band.index
                                        onBandGainChanged(band.index, gain)
                                    },
                                    style = style,
                                )
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

// Genre picker: one choice per genre with its own curve, scrolling sideways, so every
// genre is visible and one tap away. The genre shows in the curve, not in a color theme.
@Composable
private fun GenreChoices(
    genres: List<GenrePreset>,
    selected: GenrePreset?,
    onSelect: (GenrePreset) -> Unit,
    style: EqualizerDesignStyle,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        genres.forEach { genre ->
            val isSelected = genre.id == selected?.id
            Surface(
                selected = isSelected,
                onClick = { onSelect(genre) },
                modifier =
                    Modifier
                        .width(120.dp)
                        .height(84.dp)
                        .semantics { contentDescription = genre.displayName },
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) style.accent.copy(alpha = 0.14f) else style.surfaceVariant,
                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) style.accent else style.border),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    MiniCurve(
                        points = genre.base.targetCurve,
                        color = if (isSelected) style.accent else style.mutedText,
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                    )
                    Text(
                        text = genre.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = style.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// Three-per-row grid of the user's own presets. Static/chunked rather than
// LazyVerticalGrid since the list is small.
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
                        style = style,
                    )
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// Horizontally scrollable row of selectable profiles: a name and its source, no icon.
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
            Surface(
                selected = selected,
                onClick = { onProfileSelected(profile) },
                modifier = Modifier.heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.small,
                color = if (selected) style.accent.copy(alpha = 0.14f) else style.surfaceVariant,
                border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) style.accent else style.border),
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.padding(end = 8.dp)) {
                        Text(
                            text = profile.localizedName(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = style.text,
                        )
                        Text(
                            text = profile.localizedSource(),
                            style = MaterialTheme.typography.bodySmall,
                            color = style.mutedText,
                        )
                        profile.deviceName?.let { device ->
                            Text(
                                text = stringResource(R.string.profile_for_device, device),
                                style = MaterialTheme.typography.bodySmall,
                                color = style.mutedText,
                            )
                        }
                    }
                    // M5 "Import, Export und Teilen ... integrieren" - only for
                    // imported/custom profiles, mirroring PresetCard's own
                    // built-in-vs-custom distinction (built-ins have nothing
                    // device-specific worth exporting).
                    if (!profile.builtIn) {
                        IconButton(onClick = { onExportProfile(profile) }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.profile_export, profile.localizedName()),
                                tint = style.mutedText,
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
) {
    val contentColor = if (selected) style.text else style.mutedText
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = MaterialTheme.shapes.small,
        color = if (selected) style.accent.copy(alpha = 0.14f) else style.surfaceVariant,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) style.accent else style.border),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = presetIcon(preset.id),
                contentDescription = null,
                tint = if (selected) style.accent else contentColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = preset.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                // 48 dp target, so the menu is hit reliably next to the preset itself.
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.preset_more_actions, preset.name),
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
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
private fun HeadphonePowerSection(
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
private fun SwitchRow(
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
private fun ValueSlider(
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
private fun BandSliderRow(
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
private fun sonicSliderColors(style: EqualizerDesignStyle) =
    SliderDefaults.colors(
        thumbColor = style.accent,
        activeTrackColor = style.accent,
        inactiveTrackColor = style.surfaceVariant,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
    )

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
