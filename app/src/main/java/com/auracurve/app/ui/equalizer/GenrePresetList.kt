package com.auracurve.app.ui.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.eq.ActivePlaybackSource
import com.auracurve.app.preset.GenrePresetGroup
import com.auracurve.app.preset.GenreUseCase
import com.auracurve.app.preset.GenreVariant
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.PresetSourceScope
import com.auracurve.app.ui.components.cyber.GlowIconBadge
import com.auracurve.app.ui.components.hardcore.HcBadge
import com.auracurve.app.ui.components.hardcore.HcGrille
import com.auracurve.app.ui.components.hardcore.HcLed
import com.auracurve.app.ui.components.hardcore.HcSlideSwitch
import com.auracurve.app.ui.components.hardcore.HcText
import com.auracurve.app.ui.components.hardcore.HcWarn
import com.auracurve.app.ui.components.hardcore.hcGenreTint
import com.auracurve.app.ui.theme.CyberBrushes
import com.auracurve.app.ui.theme.CyberColors
import com.auracurve.app.ui.theme.CyberDims
import com.auracurve.app.ui.theme.HardcoreColors
import com.auracurve.app.ui.theme.HardcoreType
import com.auracurve.app.ui.theme.HcCorner
import com.auracurve.app.ui.theme.LocalHardcoreLook
import com.auracurve.app.ui.theme.hcWell

// Roadmap 16: how strongly a preset for the other source is greyed out (Material's disabled alpha).
internal const val INACTIVE_PRESET_ALPHA = 0.38f

/** True when [preset] takes effect with what plays now; with nothing detected every preset does. */
internal fun ActivePlaybackSource.allows(preset: Preset): Boolean = preset.metadata.sourceScope.fits(scope)

/**
 * The source list the preset picker starts on: the detected source, or with none detected the
 * active preset's own (the classic Spotify list for Flat and presets saved for both).
 */
internal fun initialPresetSourceTab(
    source: ActivePlaybackSource,
    activePreset: Preset,
): PresetSourceScope =
    source.scope.takeIf { it != PresetSourceScope.ALL }
        ?: activePreset.metadata.sourceScope.takeIf { it != PresetSourceScope.ALL }
        ?: PresetSourceScope.EXTERNAL_APPS

/** Why a preset is greyed out while [this] plays, or null when it is not. */
@Composable
internal fun ActivePlaybackSource.inactiveReason(preset: Preset): String? =
    when {
        allows(preset) -> null
        preset.metadata.sourceScope == PresetSourceScope.EXTERNAL_APPS -> stringResource(R.string.preset_source_only_external)
        else -> stringResource(R.string.preset_source_only_own)
    }

@Composable
internal fun PresetSourceScope.label(): String =
    stringResource(
        when (this) {
            PresetSourceScope.OWN_PLAYER -> R.string.preset_source_own_player
            PresetSourceScope.EXTERNAL_APPS -> R.string.preset_source_external
            PresetSourceScope.ALL -> R.string.preset_source_both
        },
    )

/**
 * Roadmap 16, P16-3: the switch between the two preset lists - own player and Spotify & other apps -
 * with the source detected right now above it. The status says it in words (the dot is only a
 * second cue), and a list for the other source says why its presets are greyed out.
 */
@Composable
internal fun PresetSourceTabs(
    selected: PresetSourceScope,
    detected: ActivePlaybackSource,
    externalAppLabel: String?,
    onSelect: (PresetSourceScope) -> Unit,
    style: EqualizerDesignStyle,
    modifier: Modifier = Modifier,
) {
    val status =
        when (detected) {
            ActivePlaybackSource.OwnPlayer -> stringResource(R.string.preset_source_detected_own)
            is ActivePlaybackSource.ExternalApp ->
                externalAppLabel?.let { stringResource(R.string.preset_source_detected_external, it) }
                    ?: stringResource(R.string.preset_source_detected_external_unknown)
            ActivePlaybackSource.None -> stringResource(R.string.preset_source_detected_none)
        }
    if (LocalHardcoreLook.current) {
        HcPresetSourceTabs(selected, detected, status, onSelect, modifier)
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.FiberManualRecord,
                contentDescription = null,
                tint = if (detected == ActivePlaybackSource.None) style.mutedText else CyberColors.signal,
                modifier = Modifier.size(10.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = status, style = MaterialTheme.typography.bodySmall, color = style.mutedText)
        }
        val tabsLabel = stringResource(R.string.preset_source_tabs)
        Row(
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = tabsLabel },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(PresetSourceScope.OWN_PLAYER, PresetSourceScope.EXTERNAL_APPS).forEach { scope ->
                ChoiceChip(
                    text = scope.label(),
                    selected = scope == selected,
                    onClick = { onSelect(scope) },
                    modifier = Modifier.weight(1f),
                    style = style,
                )
            }
        }
        if (!selected.fits(detected.scope)) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = CyberColors.surfaceHigh,
                border = BorderStroke(CyberDims.border, CyberColors.amber.copy(alpha = 0.5f)),
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = style.caution,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text =
                            stringResource(
                                if (selected == PresetSourceScope.EXTERNAL_APPS) {
                                    R.string.preset_source_inactive_note_external
                                } else {
                                    R.string.preset_source_inactive_note_own
                                },
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = style.text,
                    )
                }
            }
        }
    }
}

