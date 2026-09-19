package com.hardbasseq.eq.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.AudioEffectDescriptor
import com.hardbasseq.eq.audio.AudioEffectRepository
import com.hardbasseq.eq.audio.spike.SessionAttachSpikeController
import com.hardbasseq.eq.ui.main.MainViewModel

/**
 * First-start screen for M0/M1: app name, an (as yet unevaluated) compatibility
 * status placeholder, a toggle into the debug view that lists the raw
 * effect descriptors returned by [AudioEffectRepository], and a toggle into
 * the session-attach spike (roadmap §16). Real compatibility evaluation and
 * the production EQ engine land in M2/M3.
 *
 * All business logic (querying effects, dispatcher switching) lives in
 * [MainViewModel]; this composable only reads state and forwards events.
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    spikeController: SessionAttachSpikeController,
) {
    val showDebugEffects by viewModel.showDebugEffects.collectAsStateWithLifecycle()
    val descriptors by viewModel.effectDescriptors.collectAsStateWithLifecycle()
    var showSpikeSection by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            AssistChip(
                onClick = {},
                label = { Text(stringResource(R.string.compat_status_unknown)) },
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { viewModel.toggleDebugEffects() }) {
                Text(
                    stringResource(
                        if (showDebugEffects) R.string.hide_debug_effects else R.string.show_debug_effects,
                    ),
                )
            }

            if (showDebugEffects) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(R.string.debug_effects_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (descriptors.isEmpty()) {
                    Text(text = stringResource(R.string.debug_effects_empty))
                } else {
                    LazyColumn {
                        items(descriptors) { descriptor ->
                            EffectDescriptorRow(descriptor)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { showSpikeSection = !showSpikeSection }) {
                Text(
                    stringResource(
                        if (showSpikeSection) R.string.hide_spike_section else R.string.show_spike_section,
                    ),
                )
            }
            if (showSpikeSection) {
                Spacer(modifier = Modifier.height(16.dp))
                SessionAttachSpikeSection(controller = spikeController)
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
                .padding(vertical = 6.dp)
                .semantics { contentDescription = description },
    ) {
        Text(text = descriptor.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "${descriptor.implementor} · ${descriptor.connectMode} · ${descriptor.typeUuid}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
