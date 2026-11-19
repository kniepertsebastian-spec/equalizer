package com.auracurve.app.ui.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.audio.AudioDeviceType
import com.auracurve.app.audio.AudioEngineState
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.AudioSession
import com.auracurve.app.audio.DeviceKind
import com.auracurve.app.audio.EqualizerBandCapabilities
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.autoeq.AutoEqCatalogEntry
import com.auracurve.app.context.SoundContext
import com.auracurve.app.correction.CorrectionProfile
import com.auracurve.app.dsp.CurveComposer
import com.auracurve.app.dsp.HeadphoneComfortCurve
import com.auracurve.app.dsp.HeadphonePower
import com.auracurve.app.dsp.HeadroomCalculator
import com.auracurve.app.dsp.HeadroomWarningLevel
import com.auracurve.app.dsp.HeadroomWarningLevelCalculator
import com.auracurve.app.dsp.LoudnessCompensationCurve
import com.auracurve.app.dsp.ParametricBand
import com.auracurve.app.dsp.SubsonicFilterCurve
import com.auracurve.app.dsp.VirtualBassApproxCurve
import com.auracurve.app.dsp.VolumeLevelMapper
import com.auracurve.app.preset.BuiltInContextPresets
import com.auracurve.app.preset.GenrePreset
import com.auracurve.app.preset.GenrePresetGroups
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.PresetIntensity
import com.auracurve.app.preset.SoundGoal
import com.auracurve.app.preset.TargetPoint
import com.auracurve.app.ui.eq.EqStatus
import com.auracurve.app.ui.eq.EqStatusKind
import com.auracurve.app.ui.eq.EqStatusLine
import com.auracurve.app.ui.main.CompareSide
import com.auracurve.app.ui.main.CompareState
import com.auracurve.app.ui.theme.spacing

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
    onSharePreset: (Preset) -> Unit,
    onImportPresetLinkRequested: () -> Unit,
    onMacroBassChanged: (Float) -> Unit,
    onMacroPunchChanged: (Float) -> Unit,
    onMacroHaerteChanged: (Float) -> Unit,
    onBandGainChanged: (bandIndex: Int, gainDb: Float) -> Unit,
    onRetryAttach: () -> Unit,
    audioSessions: List<AudioSession> = emptyList(),
    activeAudioSession: AudioSession? = null,
    parametricBands: List<ParametricBand> = emptyList(),
    onAddParametricBand: () -> Unit = {},
    onChangeParametricBand: (Int, ParametricBand) -> Unit = { _, _ -> },
    onRemoveParametricBand: (Int) -> Unit = {},
    appProfileBound: Boolean = false,
    onAppProfileBoundChanged: ((Boolean) -> Unit)? = null,
    isOwnPlayerSession: Boolean = true,
    deviceKindPrompt: AudioRoute? = null,
    onAnswerDeviceKind: (DeviceKind) -> Unit = {},
    onDismissDeviceKindPrompt: () -> Unit = {},
    preEqActive: Boolean = false,
    onHighResolutionEqChanged: ((Boolean) -> Unit)? = null,
    preEqResponseMode: com.auracurve.app.dsp.PreEqResponseMode = com.auracurve.app.dsp.PreEqResponseMode.BALANCED,
    onPreEqResponseModeChanged: ((com.auracurve.app.dsp.PreEqResponseMode) -> Unit)? = null,
    onSelectAudioSession: (AudioSession) -> Unit = {},
    // Rarely used extras (diagnostics, debug) at the end of the Device tab, out of the way.
    deviceTabFooter: (@Composable ColumnScope.() -> Unit)? = null,
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

    // AuraCurve roadmap 04, part A (docs/EQUALIZER_REDESIGN.md): a fixed head with the curve, one
    // notice at a time below it, and the rest in three tabs sorted by how often it is used. The
    // sections below are the ones this screen always had, unchanged; only where they sit changed.
    var tab by rememberSaveable { mutableStateOf(EqualizerTab.SOUND) }
    var signalChainOpen by rememberSaveable { mutableStateOf(false) }
    var noSessionDetails by rememberSaveable { mutableStateOf(false) }
    // The curve folds while the options below are scrolled down and unfolds at their top (or on a
    // tap), so the options get most of a phone screen without losing sight of the curve.
    var curveCollapsed by rememberSaveable { mutableStateOf(false) }
    val collapseOnScroll =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y < -COLLAPSE_THRESHOLD_PX && source == NestedScrollSource.UserInput) curveCollapsed = true
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    // Only what the options could not take: they are at their top and the pull goes on.
                    if (available.y > COLLAPSE_THRESHOLD_PX) curveCollapsed = false
                    return Offset.Zero
                }
            }
        }
    val waitingForSession = state is AudioEngineState.Detached || state is AudioEngineState.Listening

    @Composable
    fun Head(
        graphHeight: Dp,
        collapsible: Boolean,
    ) {
        val collapsed = collapsible && curveCollapsed
        val shownGraphHeight by animateDpAsState(
            targetValue = if (collapsed) CollapsedGraphHeight else graphHeight,
            label = "graphHeight",
        )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                // Waiting for a session: the line itself opens what to do (it used to be a second card
                // below the curve saying the same).
                EqStatusLine(
                    kind = statusKind,
                    pathLabel = pathLabel,
                    onClick = { if (waitingForSession) noSessionDetails = !noSessionDetails },
                    modifier = Modifier.weight(1f),
                    trailing =
                        if (waitingForSession) {
                            {
                                Icon(
                                    imageVector = if (noSessionDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription =
                                        stringResource(if (noSessionDetails) R.string.info_less else R.string.info_more),
                                    tint = style.mutedText,
                                )
                            }
                        } else {
                            null
                        },
                )
                HeadroomBadge(
                    level = if (settings.masterEnabled) warningLevel else null,
                    onClick = { signalChainOpen = true },
                    style = style,
                )
            }
            AnimatedVisibility(visible = waitingForSession && noSessionDetails) {
                Text(
                    text = stringResource(R.string.no_session_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = style.mutedText,
                )
            }
            AnimatedVisibility(visible = isDirty) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onResetToActivePreset) { Text(stringResource(R.string.compare_reset)) }
                    TextButton(onClick = onSaveAsNewRequest) { Text(stringResource(R.string.action_save)) }
                }
            }
            Box {
                ResponseGraph(
                    height = shownGraphHeight,
                    collapsed = collapsed,
                    hint =
                        if (selectedBand == null) {
                            stringResource(
                                if (graphBands.isEmpty()) R.string.graph_hint_no_bands_short else R.string.graph_hint_select_short,
                            )
                        } else {
                            null
                        },
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
                if (collapsed) {
                    val expand = stringResource(R.string.graph_expand)
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .clickable(onClickLabel = expand, role = Role.Button) { curveCollapsed = false },
                    )
                }
            }
            // The readout takes a row only while a band is selected; the help sits in the graph.
            val selected = graphBands.firstOrNull { it.index == selectedBand }
            AnimatedVisibility(visible = selected != null && !collapsed) {
                BandReadout(
                    band = selected,
                    onGainChange = onBandGainChanged,
                    hint = "",
                )
            }
        }
    }

    // At most one notice, the most urgent first (docs/EQUALIZER_REDESIGN.md, 3.2).
    @Composable
    fun NoticeSlot() {
        when {
            state is AudioEngineState.Error ->
                OutlinedButton(onClick = onRetryAttach, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_retry))
                }
            deviceKindPrompt != null ->
                DeviceKindCard(
                    deviceName = deviceKindPrompt.name,
                    onAnswer = onAnswerDeviceKind,
                    onDismiss = onDismissDeviceKindPrompt,
                )
            // A suggestion only, never applied silently - see
            // MainViewModel.updateSuggestedCorrectionProfile()/AutoEqCatalogMatcher.
            suggestedCorrectionProfile != null ->
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

    @Composable
    fun CompareSection() {
        Section {
            // The side chips sit in the header; the idle explanation is behind (i), and the
            // level-matched delta shows only while comparing.
            SectionHeader(
                title = stringResource(R.string.compare_title),
                info = stringResource(R.string.compare_hint_idle),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                HoldToCompareButton(
                    modifier = Modifier.weight(1f),
                    onPress = { onCompareSide(CompareSide.ORIGINAL) },
                    onRelease = onEndCompare,
                    style = style,
                )
            }
            if (compare != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.compare_hint_active, String.format("%+.1f", compare.eqLevelDeltaDb)),
                        style = MaterialTheme.typography.bodySmall,
                        color = style.mutedText,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onEndCompare) { Text(stringResource(R.string.compare_done)) }
                }
            }
        }
    }

    @Composable
    fun GenreSection() {
        var presetUsageOpen by rememberSaveable { mutableStateOf(false) }
        Section {
            SectionHeader(title = stringResource(R.string.genre_title)) {
                // P5-4: a preset link someone sent (or a scanned QR code, which opens the app directly).
                TextButton(onClick = onImportPresetLinkRequested) {
                    Text(stringResource(R.string.preset_import_link))
                }
                // When the active preset (genre or custom) is a good choice; each custom preset
                // also has it in its own menu.
                IconButton(onClick = { presetUsageOpen = !presetUsageOpen }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription =
                            stringResource(
                                if (presetUsageOpen) R.string.preset_usage_active_less else R.string.preset_usage_active_more,
                            ),
                        tint = if (presetUsageOpen) style.accent else style.mutedText,
                    )
                }
            }
            AnimatedVisibility(visible = presetUsageOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = spacing.small)) {
                    Text(
                        text = stringResource(R.string.preset_usage_active_label, activePreset.name),
                        style = MaterialTheme.typography.labelLarge,
                        color = style.text,
                    )
                    PresetUsageText(preset = activePreset, style = style)
                }
            }
            // A genre variant (e.g. "Uptempo – Car") is no genre x intensity combination, but its
            // genre card still shows as chosen; no intensity step is then highlighted.
            val selectedGenre =
                selectedGenreIntensity?.first
                    ?: GenrePresetGroups.genreOf(activePreset.id)?.let { genre -> genrePresets.firstOrNull { it.id == genre.id } }
            GenreChoices(
                genres = genrePresets,
                selected = selectedGenre,
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
            val selectedIntensity = selectedGenreIntensity?.second
            // The segments name every level, so the heading needs no value of its own.
            Caption(
                text = stringResource(R.string.intensity_title),
                color = style.mutedText,
                modifier = Modifier.padding(top = 4.dp),
            )
            IntensityStepper(
                labels = intensities.map { it.displayName },
                selectedIndex = selectedIntensity?.let { intensities.indexOf(it) }?.takeIf { it >= 0 },
                enabled = selectedGenre?.allowsIntensity == true,
                onSelect = { index -> selectedGenre?.let { onGenreIntensitySelected(it, intensities[index]) } },
            )
            Caption(
                text = stringResource(R.string.genre_presets_title),
                color = style.mutedText,
                modifier = Modifier.padding(top = spacing.small),
            )
            GenrePresetList(
                groups = remember(genrePresets) { GenrePresetGroups.forGenres(genrePresets) },
                activePresetId = activePreset.id,
                onPresetSelected = onPresetSelected,
                onDuplicatePreset = onDuplicatePreset,
                onSharePreset = onSharePreset,
                style = style,
            )
            if (customPresets.isNotEmpty()) {
                var customOpen by rememberSaveable { mutableStateOf(true) }
                val activeCustom = customPresets.firstOrNull { it.id == activePreset.id }
                Spacer(modifier = Modifier.height(2.dp))
                ExpandableGroupHeader(
                    title = stringResource(R.string.presets_custom),
                    subtitle =
                        pluralStringResource(R.plurals.genre_presets_count, customPresets.size, customPresets.size) +
                            (activeCustom?.let { " · " + stringResource(R.string.genre_presets_active, it.name) } ?: ""),
                    icon = Icons.Outlined.Tune,
                    expanded = customOpen,
                    highlighted = activeCustom != null,
                    onToggle = { customOpen = !customOpen },
                    style = style,
                )
                AnimatedVisibility(visible = customOpen) {
                    PresetGrid(
                        presets = customPresets,
                        activePresetId = activePreset.id,
                        onPresetSelected = onPresetSelected,
                        onDuplicatePreset = onDuplicatePreset,
                        onRenamePresetRequest = onRenamePresetRequest,
                        onDeletePresetRequest = onDeletePresetRequest,
                        onSharePreset = onSharePreset,
                        spacing = spacing,
                        style = style,
                    )
                }
            }
        }
    }

    // Virtual Bass, loudness and subsonic: switched often, so they sit with the sound choice.
    @Composable
    fun ModulesSection() {
        Section {
            SectionHeader(
                title = stringResource(R.string.modules_title),
                info =
                    listOf(
                        stringResource(R.string.context_loudness_desc),
                        if (settings.subsonicCutoffHz > 0f) {
                            stringResource(R.string.context_subsonic_on, settings.subsonicCutoffHz.toInt())
                        } else {
                            stringResource(R.string.context_subsonic_off)
                        },
                        stringResource(R.string.context_virtual_bass_desc),
                    ).joinToString("\n"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                SignalModuleTile(
                    title = stringResource(R.string.module_loudness),
                    detail = stringResource(if (settings.loudnessMaxBoostDb > 0f) R.string.module_on else R.string.module_off),
                    checked = settings.loudnessMaxBoostDb > 0f,
                    onCheckedChange = onLoudnessCompensationChanged,
                    icon = Icons.Outlined.Hearing,
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
                    icon = Icons.Outlined.FilterAlt,
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
                    icon = Icons.Outlined.GraphicEq,
                    modifier = Modifier.weight(1f).alpha(if (isOwnPlayerSession) 1f else 0.6f),
                )
            }
        }
    }

    // Listening context (car / Bluetooth speaker): an overlay on the active genre preset
    // (MainViewModel.activeContext); a detected car/speaker route turns it on by itself.
    @Composable
    fun ContextSection() {
        Section {
            SectionHeader(
                title = stringResource(R.string.context_title),
                info = stringResource(R.string.context_default_desc),
            )
            val detectedRoute =
                when (route.type) {
                    AudioDeviceType.CAR -> stringResource(R.string.context_car_detected, route.name)
                    AudioDeviceType.BLUETOOTH_SPEAKER -> stringResource(R.string.context_speaker_detected, route.name)
                    AudioDeviceType.SOUNDBAR -> stringResource(R.string.context_soundbar_detected, route.name)
                    else -> null
                }
            if (detectedRoute != null) {
                Text(text = detectedRoute, style = MaterialTheme.typography.bodySmall, color = style.mutedText)
            }
            ContextModeChips(
                activeContext = activeContext,
                onContextModeChanged = onContextModeChanged,
            )
        }
    }

    @Composable
    fun SignalChainSection() {
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
    }

    @Composable
    fun ProfilesSection() {
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
    }

    @Composable
    fun CorrectionSection() {
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
    }

    @Composable
    fun FineTuneSection() {
        Section {
            SectionHeader(title = stringResource(R.string.fine_tune_title))
            Text(
                text = stringResource(R.string.fine_tune_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = style.mutedText,
            )
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

    @Composable
    fun TabContent(modifier: Modifier) {
        val scroll = key(tab) { rememberScrollState() }
        Column(
            modifier =
                modifier
                    .nestedScroll(collapseOnScroll)
                    .verticalScroll(scroll)
                    .padding(horizontal = spacing.medium)
                    .padding(top = spacing.extraSmall, bottom = spacing.medium),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                EqualizerTab.SOUND -> {
                    GenreSection()
                    ModulesSection()
                    SoundGoalCard(active = activeGoal, onSelect = onGoalSelected)
                    CompareSection()
                }
                EqualizerTab.FINE_TUNE -> {
                    FineTuneSection()
                    ParametricBandsSection(
                        bands = parametricBands,
                        onAdd = onAddParametricBand,
                        onChange = onChangeParametricBand,
                        onRemove = onRemoveParametricBand,
                        spacing = spacing,
                        style = style,
                    )
                    onHighResolutionEqChanged?.let { onChanged ->
                        HighResolutionEqRow(
                            checked = settings.highResolutionEq,
                            active = preEqActive,
                            onCheckedChange = onChanged,
                            responseMode = preEqResponseMode,
                            onResponseModeChange = onPreEqResponseModeChanged,
                        )
                    }
                    Text(
                        text = stringResource(R.string.graph_note_interpolated),
                        style = MaterialTheme.typography.bodySmall,
                        color = style.mutedText,
                    )
                }
                EqualizerTab.DEVICE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                        AudioSourceList(
                            sessions = audioSessions,
                            activeSession = activeAudioSession,
                            onSelect = onSelectAudioSession,
                            appProfileBound = appProfileBound,
                            onAppProfileBoundChange = onAppProfileBoundChanged,
                        )
                        Text(
                            text = stringResource(R.string.eq_playback_only_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = style.mutedText,
                        )
                    }
                    ProfilesSection()
                    CorrectionSection()
                    if (effectiveHeadphoneAcoustics) {
                        HeadphonePowerSection(
                            power = headphonePower,
                            onPowerChanged = onHeadphonePowerChanged,
                            style = style,
                        )
                    }
                    ContextSection()
                    deviceTabFooter?.invoke(this)
                }
            }
        }
    }

    @Composable
    fun Tabs() {
        EqualizerTabs(selected = tab, onSelect = { tab = it })
    }

    CompositionLocalProvider(LocalContentColor provides style.text) {
        BoxWithConstraints(modifier = modifier.fillMaxSize().background(style.background)) {
            // Short screens get a smaller curve; wide ones (landscape) put the head beside the tabs.
            val wide = maxWidth > maxHeight && maxWidth >= WIDE_LAYOUT_MIN_WIDTH
            val compact = maxHeight < COMPACT_HEIGHT
            val graphHeight = if (compact) CompactGraphHeight else DefaultGraphHeight
            // Folding only helps where the head sits above the options.
            val collapsible = !wide
            if (wide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        Head(graphHeight, collapsible)
                        NoticeSlot()
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Tabs()
                        TabContent(Modifier.weight(1f))
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(horizontal = spacing.medium).padding(top = spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        Head(graphHeight, collapsible)
                        NoticeSlot()
                    }
                    Tabs()
                    TabContent(Modifier.weight(1f))
                }
            }
        }
        if (signalChainOpen) {
            SignalChainSheet(onDismiss = { signalChainOpen = false }) { SignalChainSection() }
        }
    }
}

// Finger movement (px per event) that counts as scrolling the options, not as jitter.
private const val COLLAPSE_THRESHOLD_PX = 1f

// Size of the LED meters' scale: 12 dB fills a chain.
private const val METER_RANGE_DB = 12f