// The hardcore source switch: the detected source with a lamp (lit green when one is detected),
// a two-way slide switch, and a hazard note when the list shown does not fit what plays.
@Composable
private fun HcPresetSourceTabs(
    selected: PresetSourceScope,
    detected: ActivePlaybackSource,
    status: String,
    onSelect: (PresetSourceScope) -> Unit,
    modifier: Modifier,
) {
    val scopes = listOf(PresetSourceScope.OWN_PLAYER, PresetSourceScope.EXTERNAL_APPS)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HcLed(
                on = detected != ActivePlaybackSource.None,
                color = HardcoreColors.toxic,
                gray = detected == ActivePlaybackSource.None,
            )
            Text(text = status, style = HardcoreType.note, color = HardcoreColors.ash)
        }
        val tabsLabel = stringResource(R.string.preset_source_tabs)
        HcSlideSwitch(
            options = scopes.map { it.label() },
            selectedIndex = scopes.indexOf(selected).takeIf { it >= 0 },
            onSelect = { onSelect(scopes[it]) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = tabsLabel },
        )
        if (!selected.fits(detected.scope)) {
            HcWarn(
                text =
                    stringResource(
                        if (selected == PresetSourceScope.EXTERNAL_APPS) {
                            R.string.preset_source_inactive_note_external
                        } else {
                            R.string.preset_source_inactive_note_own
                        },
                    ),
                textColor = HardcoreColors.bone,
            )
        }
    }
}

// Every built-in preset, grouped by genre: one collapsed row per genre, opening into its
// presets (base, character variants, use cases), each with the text saying when it fits best.
// Several genres can be open at once; the genre holding the active preset starts open.
// Roadmap 16: presets that do not fit the detected source are greyed out and cannot be tapped
// (their menu with "when is this the best choice?" stays usable).
@Composable
internal fun GenrePresetList(
    groups: List<GenrePresetGroup>,
    activePresetId: String,
    onPresetSelected: (Preset) -> Unit,
    onDuplicatePreset: ((Preset) -> Unit)?,
    onSharePreset: ((Preset) -> Unit)?,
    style: EqualizerDesignStyle,
    detectedSource: ActivePlaybackSource = ActivePlaybackSource.None,
) {
    // Saved as one string: rememberSaveable cannot put a Set into the saved state bundle.
    var expanded by rememberSaveable {
        mutableStateOf(
            groups
                .firstOrNull { group -> group.entries.any { it.preset.id == activePresetId } }
                ?.genre
                ?.id
                .orEmpty(),
        )
    }
    val openIds = expanded.split(',').filter { it.isNotEmpty() }.toSet()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        groups.forEach { group ->
            val isOpen = group.genre.id in openIds
            val activeEntry = group.entries.firstOrNull { it.preset.id == activePresetId }
            ExpandableGroupHeader(
                title = group.genre.displayName,
                subtitle =
                    pluralStringResource(R.plurals.genre_presets_count, group.entries.size, group.entries.size) +
                        (activeEntry?.let { " · " + stringResource(R.string.genre_presets_active, shortName(it.preset)) } ?: ""),
                icon = genreIcon(group.genre.id),
                tint = if (LocalHardcoreLook.current) hcGenreTint(group.genre.id) else genreTint(group.genre.id),
                expanded = isOpen,
                highlighted = activeEntry != null,
                onToggle = {
                    expanded = (if (isOpen) openIds - group.genre.id else openIds + group.genre.id).joinToString(",")
                },
                style = style,
            )
            AnimatedVisibility(visible = isOpen) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    group.entries.forEach { entry ->
                        GenrePresetRow(
                            entry = entry,
                            selected = entry.preset.id == activePresetId,
                            inactiveReason = detectedSource.inactiveReason(entry.preset),
                            onClick = { onPresetSelected(entry.preset) },
                            onDuplicate = onDuplicatePreset?.let { { it(entry.preset) } },
                            onShare = onSharePreset?.let { { it(entry.preset) } },
                            style = style,
                        )
                    }
                }
            }
        }
    }
}

