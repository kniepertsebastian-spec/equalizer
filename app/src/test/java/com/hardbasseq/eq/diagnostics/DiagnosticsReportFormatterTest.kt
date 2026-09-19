package com.hardbasseq.eq.diagnostics

import com.hardbasseq.eq.audio.AudioCapabilities
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.audio.EqualizerBandCapabilities
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportFormatterTest {
    @Test
    fun generateReport_containsEssentialDiagnosticInfo() {
        val capabilities =
            AudioCapabilities(
                hasEqualizer = true,
                hasDynamicsProcessing = true,
                bands = listOf(EqualizerBandCapabilities(0, 60, -15f, 15f)),
            )
        val route = AudioRoute()

        val report =
            DiagnosticsReportFormatter.generateReport(
                engineState = AudioEngineState.Active(123),
                capabilities = capabilities,
                route = route,
            )

        assertTrue(report.contains("HardBass EQ Diagnostic Report"))
        assertTrue(report.contains("Has Equalizer: true"))
        assertTrue(report.contains("Has DynamicsProcessing: true"))
        assertTrue(report.contains("Band #0: 60 Hz"))
    }
}
