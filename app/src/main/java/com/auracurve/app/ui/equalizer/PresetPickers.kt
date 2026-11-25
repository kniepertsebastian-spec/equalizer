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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.correction.CorrectionProfile
import com.auracurve.app.eq.ActivePlaybackSource
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.PresetSourceScope
import com.auracurve.app.preset.PresetUsageAdvisor
import com.auracurve.app.preset.PresetUsageHint
import com.auracurve.app.ui.components.hardcore.HcLed
import com.auracurve.app.ui.components.hardcore.HcPadShape
import com.auracurve.app.ui.components.hardcore.HcText
import com.auracurve.app.ui.components.hardcore.hcPad
import com.auracurve.app.ui.theme.CyberBrushes
import com.auracurve.app.ui.theme.CyberColors
import com.auracurve.app.ui.theme.CyberDims
import com.auracurve.app.ui.theme.HardcoreColors
import com.auracurve.app.ui.theme.HardcoreType
import com.auracurve.app.ui.theme.HcCorner
import com.auracurve.app.ui.theme.LocalHardcoreLook
import com.auracurve.app.ui.theme.Spacing
import com.auracurve.app.ui.theme.hcKeyFace
import com.auracurve.app.ui.theme.spacing

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

// The color of a genre's icon badge (Design/DESIGN_SPEC.md §5B): a cue next to the name and the
// icon, never the only one.
internal fun genreTint(genreId: String): Color =
    when (genreId) {
        "genre_terror" -> CyberColors.orange
        "genre_uptempo" -> CyberColors.cyan
        "genre_gabber" -> CyberColors.yellow
        "genre_early" -> CyberColors.violet
        "genre_frenchcore" -> GenreBlue
        "genre_dance" -> CyberColors.orchid
        "genre_pop" -> GenreBlue
        "genre_rap" -> CyberColors.cyan
        "genre_flat" -> CyberColors.mutedText
        else -> CyberColors.accentText
    }

private val GenreBlue = Color(0xFF4A9BFF)

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
    // Roadmap 16: own presets saved for the other source are greyed out like the built-ins.
    detectedSource: ActivePlaybackSource = ActivePlaybackSource.None,
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
                        inactiveReason = detectedSource.inactiveReason(preset),
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
    // Hardcore: each profile is a rubber pad, pressed in with a lit lamp when it is the active one.
    val hardcore = LocalHardcoreLook.current
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        profiles.forEach { profile ->
            val selected = profile.id == activeProfileId
            val cyberColor = if (selected) CyberColors.cyan.copy(alpha = 0.10f) else CyberColors.surfaceHigh
            Surface(
                selected = selected,
                onClick = { onProfileSelected(profile) },
                modifier = Modifier.heightIn(min = 56.dp).then(if (hardcore) Modifier.hcPad(on = selected) else Modifier),
                shape = if (hardcore) HcPadShape else MaterialTheme.shapes.medium,
                color = if (hardcore) Color.Transparent else cyberColor,
                contentColor = if (hardcore) HardcoreColors.bone else contentColorFor(cyberColor),
                border =
                    when {
                        hardcore -> null
                        selected -> BorderStroke(CyberDims.activeBorder, CyberBrushes.pillBorder)
                        else -> BorderStroke(CyberDims.border, CyberColors.outline.copy(alpha = 0.6f))
                    },
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hardcore) {
                        HcLed(on = selected, modifier = Modifier.padding(end = 10.dp))
                    }
                    Column(modifier = Modifier.padding(end = 8.dp)) {
                        if (hardcore) {
                            HcText(text = profile.localizedName(), style = HardcoreType.pad, color = HardcoreColors.bone)
                        } else {
                            Text(
                                text = profile.localizedName(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = style.text,
                            )
                        }
                        Text(
                            text = profile.localizedSource(),
                            style = if (hardcore) HardcoreType.small else MaterialTheme.typography.bodySmall,
                            color = if (hardcore) HardcoreColors.ash else style.mutedText,
                        )
                        profile.deviceName?.let { device ->
                            Text(
                                text = stringResource(R.string.profile_for_device, device),
                                style = if (hardcore) HardcoreType.small else MaterialTheme.typography.bodySmall,
                                color = if (hardcore) HardcoreColors.ash else style.mutedText,
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
                                tint = if (hardcore) HardcoreColors.ash else style.mutedText,
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
    // Roadmap 16: why the preset does not take effect with what plays now (greyed out, not tappable).
    inactiveReason: String? = null,
) {
    // Hardcore: a key, pressed in with its icon lit red when the preset is the active one.
    val hardcore = LocalHardcoreLook.current
    val contentColor =
        when {
            hardcore -> HardcoreColors.bone
            selected -> Color.White
            else -> style.text
        }
    var showMenu by remember { mutableStateOf(false) }
    var showUsage by remember { mutableStateOf(false) }
    val enabled = inactiveReason == null

    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .height(56.dp)
                .alpha(if (enabled) 1f else INACTIVE_PRESET_ALPHA)
                .then(if (hardcore) Modifier.hcKeyFace(pressed = selected) else Modifier)
                .semantics { inactiveReason?.let { stateDescription = it } },
        shape = if (hardcore) HcCorner else MaterialTheme.shapes.medium,
        color = if (hardcore) Color.Transparent else CyberColors.surface,
        contentColor = if (hardcore) HardcoreColors.bone else contentColorFor(CyberColors.surface),
        border =
            when {
                hardcore -> null
                selected -> BorderStroke(CyberDims.activeBorder, CyberBrushes.pillBorder)
                else -> BorderStroke(CyberDims.border, CyberBrushes.border)
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (selected && !hardcore) Modifier.background(CyberBrushes.pill) else Modifier)
                    .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = presetIcon(preset.id),
                contentDescription = null,
                tint =
                    when {
                        hardcore -> if (selected) HardcoreColors.bloodHi else HardcoreColors.ash
                        selected -> Color.White
                        else -> CyberColors.cyan
                    },
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            if (hardcore) {
                HcText(
                    text = preset.name,
                    style = HardcoreType.key,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Box {
                // 48 dp target, so the menu is hit reliably next to the preset itself.
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.preset_more_actions, preset.name),
                        tint = if (hardcore) HardcoreColors.ash else contentColor,
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
 * Roadmap 16, P16-3: "Save as new preset" with the source the preset is for - own player,
 * Spotify & other apps, or both. Starts on [initialScope] (the detected source).
 */
@Composable
internal fun SavePresetDialog(
    initialScope: PresetSourceScope,
    onConfirm: (name: String, scope: PresetSourceScope) -> Unit,
    onDismiss: () -> Unit,
    style: EqualizerDesignStyle = SonicStyle,
) {
    var name by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(initialScope) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preset_save_as_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.field_name)) },
                )
                Text(
                    text = stringResource(R.string.preset_save_scope_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = style.text,
                )
                listOf(PresetSourceScope.OWN_PLAYER, PresetSourceScope.EXTERNAL_APPS, PresetSourceScope.ALL).forEach { option ->
                    ChoiceChip(
                        text = option.label(),
                        selected = option == scope,
                        onClick = { scope = option },
                        modifier = Modifier.fillMaxWidth(),
                        style = style,
                    )
                }
                Text(
                    text = stringResource(R.string.preset_save_scope_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = style.mutedText,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), scope) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
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
