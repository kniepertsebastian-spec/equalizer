package com.auracurve.app.ui.equalizer

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Whatshot
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.correction.CorrectionProfile
import com.auracurve.app.preset.GenrePreset
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.PresetUsageAdvisor
import com.auracurve.app.preset.PresetUsageHint
import com.auracurve.app.ui.theme.PillShape
import com.auracurve.app.ui.theme.Spacing
import com.auracurve.app.ui.theme.spacing

// Genre picker: one card per genre with an icon, its name and its own curve, scrolling
// sideways so every genre is one tap away. The chosen card is filled with the accent gradient.
@Composable
internal fun GenreChoices(
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
            val content = if (isSelected) Color.White else style.text
            Surface(
                selected = isSelected,
                onClick = { onSelect(genre) },
                modifier =
                    Modifier
                        .width(112.dp)
                        .height(96.dp)
                        .semantics { contentDescription = genre.displayName },
                shape = MaterialTheme.shapes.medium,
                color = style.surfaceVariant,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(if (isSelected) Modifier.background(style.accentFill) else Modifier)
                            .padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White.copy(alpha = 0.18f) else style.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = genreIcon(genre.id),
                            contentDescription = null,
                            tint = if (isSelected) Color.White else style.accent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = genre.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MiniCurve(
                        points = genre.base.targetCurve,
                        color = if (isSelected) Color.White else style.accent,
                        modifier = Modifier.fillMaxWidth().height(18.dp),
                    )
                }
            }
        }
    }
}

// One icon per genre, so the cards can be told apart before the name is read.
internal fun genreIcon(genreId: String): ImageVector =
    when (genreId) {
        "genre_terror" -> Icons.Filled.Whatshot
        "genre_uptempo" -> Icons.Filled.Speed
        "genre_gabber" -> Icons.Filled.Bolt
        "genre_early" -> Icons.Filled.History
        "genre_frenchcore" -> Icons.Filled.GraphicEq
        "genre_dance" -> Icons.Filled.Nightlife
        "genre_pop" -> Icons.Filled.Mic
        "genre_rap" -> Icons.Filled.RecordVoiceOver
        "genre_flat" -> Icons.Filled.Remove
        else -> Icons.Filled.MusicNote
    }

// Three-per-row grid of the user's own presets. Static/chunked rather than
// LazyVerticalGrid since the list is small.
@Composable
internal fun PresetGrid(
    presets: List<Preset>,
    activePresetId: String,
    onPresetSelected: (Preset) -> Unit,
    onDuplicatePreset: (Preset) -> Unit,
    onRenamePresetRequest: (Preset) -> Unit,
    onDeletePresetRequest: (Preset) -> Unit,
    onSharePreset: (Preset) -> Unit,
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
                        onShare = { onSharePreset(preset) },
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
internal fun CorrectionProfileRow(
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
                shape = MaterialTheme.shapes.medium,
                color = if (selected) style.accent.copy(alpha = 0.16f) else style.surfaceVariant,
                border = if (selected) BorderStroke(1.5.dp, style.accent) else null,
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
internal fun PresetCard(
    preset: Preset,
    selected: Boolean,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onRenameRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle,
) {
    val contentColor = if (selected) Color.White else style.text
    var showMenu by remember { mutableStateOf(false) }
    var showUsage by remember { mutableStateOf(false) }

    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = PillShape,
        color = style.surfaceVariant,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (selected) Modifier.background(style.accentFill) else Modifier)
                    .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = presetIcon(preset.id),
                contentDescription = null,
                tint = if (selected) Color.White else style.accent,
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

    if (showUsage) {
        AlertDialog(
            onDismissRequest = { showUsage = false },
            title = { Text(stringResource(R.string.preset_usage_title, preset.name)) },
            text = { PresetUsageText(preset = preset, style = style) },
            confirmButton = {
                TextButton(onClick = { showUsage = false }) {
                    Text(stringResource(R.string.preset_usage_close))
                }
            },
        )
    }
}

/**
 * When a preset is a good choice, read from its own values (PresetUsageAdvisor): one line per
 * hint, then the strongest boost, which tells why the built-in player gets quieter.
 */
@Composable
internal fun PresetUsageText(
    preset: Preset,
    style: EqualizerDesignStyle,
    modifier: Modifier = Modifier,
) {
    val usage = remember(preset) { PresetUsageAdvisor.describe(preset) }
    val lines =
        usage.hints.map { stringResource(it.textRes()) }.ifEmpty { listOf(stringResource(R.string.preset_usage_all_rounder)) } +
            if (usage.strongestBoostDb >=
                1f
            ) {
                listOf(stringResource(R.string.preset_usage_strongest, usage.strongestBoostDb))
            } else {
                emptyList()
            }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            Text(text = "• $line", color = style.mutedText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun PresetUsageHint.textRes(): Int =
    when (this) {
        PresetUsageHint.NEUTRAL -> R.string.preset_usage_neutral
        PresetUsageHint.VERY_STRONG_BOOST -> R.string.preset_usage_very_strong
        PresetUsageHint.KICK_FOCUS -> R.string.preset_usage_kick
        PresetUsageHint.SUB_FOCUS -> R.string.preset_usage_sub
        PresetUsageHint.QUIET_LISTENING -> R.string.preset_usage_quiet
        PresetUsageHint.SMALL_DRIVERS -> R.string.preset_usage_small_drivers
        PresetUsageHint.SOFTENED_HIGHS -> R.string.preset_usage_softened_highs
    }

internal fun presetIcon(presetId: String): ImageVector =
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
