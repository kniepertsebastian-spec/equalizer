package com.hardbasseq.eq.ui

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
import androidx.compose.ui.graphics.Color
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
import com.hardbasseq.eq.ui.equalizer.EqualizerScreen
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
    val showSourcePicker by viewModel.showSourcePicker.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    var showSpikeSection by remember { mutableStateOf(false) }

    if (showSourcePicker) {
        PlayerSourcePickerDialog(
            onSourceChosen = { viewModel.choosePlayerSource(it) },
            onDismiss = { viewModel.dismissSourcePicker() },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
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
                onMasterToggled = { viewModel.setMasterEnabled(it) },
                onOpenSourcePicker = { viewModel.openSourcePicker() },
                onBypassToggled = { viewModel.setBypass(it) },
                onPresetSelected = { viewModel.selectPreset(it) },
                onMacroBassChanged = { viewModel.setMacroBass(it) },
                onMacroPunchChanged = { viewModel.setMacroPunch(it) },
                onMacroHaerteChanged = { viewModel.setMacroHaerte(it) },
                onBandGainChanged = { idx, gain -> viewModel.setBandGain(idx, gain) },
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
