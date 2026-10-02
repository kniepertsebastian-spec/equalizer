package com.hardbasseq.eq.diagnostics

import android.os.Build
import com.hardbasseq.eq.BuildConfig
import com.hardbasseq.eq.audio.AudioCapabilities
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.audio.ProcessingSettings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val EVENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

object DiagnosticsReportFormatter {
    fun generateReport(
        engineState: AudioEngineState,
        capabilities: AudioCapabilities,
        route: AudioRoute,
        processingSettings: ProcessingSettings? = null,
        recentEvents: List<DiagnosticsEvent> = emptyList(),
        playerSource: String? = null,
    ): String =
        buildString {
            appendLine("=== HardBass EQ Diagnostic Report ===")
            appendLine("App Version: ${BuildConfig.VERSION_NAME}")
            appendLine("Android API Level: ${Build.VERSION.SDK_INT}")
            appendLine("Device Model: ${Build.MODEL}")
            appendLine("Device Fingerprint: ${Build.FINGERPRINT}")
            appendLine("Audio Output Route Type: ${route.type.name}")
            appendLine("Audio Output Name: ${route.name}")
            appendLine("Engine State: $engineState")
            appendLine("EQ Status: ${eqStatusLine(engineState, processingSettings)}")
            appendLine("Audio Session: ${sessionLine(engineState)}")
            appendLine("Player: ${playerSource ?: "unknown"}")
            appendLine("Last Error: ${lastError(engineState) ?: "none"}")
            appendLine()
            appendLine("--- Capabilities ---")
            appendLine("Has Equalizer: ${capabilities.hasEqualizer}")
            appendLine("Has DynamicsProcessing: ${capabilities.hasDynamicsProcessing}")
            appendLine("Has Input Gain: ${capabilities.hasInputGain}")
            appendLine("Has Limiter: ${capabilities.hasLimiter}")
            appendLine("Has Multiband Compressor: ${capabilities.hasMbc}")
            appendLine("Band Count: ${capabilities.bands.size}")
            capabilities.bands.forEach { band ->
                val freqLabel = if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000} kHz" else "${band.centerFreqHz} Hz"
                appendLine("  Band #${band.index}: $freqLabel [${band.minGainDb} dB .. ${band.maxGainDb} dB]")
            }
            if (processingSettings != null) {
                appendLine()
                appendLine("--- Active DSP Stages ---")
                appendLine("Master Enabled: ${processingSettings.masterEnabled}")
                appendLine("Bypass: ${processingSettings.bypass}")
                appendLine("Input Gain: ${processingSettings.inputGainDb} dB")
                appendLine("Limiter Enabled: ${processingSettings.limiterEnabled} (threshold ${processingSettings.limiterThresholdDb} dB)")
                appendLine(
                    "MBC Enabled: ${processingSettings.mbcEnabled} " +
                        "(threshold ${processingSettings.mbcThresholdDb} dB, ratio ${processingSettings.mbcRatio}:1)",
                )
            }
            if (recentEvents.isNotEmpty()) {
                appendLine()
                appendLine("--- Recent Session Events ---")
                recentEvents.forEach { event ->
                    val time = EVENT_TIME_FORMATTER.format(Instant.ofEpochMilli(event.timestampMillis))
                    appendLine("[$time] ${event.message}")
                }
            }
            appendLine("====================================")
        }

    private fun eqStatusLine(
        state: AudioEngineState,
        settings: ProcessingSettings?,
    ): String =
        when {
            settings != null && (!settings.masterEnabled || settings.bypass) -> "OFF (switched off or bypassed)"
            state is AudioEngineState.Active -> "ACTIVE (effect confirmed on the audio path)"
            state is AudioEngineState.Detached || state is AudioEngineState.Listening -> "WAITING (no audio session yet)"
            state is AudioEngineState.Attaching -> "CONNECTING"
            state is AudioEngineState.LostControl || state is AudioEngineState.Retrying -> "LOST (reconnecting)"
            state is AudioEngineState.Unsupported -> "UNSUPPORTED (no compatible audio session)"
            else -> "ERROR"
        }

    private fun sessionLine(state: AudioEngineState): String =
        when (state) {
            is AudioEngineState.Attaching -> "attaching to session ${state.sessionId}"
            is AudioEngineState.Active -> "attached to session ${state.sessionId}"
            is AudioEngineState.LostControl -> "lost session ${state.sessionId}"
            is AudioEngineState.Retrying -> "retrying session ${state.sessionId} (attempt ${state.attempt})"
            else -> "none"
        }

    private fun lastError(state: AudioEngineState): String? =
        when (state) {
            is AudioEngineState.LostControl -> state.reason
            is AudioEngineState.Unsupported -> state.reason
            is AudioEngineState.Error -> state.message
            else -> null
        }
}