// Header row of a collapsible group: a glass row with a glowing icon badge in the group's color.
// The chevron and the "active" note carry the state, and the group holding the active preset
// gets the pills' bright edge, so it does not depend on color alone.
@Composable
internal fun ExpandableGroupHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    highlighted: Boolean,
    onToggle: () -> Unit,
    style: EqualizerDesignStyle,
    tint: Color = CyberColors.accentText,
) {
    val stateText = stringResource(if (expanded) R.string.genre_presets_expanded else R.string.genre_presets_collapsed)
    if (LocalHardcoreLook.current) {
        HcCrateHeader(title, subtitle, icon, expanded, highlighted, onToggle, tint, stateText)
        return
    }
    Surface(
        onClick = onToggle,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .semantics { stateDescription = stateText },
        shape = MaterialTheme.shapes.medium,
        color = CyberColors.surface,
        border =
            if (highlighted) {
                BorderStroke(CyberDims.activeBorder, CyberBrushes.pillBorder)
            } else {
                BorderStroke(CyberDims.border, CyberBrushes.border)
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlowIconBadge(icon = icon, tint = tint, size = 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
                    color = style.text,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (highlighted) CyberColors.cyanText else style.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = if (expanded) CyberColors.cyan else style.mutedText,
            )
        }
    }
}

// A genre as a crate: a steel box with the genre's badge, its name stamped in capitals, a speaker
// grille and the chevron. The crate holding the active preset gets a blood-red edge on the left
// and its "active" note in bone, so the state does not rest on the color alone.
@Composable
private fun HcCrateHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    highlighted: Boolean,
    onToggle: () -> Unit,
    tint: Color,
    stateText: String,
) {
    // The "active" part of the subtitle stands out; the text stays one string for TalkBack.
    val split = subtitle.indexOf(" · ")
    val styledSubtitle =
        buildAnnotatedString {
            append(subtitle)
            if (split >= 0) addStyle(SpanStyle(color = HardcoreColors.bone), split + 3, subtitle.length)
        }
    Surface(
        onClick = onToggle,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .semantics { stateDescription = stateText },
        shape = HcCorner,
        color = Color.Transparent,
        contentColor = HardcoreColors.bone,
    ) {
        Box(
            modifier =
                Modifier.drawBehind {
                    drawRect(CrateFace)
                    drawRect(if (highlighted) CrateEdgeActive else CrateEdge, style = Stroke(1.dp.toPx()))
                    if (highlighted) drawRect(HardcoreColors.bloodHi, size = Size(3.dp.toPx(), size.height))
                },
        ) {
            Box(modifier = Modifier.matchParentSize().padding(end = 44.dp), contentAlignment = Alignment.CenterEnd) {
                HcGrille(modifier = Modifier.fillMaxHeight().padding(vertical = 6.dp))
            }
            Row(
                modifier = Modifier.heightIn(min = 62.dp).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HcBadge(icon = icon, tint = tint)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    HcText(
                        text = title,
                        style = HardcoreType.crate,
                        color = HardcoreColors.bone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = styledSubtitle,
                        style = HardcoreType.small,
                        color = HardcoreColors.ash,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = if (expanded) HardcoreColors.bone else HardcoreColors.ash,
                    )
                }
            }
        }
    }
}

private val CrateFace = Brush.verticalGradient(listOf(Color(0xFF1A1917), Color(0xFF141312)))
private val CrateEdge = Color(0xFF2C2925)
private val CrateEdgeActive = Color(0xFF4A1A15)
private val PatchFace = Color(0xFF141312)
private val PatchFaceOn = Brush.horizontalGradient(0f to Color(0xFF24110E), 0.7f to Color(0xFF161211))
private val PatchEdge = Color(0xFF262320)
private val PatchEdgeOn = Color(0xFF5A1D16)

// A preset as a patch: icon in a small well, the name stamped in capitals with a lit lamp when it
// is the chosen one, when it fits, why it is greyed out, and the same menu as the plain row.
@Composable
private fun HcPatchRow(
    entry: GenreVariant,
    selected: Boolean,
    inactiveReason: String?,
    onClick: () -> Unit,
    menu: @Composable () -> Unit,
) {
    val preset = entry.preset
    val enabled = inactiveReason == null
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else INACTIVE_PRESET_ALPHA)
                .semantics {
                    contentDescription = preset.name
                    inactiveReason?.let { stateDescription = it }
                },
        shape = HcCorner,
        color = Color.Transparent,
        contentColor = HardcoreColors.bone,
        border = BorderStroke(1.dp, if (selected) PatchEdgeOn else PatchEdge),
    ) {
        Row(
            modifier =
                Modifier
                    .then(if (selected) Modifier.background(PatchFaceOn) else Modifier.background(PatchFace))
                    .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 0.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(modifier = Modifier.size(28.dp).hcWell(depth = 2.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = useCaseIcon(entry.useCase),
                    contentDescription = null,
                    tint = if (selected) HardcoreColors.bloodHi else HardcoreColors.ash,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HcText(text = shortName(preset), style = HardcoreType.patch, color = HardcoreColors.bone)
                    // The chosen preset is marked by the lit lamp as well as the face.
                    if (selected) HcLed(on = true)
                }
                Text(text = entry.description, style = HardcoreType.small, color = HardcoreColors.ash)
                inactiveReason?.let { reason ->
                    Text(text = reason, style = HardcoreType.small, fontWeight = FontWeight.SemiBold, color = HardcoreColors.hazard)
                }
            }
            menu()
        }
    }
}

