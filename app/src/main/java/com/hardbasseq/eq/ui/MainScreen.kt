package com.hardbasseq.eq.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.AudioEffectDescriptor
import com.hardbasseq.eq.audio.spike.SessionAttachSpikeController
import com.hardbasseq.eq.integration.PlayerSource
import com.hardbasseq.eq.preset.Preset
import com.hardbasseq.eq.preset.PresetDesign
import com.hardbasseq.eq.ui.equalizer.EqualizerScreen
import com.hardbasseq.eq.ui.equalizer.styleFor
import com.hardbasseq.eq.ui.main.MainViewModel
import com.hardbasseq.eq.ui.theme.HardBassCardBorder
import com.hardbasseq.eq.ui.theme.spacing
import com.soundcloud.equalizer.player.playback.NowPlaying

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    spikeController: SessionAttachSpikeController,
    onNavigateToDiagnostics: () -> Unit = {},
) {
    val showDebugEffects by viewModel.showDebugEffects.collectAsStateWithLifecycle()
    val descriptors by viewModel.effectDescriptors.collectAsStateWithLifecycle()

    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val route by viewModel.currentRoute.collectAsStateWithLifecycle()
    val settings by viewModel.processingSettings.collectAsStateWithLifecycle()
    val activePreset by viewModel.activePreset.collectAsStateWithLifecycle()
    val allPresets by viewModel.allPresets.collectAsStateWithLifecycle()
    val activeCorrectionProfile by viewModel.activeCorrectionProfile.collectAsStateWithLifecycle()
    val allCorrectionProfiles by viewModel.allCorrectionProfiles.collectAsStateWithLifecycle()
    val suggestedCorrectionProfile by viewModel.suggestedCorrectionProfile.collectAsStateWithLifecycle()
    val effectiveHeadphoneAcoustics by viewModel.effectiveHeadphoneAcoustics.collectAsStateWithLifecycle()
    val pendingImportPreview by viewModel.pendingImportPreview.collectAsStateWithLifecycle()
    val importError by viewModel.importError.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    val pendingDeletePreset by viewModel.pendingDeletePreset.collectAsStateWithLifecycle()
    val showSourcePicker by viewModel.showSourcePicker.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    var showSpikeSection by remember { mutableStateOf(false) }
    var showSaveAsNewDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Preset?>(null) }

    val context = LocalContext.current
    // M5 "Import, Export und Teilen ... über Android Storage Access Framework/
    // Share Sheet integrieren" - OpenDocument shows the system file picker (any
    // storage provider, no runtime storage permission needed) and hands back a
    // content:// Uri; the file's text is read here (this composable has the
    // Context/ContentResolver access) and handed to the ViewModel, which owns
    // parsing/validation.
    val importCorrectionProfileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val text =
                runCatching {
                    context.contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                }.getOrNull()
            if (text.isNullOrBlank()) {
                return@rememberLauncherForActivityResult
            }
            val sourceName = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Importiertes Profil"
            viewModel.previewCorrectionProfileImport(sourceName, text)
        }

    if (showSourcePicker) {
        PlayerSourcePickerDialog(
            onSourceChosen = { viewModel.choosePlayerSource(it) },
            onDismiss = { viewModel.dismissSourcePicker() },
        )
    }

    if (showSaveAsNewDialog) {
        PresetNameDialog(
            title = "Als neues Preset speichern",
            initialName = "",
            onConfirm = { name ->
                viewModel.saveAsNewPreset(name)
                showSaveAsNewDialog = false
            },
            onDismiss = { showSaveAsNewDialog = false },
        )
    }

    renameTarget?.let { target ->
        PresetNameDialog(
            title = "Preset umbenennen",
            initialName = target.name,
            onConfirm = { name ->
                viewModel.renamePreset(target, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    pendingDeletePreset?.let { target ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeletePreset() },
            title = { Text("Preset löschen?") },
            text = { Text("\"${target.name}\" wird dauerhaft gelöscht.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDeletePreset() }) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeletePreset() }) {
                    Text("Abbrechen")
                }
            },
        )
    }

    // M5 "Vor dem Anwenden ist die resultierende Kurve sichtbar" / "Extreme
    // Boosts werden nicht still angewandt, sondern begrenzt oder bestätigt" -
    // nothing is saved until this dialog's "Importieren" is pressed.
    pendingImportPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelCorrectionProfileImport() },
            title = { Text(preview.profile.name) },
            text = {
                Column {
                    Text("Quelle: ${preview.profile.sourceLabel}")
                    Text(
                        "Frequenzbereich: ${preview.minFreqHz.toInt()}–${preview.maxFreqHz.toInt()} Hz, " +
                            "${preview.profile.curve.size} Punkte",
                    )
                    Text("Maximaler Boost: +${String.format("%.1f", preview.maxBoostDb)} dB")
                    Text("Benötigter Headroom: ${String.format("%.1f", preview.requiredHeadroomDb)} dB")
                    if (preview.isExtremeBoost) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Dieser Boost ist ungewöhnlich stark - bitte prüfen, bevor du importierst.",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmCorrectionProfileImport() }) {
                    Text("Importieren")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelCorrectionProfileImport() }) {
                    Text("Abbrechen")
                }
            },
        )
    }

    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportError() },
            title = { Text("Import fehlgeschlagen") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissImportError() }) {
                    Text("OK")
                }
            },
        )
    }

    Scaffold(
        containerColor = styleFor(PresetDesign.forPreset(activePreset)).background,
        bottomBar = {
            nowPlaying?.let { playing ->
                NowPlayingBar(
                    nowPlaying = playing,
                    onTogglePlayback = { viewModel.toggleNowPlayingPlayback() },
                    onOpenPlayer = { viewModel.reopenPlayer() },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            EqualizerScreen(
                state = engineState,
                route = route,
                settings = settings,
                bands = capabilities.bands,
                activePreset = activePreset,
                allPresets = allPresets,
                activeCorrectionProfile = activeCorrectionProfile,
                allCorrectionProfiles = allCorrectionProfiles,
                suggestedCorrectionProfile = suggestedCorrectionProfile,
                effectiveHeadphoneAcoustics = effectiveHeadphoneAcoustics,
                isDirty = isDirty,
                onMasterToggled = { viewModel.setMasterEnabled(it) },
                onOpenSourcePicker = { viewModel.openSourcePicker() },
                onBypassToggled = { viewModel.setBypass(it) },
                onPresetSelected = { viewModel.selectPreset(it) },
                onCorrectionProfileSelected = { viewModel.selectCorrectionProfile(it) },
                onAcceptSuggestedCorrectionProfile = { viewModel.acceptSuggestedCorrectionProfile() },
                onDismissSuggestedCorrectionProfile = { viewModel.dismissSuggestedCorrectionProfile() },
                onHeadphoneAcousticsChanged = { viewModel.setHeadphoneAcousticsOverride(it) },
                onImportCorrectionProfileRequested = {
                    // "*/*" rather than a specific text MIME type: AutoEQ files are
                    // typically .txt/.csv, but different file managers/providers
                    // report inconsistent MIME types for those (some report
                    // application/octet-stream) - permissive here, AutoEqParser
                    // itself already validates the actual content.
                    importCorrectionProfileLauncher.launch(arrayOf("*/*"))
                },
                onExportCorrectionProfile = { profile ->
                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, viewModel.exportCorrectionProfileJson(profile))
                            putExtra(Intent.EXTRA_SUBJECT, profile.name)
                        }
                    context.startActivity(Intent.createChooser(shareIntent, "Korrekturprofil teilen"))
                },
                onResetToActivePreset = { viewModel.resetToActivePreset() },
                onSaveAsNewRequest = { showSaveAsNewDialog = true },
                onDuplicatePreset = { viewModel.duplicatePreset(it) },
                onRenamePresetRequest = { renameTarget = it },
                onDeletePresetRequest = { viewModel.requestDeletePreset(it) },
                onMacroBassChanged = { viewModel.setMacroBass(it) },
                onMacroPunchChanged = { viewModel.setMacroPunch(it) },
                onMacroHaerteChanged = { viewModel.setMacroHaerte(it) },
                onBandGainChanged = { idx, gain -> viewModel.setBandGain(idx, gain) },
                onRetryAttach = { viewModel.retryAttach() },
                modifier = Modifier.weight(1f),
            )

            // Diagnostics and Debug Tools bar. Horizontally scrollable because the
            // German labels (e.g. "Session-Attach-Spike anzeigen (M0)") don't fit
            // three buttons on a single screen width - without scrolling, the third
            // button was pushed off-screen and unreachable.
            Row(
                modifier =
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                OutlinedButton(onClick = onNavigateToDiagnostics) {
                    Text("Diagnose & Report")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = { viewModel.toggleDebugEffects() }) {
                    Text(
                        stringResource(
                            if (showDebugEffects) R.string.hide_debug_effects else R.string.show_debug_effects,
                        ),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = { showSpikeSection = !showSpikeSection }) {
                    Text(
                        stringResource(
                            if (showSpikeSection) R.string.hide_spike_section else R.string.show_spike_section,
                        ),
                    )
                }
            }

            if (showDebugEffects) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(text = stringResource(R.string.debug_effects_title), style = MaterialTheme.typography.titleMedium)
                    if (descriptors.isEmpty()) {
                        Text(text = stringResource(R.string.debug_effects_empty))
                    } else {
                        LazyColumn(modifier = Modifier.height(100.dp)) {
                            items(descriptors) { descriptor ->
                                EffectDescriptorRow(descriptor)
                            }
                        }
                    }
                }
            }

            if (showSpikeSection) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SessionAttachSpikeSection(controller = spikeController)
                }
            }
        }
    }
}

