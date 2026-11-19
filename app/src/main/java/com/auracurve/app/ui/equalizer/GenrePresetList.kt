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
import androidx.compose.material.icons.filled.Headphones
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
import com.auracurve.app.preset.GenrePresetGroup
import com.auracurve.app.preset.GenreUseCase
import com.auracurve.app.preset.GenreVariant
import com.auracurve.app.preset.Preset

// Every built-in preset, grouped by genre: one collapsed row per genre, opening into its
// presets (base, character variants, use cases), each with the text saying when it fits best.
// Several genres can be open at once; the genre holding the active preset starts open.
@Composable
internal fun GenrePresetList(
    groups: List<GenrePresetGroup>,
    activePresetId: String,
    onPresetSelected: (Preset) -> Unit,
    onDuplicatePreset: (Preset) -> Unit,
    onSharePreset: (Preset) -> Unit,
    style: EqualizerDesignStyle,
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
                            onClick = { onPresetSelected(entry.preset) },
                            onDuplicate = { onDuplicatePreset(entry.preset) },
                            onShare = { onSharePreset(entry.preset) },
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
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    style: EqualizerDesignStyle,
) {
    val preset = entry.preset
    val content = if (selected) Color.White else style.text
    val secondary = if (selected) Color.White.copy(alpha = 0.85f) else style.mutedText
    var showMenu by remember { mutableStateOf(false) }
    var showUsage by remember { mutableStateOf(false) }

    Surface(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = preset.name },
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
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.preset_duplicate)) },
                        onClick = {
                            showMenu = false
                            onDuplicate()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.preset_share)) },
                        onClick = {
                            showMenu = false
                            onShare()
                        },
                    )
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
