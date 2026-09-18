package com.hardbasseq.eq.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.spike.ControlIntentTestResult
import com.hardbasseq.eq.audio.spike.SessionAttachSpikeController
import com.hardbasseq.eq.audio.spike.SessionAttachSpikeResult
import com.hardbasseq.eq.audio.spike.SessionZeroProbeResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * M0 session-attach spike UI (roadmap §16, "Session-Attach-Spike"): starts a
 * self-owned test tone, attaches Equalizer/DynamicsProcessing to its
 * session, and shows the real capabilities this device reports. This is
 * throwaway diagnostic UI, not the app's future EQ screen (that's M3).
 */
@Composable
fun SessionAttachSpikeSection(controller: SessionAttachSpikeController) {
    var isPlaying by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SessionAttachSpikeResult?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(controller) {
        onDispose { scope.launch { controller.stop() } }
    }

    Column {
        Text(text = stringResource(R.string.spike_title), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = stringResource(R.string.spike_description), style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Button(
                enabled = !isBusy,
                onClick = {
                    isBusy = true
                    scope.launch {
                        result = controller.run()
                        isPlaying = true
                        isBusy = false
                    }
                },
            ) {
                Text(stringResource(if (isBusy) R.string.spike_running else R.string.spike_start))
            }
            if (isPlaying) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    scope.launch { controller.stop() }
                    isPlaying = false
                    result = null
                }) {
                    Text(stringResource(R.string.spike_stop))
                }
            }
        }

        result?.let { SpikeResultView(it) }

        Spacer(modifier = Modifier.height(24.dp))
        ControlIntentTestSection(controller, scope)

        Spacer(modifier = Modifier.height(24.dp))
        SessionZeroExperimentSection(controller, scope)
    }
}

@Composable
private fun ControlIntentTestSection(
    controller: SessionAttachSpikeController,
    scope: CoroutineScope,
) {
    var isBusy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ControlIntentTestResult?>(null) }

    Column {
        Text("Control-session intents (M0)", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Sends our own OPEN/CLOSE AudioEffect control-session broadcasts and checks " +
                "whether our own receiver round-trips them correctly. Only validates our own " +
                "receiver plumbing, not whether any real player app sends these on its own.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = !isBusy,
            onClick = {
                isBusy = true
                scope.launch {
                    result = controller.testControlIntents()
                    isBusy = false
                }
            },
        ) {
            Text(if (isBusy) "Running…" else "Send test open/close broadcasts")
        }
        result?.let { r ->
            Spacer(modifier = Modifier.height(8.dp))
            Text("Session ID used: ${r.audioSessionId}", style = MaterialTheme.typography.bodySmall)
            if (r.receivedEvents.isEmpty()) {
                Text("No broadcasts received back.", style = MaterialTheme.typography.bodySmall)
            } else {
                r.receivedEvents.forEach { event ->
                    Text(
                        "Received: ${event.action} session=${event.audioSessionId} pkg=${event.packageName}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionZeroExperimentSection(
    controller: SessionAttachSpikeController,
    scope: CoroutineScope,
) {
    var isBusy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SessionZeroProbeResult?>(null) }

    Column {
        Text("Session 0 experiment (M0, informational only)", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Roadmap §2: session 0 (global mix) insert effects are officially deprecated. This " +
                "only checks whether an Equalizer can be *constructed* on session 0 - it is never " +
                "enabled, so this cannot audibly change any playback. A success here is not a " +
                "supported feature.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = !isBusy,
            onClick = {
                isBusy = true
                scope.launch {
                    result = controller.probeSessionZero()
                    isBusy = false
                }
            },
        ) {
            Text(if (isBusy) "Running…" else "Probe session 0 (read-only)")
        }
        result?.let { r ->
            Spacer(modifier = Modifier.height(8.dp))
            val mark = if (r.attachSucceeded) "OK" else "FAIL"
            Text("[$mark] ${r.detail}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SpikeResultView(result: SessionAttachSpikeResult) {
    Spacer(modifier = Modifier.height(12.dp))
    Text("Audio session ID: ${result.audioSessionId}", style = MaterialTheme.typography.bodySmall)

    Spacer(modifier = Modifier.height(8.dp))
    Text("Equalizer", style = MaterialTheme.typography.titleSmall)
    val eq = result.equalizer
    if (eq != null) {
        Text(
            "Bands: ${eq.numberOfBands}, gain range: " +
                "${eq.minLevelMillibel / 100.0}..${eq.maxLevelMillibel / 100.0} dB",
            style = MaterialTheme.typography.bodySmall,
        )
        eq.bands.forEach { band ->
            Text(
                "Band ${band.index}: center ${band.centerFreqMilliHz / 1000} Hz " +
                    "(${band.minFreqMilliHz / 1000}–${band.maxFreqMilliHz / 1000} Hz)",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    } else {
        Text("Not available: ${result.equalizerError}", style = MaterialTheme.typography.bodySmall)
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text("DynamicsProcessing stages", style = MaterialTheme.typography.titleSmall)
    result.dynamicsProcessingStages.forEach { stage ->
        val mark = if (stage.succeeded) "OK" else "FAIL"
        Text(
            "[$mark] ${stage.stage}: ${stage.detail}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