@Composable
private fun PlayerSourcePickerDialog(
    onSourceChosen: (PlayerSource) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Play from...") },
        text = { Text("No audio session found yet. Start the player so HardBass EQ has something to process.") },
        confirmButton = {
            TextButton(onClick = { onSourceChosen(PlayerSource.SOUNDCLOUD) }) {
                Text("SoundCloud")
            }
        },
        dismissButton = {
            TextButton(onClick = { onSourceChosen(PlayerSource.YOUTUBE) }) {
                Text("YouTube")
            }
        },
    )
}

// Shared by "als neues Preset speichern" and "umbenennen" - both are just "give this
// preset a name", differing only in title/initial value/what happens on confirm.
@Composable
private fun PresetNameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
    )
}

// Small persistent "now playing" strip for the in-app SoundCloud player, docked to
// the bottom like Spotify's own mini-player - reflects AudioPlayerService's state
// (see NowPlayingState) without needing PlayerActivity's full track-browsing screen
// open. Tapping it reopens that screen; the play/pause button controls playback
// directly, without leaving the equalizer.
@Composable
private fun NowPlayingBar(
    nowPlaying: NowPlaying,
    onTogglePlayback: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Card(
        onClick = onOpenPlayer,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.small),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, HardBassCardBorder),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.medium, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nowPlaying.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = nowPlaying.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(spacing.small))
            IconButton(onClick = onTogglePlayback) {
                Icon(
                    imageVector = if (nowPlaying.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (nowPlaying.isPlaying) "Pause" else "Abspielen",
                )
            }
        }
    }
}

@Composable
private fun EffectDescriptorRow(descriptor: AudioEffectDescriptor) {
    val description = "${descriptor.name}, ${descriptor.implementor}, ${descriptor.connectMode}"
    Column(
        modifier =
            Modifier
                .padding(vertical = 4.dp)
                .semantics { contentDescription = description },
    ) {
        Text(text = descriptor.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "${descriptor.implementor} · ${descriptor.connectMode} · ${descriptor.typeUuid}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