@Composable
private fun GenrePresetRow(
    entry: GenreVariant,
    selected: Boolean,
    inactiveReason: String?,
    onClick: () -> Unit,
    onDuplicate: (() -> Unit)?,
    onShare: (() -> Unit)?,
    style: EqualizerDesignStyle,
) {
    val preset = entry.preset
    val content = if (selected) Color.White else style.text
    val secondary = if (selected) Color.White.copy(alpha = 0.85f) else style.mutedText
    var showMenu by remember { mutableStateOf(false) }
    var showUsage by remember { mutableStateOf(false) }
    val enabled = inactiveReason == null
    val menu: @Composable (tint: Color) -> Unit = { tint ->
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.preset_more_actions, preset.name),
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
            // Built-ins stay unchangeable: no rename or delete, Duplicate makes an editable copy.
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.preset_usage_menu)) },
                    onClick = {
                        showMenu = false
                        showUsage = true
                    },
                )
                onDuplicate?.let { duplicate ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.preset_duplicate)) },
                        onClick = {
                            showMenu = false
                            duplicate()
                        },
                    )
                }
                onShare?.let { share ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.preset_share)) },
                        onClick = {
                            showMenu = false
                            share()
                        },
                    )
                }
            }
        }
    }

    if (LocalHardcoreLook.current) {
        HcPatchRow(entry, selected, inactiveReason, onClick, menu = { menu(HardcoreColors.ash) })
    } else {
        Surface(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .alpha(if (enabled) 1f else INACTIVE_PRESET_ALPHA)
                    .semantics {
                        contentDescription = preset.name
                        inactiveReason?.let { stateDescription = it }
                    },
            shape = MaterialTheme.shapes.medium,
            color = CyberColors.surfaceHigh,
            border =
                if (selected) {
                    BorderStroke(CyberDims.activeBorder, CyberBrushes.pillBorder)
                } else {
                    BorderStroke(CyberDims.border, CyberColors.outline.copy(alpha = 0.6f))
                },
        ) {
            Row(
                modifier =
                    Modifier
                        .then(if (selected) Modifier.background(CyberBrushes.pill) else Modifier)
                        .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = useCaseIcon(entry.useCase),
                    contentDescription = null,
                    tint = if (selected) Color.White else CyberColors.cyan,
                    modifier = Modifier.padding(top = 2.dp).size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = shortName(preset),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = content,
                        )
                        // The chosen preset is marked by a check as well as the fill.
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Text(text = entry.description, style = MaterialTheme.typography.bodySmall, color = secondary)
                    inactiveReason?.let { reason ->
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = style.caution,
                        )
                    }
                }
                menu(content)
            }
        }
    }

    if (showUsage) {
        AlertDialog(
            onDismissRequest = { showUsage = false },
            title = { Text(stringResource(R.string.preset_usage_title, preset.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = entry.description, style = MaterialTheme.typography.bodyMedium, color = style.text)
                    PresetUsageText(preset = preset, style = style)
                }
            },
            confirmButton = {
                TextButton(onClick = { showUsage = false }) {
                    Text(stringResource(R.string.preset_usage_close))
                }
            },
        )
    }
}

// Inside its genre's list the genre name is redundant: "Uptempo – Car" reads as "Car".
private fun shortName(preset: Preset): String = preset.name.substringAfter(" – ", preset.name)

private fun useCaseIcon(useCase: GenreUseCase?): ImageVector =
    when (useCase) {
        GenreUseCase.CAR -> Icons.Filled.DirectionsCar
        GenreUseCase.BLUETOOTH_SPEAKER -> Icons.Filled.Speaker
        GenreUseCase.HEADPHONES -> Icons.Filled.Headphones
        GenreUseCase.LOUD -> Icons.Filled.VolumeUp
        GenreUseCase.QUIET -> Icons.Filled.VolumeDown
        null -> Icons.Filled.Tune
    }
