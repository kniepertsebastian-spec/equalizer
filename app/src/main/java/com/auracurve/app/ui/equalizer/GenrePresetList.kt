package com.auracurve.app.ui.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.FiberManualRecord,
                contentDescription = null,
                tint = if (detected == ActivePlaybackSource.None) style.mutedText else style.signal,
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
            Surface(shape = MaterialTheme.shapes.medium, color = style.surfaceVariant) {
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

// Header row of a collapsible group. The chevron and the "active" note carry the state, so it
// does not depend on color alone.
@Composable
internal fun ExpandableGroupHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    highlighted: Boolean,
    onToggle: () -> Unit,
    style: EqualizerDesignStyle,
) {
    val stateText = stringResource(if (expanded) R.string.genre_presets_expanded else R.string.genre_presets_collapsed)
    Surface(
        onClick = onToggle,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics { stateDescription = stateText },
        shape = MaterialTheme.shapes.medium,
        color = style.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(style.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = style.accent, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
                    color = style.text,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (highlighted) style.accent else style.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = style.mutedText,
            )
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
        color = style.surfaceVariant,
    ) {
        Row(
            modifier =
                Modifier
                    .then(if (selected) Modifier.background(style.accentFill) else Modifier)
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = useCaseIcon(entry.useCase),
                contentDescription = null,
                tint = if (selected) Color.White else style.accent,
                modifier = Modifier.padding(top = 2.dp).size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = shortName(preset),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = content,
                )
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
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.preset_more_actions, preset.name),
                        tint = content,
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
