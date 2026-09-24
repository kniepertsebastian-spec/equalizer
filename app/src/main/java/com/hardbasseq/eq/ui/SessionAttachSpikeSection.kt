package com.hardbasseq.eq.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import com.hardbasseq.eq.audio.spike.RootAudioStartResult
import com.hardbasseq.eq.audio.spike.RootSessionZeroProbeResult
import com.hardbasseq.eq.audio.spike.SessionAttachSpikeController
import com.hardbasseq.eq.audio.spike.SessionAttachSpikeResult
import com.hardbasseq.eq.audio.spike.SessionZeroProbeResult
import com.hardbasseq.eq.audio.spike.TestSignal
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
                        result = controller.run(TestSignal.SINE)
                        isPlaying = true
                        isBusy = false
                    }
                },
            ) {
                Text(stringResource(if (isBusy) R.string.spike_running else R.string.spike_start))
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                enabled = !isBusy,
                onClick = {
                    isBusy = true
                    scope.launch {
                        result = controller.run(TestSignal.SWEEP)
                        isPlaying = true
                        isBusy = false
                    }
                },
            ) {
                Text(stringResource(if (isBusy) R.string.spike_running else R.string.spike_start_sweep))
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

        Spacer(modifier = Modifier.height(24.dp))
        RootSessionZeroProbeSection(controller, scope)
    }
}

@Composable
private fun RootSessionZeroProbeSection(
    controller: SessionAttachSpikeController,
    scope: CoroutineScope,
) {
    var isBusy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RootSessionZeroProbeResult?>(null) }
    var audioResult by remember { mutableStateOf<RootAudioStartResult?>(null) }
    var bandGains by remember { mutableStateOf(emptyMap<Int, Float>()) }
    val rootBands = audioResult?.bands.orEmpty()

    DisposableEffect(controller) {
        onDispose { scope.launch { controller.stopRootEqualizer() } }
    }

    Column {
        Text("Root session-0 probe (experimental)", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Requests root through su, then starts a short-lived Android process as UID 0. " +
                "It only tries to construct Equalizer on session 0 and releases it immediately; " +
                "the effect is never enabled. This tests root access and session-0 creation, " +
                "not audible system-wide equalization.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = !isBusy,
            onClick = {
                isBusy = true
                scope.launch {
                    result = controller.probeRootSessionZero()
                    isBusy = false
                }
            },
        ) {
            Text(if (isBusy) "Waiting for root…" else "Probe session 0 as root")
        }
        result?.let { probe ->
            Spacer(modifier = Modifier.height(8.dp))
            val status =
                when {
                    !probe.rootGranted -> "ROOT NOT CONFIRMED"
                    probe.attachSucceeded -> "ROOT + ATTACH OK"
                    else -> "ROOT OK, ATTACH FAILED"
                }
            Text("[$status] ${probe.detail}", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Live system-mix test", style = MaterialTheme.typography.titleSmall)
        Text(
            "Starting this test enables the output-mix effect. It may change audio from every app. " +
                "Stop it here to release the effect.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Button(
                enabled = !isBusy && audioResult?.started != true,
                onClick = {
                    isBusy = true
                    scope.launch {
                        val start = controller.startRootEqualizer()
                        audioResult = start
                        bandGains = start.bands.associate { it.index to 0f }
                        isBusy = false
                    }
                },
            ) {
                Text(if (isBusy) "Waiting…" else "Start root output EQ")
            }
            if (audioResult?.started == true) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    enabled = !isBusy,
                    onClick = {
                        isBusy = true
                        scope.launch {
                            controller.stopRootEqualizer()
                            audioResult = null
                            bandGains = emptyMap()
                            isBusy = false
                        }
                    },
                ) {
                    Text("Stop")
                }
            }
        }
        audioResult?.let { start ->
            Text(
                if (start.started) "[ACTIVE] ${start.detail}" else "[FAILED] ${start.detail}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (audioResult?.started == true) {
            rootBands.forEach { band ->
                val gain = bandGains[band.index] ?: 0f
                Text("${band.centerFrequencyHz} Hz · ${"%.1f".format(gain)} dB", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = gain,
                    valueRange = band.minGainDb..band.maxGainDb,
                    onValueChange = { changed -> bandGains = bandGains + (band.index to changed) },
                    onValueChangeFinished = {
                        scope.launch { controller.setRootBandGain(band.index, bandGains[band.index] ?: 0f) }
                    },
                )
            }
        }
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
