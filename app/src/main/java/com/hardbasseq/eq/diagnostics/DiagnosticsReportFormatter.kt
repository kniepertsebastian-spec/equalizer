package com.hardbasseq.eq.diagnostics

import android.os.Build
import com.hardbasseq.eq.audio.AudioCapabilities
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.AudioRoute

object DiagnosticsReportFormatter {

    fun generateReport(
        engineState: AudioEngineState,
        capabilities: AudioCapabilities,
        route: AudioRoute,
    ): String {
        return buildString {
            appendLine("=== HardBass EQ Diagnostic Report ===")
            appendLine("App Version: 0.6.0-m6")
            appendLine("Android API Level: ${Build.VERSION.SDK_INT}")
            appendLine("Device Model: ${Build.MODEL}")
            appendLine("Audio Output Route Type: ${route.type.name}")
            appendLine("Audio Output Name: ${route.name}")
            appendLine("Engine State: $engineState")
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
            appendLine("====================================")
        }
    }
}
