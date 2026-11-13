package com.hardbasseq.eq.diagnostics

import android.app.ApplicationExitInfo
import com.hardbasseq.eq.BuildConfig
import com.hardbasseq.eq.audio.AudioCapabilities
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.audio.EqualizerBandCapabilities
import com.hardbasseq.eq.audio.ProcessingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportFormatterTest {
    private val capabilities =
        AudioCapabilities(
            hasEqualizer = true,
            hasDynamicsProcessing = true,
            hasInputGain = true,
            hasLimiter = true,
            hasMbc = true,
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

        assertTrue(report.contains("Baschtls Gedröhne Diagnostic Report"))
        assertTrue(report.contains("Has Equalizer: true"))
        assertTrue(report.contains("Has DynamicsProcessing: true"))
        assertTrue(report.contains("Band #0: 60 Hz"))
    }

    @Test
    fun generateReport_statesEqStatusSessionPlayerAndLastError() {
        val active =
            DiagnosticsReportFormatter.generateReport(
                engineState = AudioEngineState.Active(123),
                capabilities = capabilities,
                route = route,
                playerSource = "SOUNDCLOUD",
            )
        assertTrue(active.contains("EQ Status: ACTIVE"))
        assertTrue(active.contains("Audio Session: attached to session 123"))
        assertTrue(active.contains("Player: SOUNDCLOUD"))
        assertTrue(active.contains("Last Error: none"))

        val failed =
            DiagnosticsReportFormatter.generateReport(
                engineState = AudioEngineState.Error("effect rejected"),
                capabilities = capabilities,
                route = route,
            )
        assertTrue(failed.contains("EQ Status: ERROR"))
        assertTrue(failed.contains("Last Error: effect rejected"))
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
    fun generateReport_includesActiveDspStagesAndDistinguishesAppliedFromWaiting() {
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

        val activeReport =
            DiagnosticsReportFormatter.generateReport(
                engineState = AudioEngineState.Active(1),
                capabilities = capabilities,
                route = route,
                processingSettings = settings,
            )

        assertTrue(activeReport.contains("Active DSP Stages"))
        assertTrue(activeReport.contains("Hardware Processing Applied: true"))
        assertTrue(activeReport.contains("Limiter Enabled: true (threshold -1.5 dB, applied=true)"))
        assertTrue(activeReport.contains("MBC Enabled: true (threshold -7.0 dB, ratio 2.5:1, applied=true)"))

        val waitingReport =
            DiagnosticsReportFormatter.generateReport(
                engineState = AudioEngineState.Listening,
                capabilities = AudioCapabilities(hasEqualizer = true, hasDynamicsProcessing = true),
                route = route,
                processingSettings = settings,
            )
        assertTrue(waitingReport.contains("Hardware Processing Applied: false"))
        assertTrue(waitingReport.contains("applied=false"))
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

    @Test
    fun generateReport_saysWhetherTheHighResolutionEqWasRequestedAndWhyItIsUnavailable() {
        val report =
            DiagnosticsReportFormatter.generateReport(
                engineState = AudioEngineState.Active(1),
                capabilities = capabilities.copy(preEqUnavailableReason = "IllegalArgumentException: too many bands"),
                route = route,
                processingSettings = ProcessingSettings(highResolutionEq = true),
            )

        assertTrue(report.contains("EQ Path: classic 1-band"))
        assertTrue(report.contains("High-resolution EQ requested: true"))
        assertTrue(report.contains("High-resolution EQ unavailable: IllegalArgumentException: too many bands"))
    }

    @Test
    fun generateReport_listsProcessExitsAndNamesTheLastFault() {
        val report =
            DiagnosticsReportFormatter.generateReport(
                engineState = AudioEngineState.Active(1),
                capabilities = capabilities,
                route = route,
                processExits =
                    listOf(
                        ProcessExitRecord(2_000_000L, "USER_REQUESTED", null, 100),
                        ProcessExitRecord(1_000_000L, "CRASH", "ForegroundServiceStartNotAllowedException", 400),
                    ),
            )

        assertTrue(report.contains("Last Process Exit: CRASH at"))
        assertTrue(report.contains("--- Process Exit History"))
        assertTrue(report.contains("CRASH - ForegroundServiceStartNotAllowedException"))
        assertTrue(report.contains("USER_REQUESTED"))
    }

    @Test
    fun generateReport_saysNothingWasRecordedWithoutExits() {
        val report =
            DiagnosticsReportFormatter.generateReport(AudioEngineState.Active(1), capabilities, route)

        assertTrue(report.contains("Last Process Exit: none recorded"))
        assertFalse(report.contains("Process Exit History"))
    }

    @Test
    fun generateReport_marksHarmlessExitAsNotAFault() {
        val report =
            DiagnosticsReportFormatter.generateReport(
                engineState = AudioEngineState.Active(1),
                capabilities = capabilities,
                route = route,
                processExits = listOf(ProcessExitRecord(1_000_000L, "USER_REQUESTED", null, 100)),
            )

        assertTrue(report.contains("USER_REQUESTED at"))
        assertTrue(report.contains("(not a fault)"))
    }

    @Test
    fun generateReport_showsBluetoothSampleRateEncodingsAndCodecLimit() {
        val report =
            DiagnosticsReportFormatter.generateReport(
                engineState = AudioEngineState.Active(1),
                capabilities = capabilities,
                route = route,
                outputAudio =
                    OutputAudioInfo(
                        deviceType = "BLUETOOTH_A2DP",
                        isBluetooth = true,
                        nativeSampleRateHz = 48_000,
                        supportedSampleRatesHz = listOf(44_100, 48_000),
                        encodings = listOf("PCM_16"),
                    ),
            )

        assertTrue(report.contains("Output Device Type: BLUETOOTH_A2DP"))
        assertTrue(report.contains("Native Output Sample Rate: 48000 Hz"))
        assertTrue(report.contains("Bluetooth Sample Rates: 44100 Hz, 48000 Hz"))
        assertTrue(report.contains("Bluetooth Encodings: PCM_16"))
        assertTrue(report.contains("Bluetooth Codec: not readable"))
    }

    @Test
    fun generateReport_nonBluetoothOutputHasNoCodecLine() {
        val report =
            DiagnosticsReportFormatter.generateReport(
                engineState = AudioEngineState.Active(1),
                capabilities = capabilities,
                route = route,
                outputAudio = OutputAudioInfo("BUILTIN_SPEAKER", false, 48_000, emptyList(), emptyList()),
            )

        assertTrue(report.contains("Output Device Type: BUILTIN_SPEAKER"))
        assertFalse(report.contains("Bluetooth Codec"))
    }

    @Test
    fun exitReasonName_mapsKnownAndUnknownCodes() {
        assertEquals("CRASH", exitReasonName(ApplicationExitInfo.REASON_CRASH))
        assertEquals("LOW_MEMORY", exitReasonName(ApplicationExitInfo.REASON_LOW_MEMORY))
        assertEquals("UNKNOWN(999)", exitReasonName(999))
    }
}
