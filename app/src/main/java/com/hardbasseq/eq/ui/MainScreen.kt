package com.hardbasseq.eq.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.AudioEffectDescriptor
import com.hardbasseq.eq.integration.PlayerSource
import com.hardbasseq.eq.preset.BuiltInGenrePresets
import com.hardbasseq.eq.preset.Preset
import com.hardbasseq.eq.preset.PresetIntensity
import com.hardbasseq.eq.ui.equalizer.CurvePreview
import com.hardbasseq.eq.ui.equalizer.EqualizerScreen
import com.hardbasseq.eq.ui.equalizer.appLabelFor
import com.hardbasseq.eq.ui.equalizer.localizedSourceLabel
import com.hardbasseq.eq.ui.main.MainViewModel
import com.hardbasseq.eq.ui.share.SharePresetDialog
import com.hardbasseq.eq.ui.update.ReleaseUpdateNotice

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToDiagnostics: () -> Unit = {},
    // The built-in player has a track loaded (mini player and "Now playing" live in the app shell).
    playerLoaded: Boolean = false,
    onOpenServices: () -> Unit = {},
) {
    val showDebugEffects by viewModel.showDebugEffects.collectAsStateWithLifecycle()
    val descriptors by viewModel.effectDescriptors.collectAsStateWithLifecycle()

    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val audioSessions by viewModel.audioSessions.collectAsStateWithLifecycle()
    val activeAudioSession by viewModel.activeAudioSession.collectAsStateWithLifecycle()
    val appProfileBound by viewModel.appProfileBound.collectAsStateWithLifecycle()
    val isOwnPlayerSession by viewModel.isOwnPlayerSession.collectAsStateWithLifecycle()
    val deviceKindPrompt by viewModel.deviceKindPrompt.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val route by viewModel.displayRoute.collectAsStateWithLifecycle()
    val settings by viewModel.processingSettings.collectAsStateWithLifecycle()
    val activePreset by viewModel.activePreset.collectAsStateWithLifecycle()
    val customPresetsOnly by viewModel.customPresetsOnly.collectAsStateWithLifecycle()
    val selectedGenreIntensity by viewModel.selectedGenreIntensity.collectAsStateWithLifecycle()
    val activeCorrectionProfile by viewModel.activeCorrectionProfile.collectAsStateWithLifecycle()
    val allCorrectionProfiles by viewModel.allCorrectionProfiles.collectAsStateWithLifecycle()
    val suggestedCorrectionProfile by viewModel.suggestedCorrectionProfile.collectAsStateWithLifecycle()
    val effectiveHeadphoneAcoustics by viewModel.effectiveHeadphoneAcoustics.collectAsStateWithLifecycle()
    val headphonePower by viewModel.headphonePower.collectAsStateWithLifecycle()
    val activeGoal by viewModel.activeGoal.collectAsStateWithLifecycle()
    val currentLevelDb by viewModel.currentLevelDb.collectAsStateWithLifecycle()
    val activeContext by viewModel.activeContext.collectAsStateWithLifecycle()
    val pendingImportPreview by viewModel.pendingImportPreview.collectAsStateWithLifecycle()
    val importError by viewModel.importError.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    val pendingDeletePreset by viewModel.pendingDeletePreset.collectAsStateWithLifecycle()
    val showSourcePicker by viewModel.showSourcePicker.collectAsStateWithLifecycle()
    val compare by viewModel.compare.collectAsStateWithLifecycle()
    // Leaving the equalizer ends a running Original/EQ comparison, so the next track is not heard "as original" by accident.
    DisposableEffect(Unit) { onDispose { viewModel.endCompare() } }
    var showMoreTools by rememberSaveable { mutableStateOf(false) }
    var showSaveAsNewDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Preset?>(null) }
    var shareTarget by remember { mutableStateOf<Preset?>(null) }

    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    val resources = LocalResources.current
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
            val sourceName =
                uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                    ?: resources.getString(R.string.imported_profile_default)
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
            title = stringResource(R.string.preset_save_as_new),
            initialName = "",
            onConfirm = { name ->
                viewModel.saveAsNewPreset(name)
                showSaveAsNewDialog = false
            },
            onDismiss = { showSaveAsNewDialog = false },
        )
    }

    shareTarget?.let { target ->
        val link = remember(target) { viewModel.presetShareLink(target) }
        SharePresetDialog(
            presetName = target.name,
            link = link,
            onShareLink = {
                val send =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, link)
                        putExtra(Intent.EXTRA_SUBJECT, target.name)
                    }
                context.startActivity(Intent.createChooser(send, resources.getString(R.string.preset_share)))
            },
            onCopyLink = { clipboard.setPrimaryClip(ClipData.newPlainText(target.name, link)) },
            onDismiss = { shareTarget = null },
        )
    }

    renameTarget?.let { target ->
        PresetNameDialog(
            title = stringResource(R.string.preset_rename_title),
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
            title = { Text(stringResource(R.string.preset_delete_title)) },
            text = { Text(stringResource(R.string.preset_delete_text, target.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDeletePreset() }) {
                    Text(stringResource(R.string.preset_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeletePreset() }) {
                    Text(stringResource(R.string.action_cancel))
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
                    Text(stringResource(R.string.import_preview_source, localizedSourceLabel(preview.profile.sourceLabel)))
                    Text(
                        stringResource(
                            R.string.import_preview_range,
                            preview.minFreqHz.toInt(),
                            preview.maxFreqHz.toInt(),
                            preview.profile.curve.size,
                        ),
                    )
                    Text(stringResource(R.string.import_preview_boost, String.format("%.1f", preview.maxBoostDb)))
                    Text(stringResource(R.string.import_preview_headroom, String.format("%.1f", preview.requiredHeadroomDb)))
                    CurvePreview(curve = preview.profile.curve, modifier = Modifier.padding(top = 8.dp))
                    if (preview.isExtremeBoost) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.import_preview_extreme),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmCorrectionProfileImport() }) {
                    Text(stringResource(R.string.action_import))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelCorrectionProfileImport() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportError() },
            title = { Text(stringResource(R.string.import_failed_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissImportError() }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.feedback.collect { snackbarHostState.showSnackbar(it) } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The navigation bar of the app shell below handles the bottom inset.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ReleaseUpdateNotice()
            EqualizerScreen(
                state = engineState,
                route = route,
                settings = settings,
                bands = capabilities.bands,
                activePreset = activePreset,
                customPresets = customPresetsOnly,
                genrePresets = BuiltInGenrePresets.all,
                intensities = PresetIntensity.entries,
                selectedGenreIntensity = selectedGenreIntensity,
                activeCorrectionProfile = activeCorrectionProfile,
                allCorrectionProfiles = allCorrectionProfiles,
                suggestedCorrectionProfile = suggestedCorrectionProfile,
                effectiveHeadphoneAcoustics = effectiveHeadphoneAcoustics,
                onOpenServices = onOpenServices,
                onRenameRoute = { viewModel.renameCurrentRoute(it) },
                pathLabel =
                    activeAudioSession?.let { appLabelFor(context, it.packageName) }
                        ?: if (playerLoaded) stringResource(R.string.source_soundcloud) else null,
                audioSessions = audioSessions,
                activeAudioSession = activeAudioSession,
                appProfileBound = appProfileBound,
                onAppProfileBoundChanged = { viewModel.setAppProfileBound(it) },
                isOwnPlayerSession = isOwnPlayerSession,
                preEqActive = capabilities.preEqCutoffsHz.isNotEmpty(),
                onHighResolutionEqChanged = { viewModel.setHighResolutionEq(it) },
                preEqResponseMode =
                    try {
                        com.hardbasseq.eq.dsp.PreEqResponseMode
                            .valueOf(settings.preEqResponseMode)
                    } catch (e: Exception) {
                        com.hardbasseq.eq.dsp.PreEqResponseMode.DEFAULT
                    },
                onPreEqResponseModeChanged = { viewModel.setPreEqResponseMode(it) },
                deviceKindPrompt = deviceKindPrompt,
                onAnswerDeviceKind = { viewModel.answerDeviceKind(it) },
                onDismissDeviceKindPrompt = { viewModel.dismissDeviceKindPrompt() },
                onSelectAudioSession = { viewModel.selectAudioSession(it) },
                compare = compare,
                onStartCompare = { viewModel.startCompare() },
                onCompareSide = { viewModel.setCompareSide(it) },
                onEndCompare = { viewModel.endCompare() },
                headphonePower = headphonePower,
                activeGoal = activeGoal,
                onGoalSelected = { viewModel.setSoundGoal(it) },
                activeContext = activeContext,
                currentLevelDb = currentLevelDb,
                isDirty = isDirty,
                onMasterToggled = { viewModel.setMasterEnabled(it) },
                onOpenSourcePicker = { viewModel.openSourcePicker() },
                onBypassToggled = { viewModel.setBypass(it) },
                onPresetSelected = { viewModel.selectPreset(it) },
                onGenreIntensitySelected = { genre, intensity -> viewModel.selectGenreIntensity(genre, intensity) },
                onCorrectionProfileSelected = { viewModel.selectCorrectionProfile(it) },
                onAcceptSuggestedCorrectionProfile = { viewModel.acceptSuggestedCorrectionProfile() },
                onDismissSuggestedCorrectionProfile = { viewModel.dismissSuggestedCorrectionProfile() },
                onHeadphoneAcousticsChanged = { viewModel.setHeadphoneAcousticsOverride(it) },
                onHeadphonePowerChanged = { viewModel.setHeadphonePower(it) },
                onContextModeChanged = { viewModel.setContextMode(it) },
                onLoudnessCompensationChanged = { viewModel.setLoudnessCompensationEnabled(it) },
                onSubsonicFilterChanged = { viewModel.setSubsonicFilterEnabled(it) },
                onVirtualBassChanged = { viewModel.setVirtualBassEnabled(it) },
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
                    context.startActivity(Intent.createChooser(shareIntent, resources.getString(R.string.share_correction_profile)))
                },
                onResetToActivePreset = { viewModel.resetToActivePreset() },
                onSaveAsNewRequest = { showSaveAsNewDialog = true },
                onDuplicatePreset = { viewModel.duplicatePreset(it) },
                onRenamePresetRequest = { renameTarget = it },
                onDeletePresetRequest = { viewModel.requestDeletePreset(it) },
                onSharePreset = { shareTarget = it },
                onImportPresetLinkRequested = {
                    val clip =
                        clipboard.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                    viewModel.importPresetLink(clip.orEmpty())
                },
                onMacroBassChanged = { viewModel.setMacroBass(it) },
                onMacroPunchChanged = { viewModel.setMacroPunch(it) },
                onMacroHaerteChanged = { viewModel.setMacroHaerte(it) },
                onBandGainChanged = { idx, gain -> viewModel.setBandGain(idx, gain) },
                onRetryAttach = { viewModel.retryAttach() },
                modifier = Modifier.weight(1f),
            )

            // Diagnostics and debug tools stay out of the way: one collapsed entry, opened on
            // demand (Settings -> Diagnostics). The bar is horizontally scrollable because
            // the buttons don't fit one screen width.
            TextButton(
                onClick = { showMoreTools = !showMoreTools },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(stringResource(if (showMoreTools) R.string.more_tools_hide else R.string.more_tools_show))
            }
            if (showMoreTools) {
                Row(
                    modifier =
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    OutlinedButton(onClick = onNavigateToDiagnostics) {
                        Text(stringResource(R.string.diagnostics_button))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { viewModel.toggleDebugEffects() }) {
                        Text(
                            stringResource(
                                if (showDebugEffects) R.string.hide_debug_effects else R.string.show_debug_effects,
                            ),
                        )
                    }
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
        title = { Text(stringResource(R.string.source_picker_title)) },
        text = { Text(stringResource(R.string.source_picker_text)) },
        confirmButton = {
            TextButton(onClick = { onSourceChosen(PlayerSource.SOUNDCLOUD) }) {
                Text(stringResource(R.string.source_soundcloud))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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
                label = { Text(stringResource(R.string.field_name)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
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
