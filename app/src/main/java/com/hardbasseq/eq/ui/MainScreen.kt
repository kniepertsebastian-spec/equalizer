package com.hardbasseq.eq.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.AudioEffectDescriptor
import com.hardbasseq.eq.audio.spike.SessionAttachSpikeController
import com.hardbasseq.eq.ui.equalizer.EqualizerScreen
import com.hardbasseq.eq.ui.main.MainViewModel

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

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            EqualizerScreen(
                state = engineState,
                route = route,
                settings = settings,
                bands = capabilities.bands,
                activePreset = activePreset,
                onMasterToggled = { viewModel.setMasterEnabled(it) },
                onBypassToggled = { viewModel.setBypass(it) },
                onPresetSelected = { viewModel.selectPreset(it) },
                onMacroBassChanged = { viewModel.setMacroBass(it) },
                onMacroPunchChanged = { viewModel.setMacroPunch(it) },
                onMacroHaerteChanged = { viewModel.setMacroHaerte(it) },
                onBandGainChanged = { idx, gain -> viewModel.setBandGain(idx, gain) },
                modifier = Modifier.weight(1f),
            )

            // Diagnostics and Debug Tools bar
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
