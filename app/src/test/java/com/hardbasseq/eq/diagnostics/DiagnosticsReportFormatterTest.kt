package com.hardbasseq.eq.diagnostics

import com.hardbasseq.eq.BuildConfig
import com.hardbasseq.eq.audio.AudioCapabilities
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.audio.EqualizerBandCapabilities
import com.hardbasseq.eq.audio.ProcessingSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportFormatterTest {
    private val capabilities =
        AudioCapabilities(
            hasEqualizer = true,
            hasDynamicsProcessing = true,
            bands = listOf(EqualizerBandCapabilities(0, 60, -15f, 15f)),
        )
    private val route = AudioRoute()

    @Test
    fun generateReport_containsEssentialDiagnosticInfo() {
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

    @Test
    fun generateReport_usesRealAppVersionInsteadOfAHardcodedString() {
        val report =
            DiagnosticsReportFormatter.generateReport(
                engineState = AudioEngineState.Detached,
                capabilities = capabilities,
                route = route,
            )

        assertTrue(report.contains("App Version: ${BuildConfig.VERSION_NAME}"))
    }

    @Test
    fun generateReport_omitsDspAndEventSectionsWhenNotProvided() {
        val report =
            DiagnosticsReportFormatter.generateReport(
                engineState = AudioEngineState.Detached,
                capabilities = capabilities,
                route = route,
            )

        assertFalse(report.contains("Active DSP Stages"))
        assertFalse(report.contains("Recent Session Events"))
    }

    @Test
    fun generateReport_includesActiveDspStagesWhenProvided() {
        val settings =
            ProcessingSettings(
                masterEnabled = true,
                bypass = false,
                limiterEnabled = true,
                limiterThresholdDb = -1.5f,
                mbcEnabled = true,
                mbcThresholdDb = -7f,
                mbcRatio = 2.5f,
            )

        val report =
            DiagnosticsReportFormatter.generateReport(
                engineState = AudioEngineState.Active(1),
                capabilities = capabilities,
                route = route,
                processingSettings = settings,
            )

        assertTrue(report.contains("Active DSP Stages"))
        assertTrue(report.contains("Limiter Enabled: true (threshold -1.5 dB)"))
        assertTrue(report.contains("MBC Enabled: true (threshold -7.0 dB, ratio 2.5:1)"))
    }

    @Test
    fun generateReport_includesTimestampedRecentEvents() {
        val events =
            listOf(
                DiagnosticsEvent(timestampMillis = 0L, message = "Session attached: id=42 package=com.spotify.music"),
            )

        val report =
            DiagnosticsReportFormatter.generateReport(
                engineState = AudioEngineState.Active(42),
                capabilities = capabilities,
                route = route,
                recentEvents = events,
            )

        assertTrue(report.contains("Recent Session Events"))
        assertTrue(report.contains("Session attached: id=42 package=com.spotify.music"))
    }
}
