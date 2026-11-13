package com.hardbasseq.eq.diagnostics

import android.app.ApplicationExitInfo

/**
 * One entry of Android's own process-exit history (`ActivityManager.getHistoricalProcessExitReasons`,
 * API 30+). Unlike [InMemoryDiagnosticsRecorder] this survives a process restart, so the reason a
 * previous run died is still visible in the report after the app was killed (roadmap 06, P6-4).
 */
data class ProcessExitRecord(
    val timestampMillis: Long,
    val reason: String,
    val description: String?,
    val importance: Int,
)

/** Maps Android's `REASON_*` code to a stable, human-readable name. */
fun exitReasonName(reason: Int): String =
    when (reason) {
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> "UNKNOWN($reason)"
    }

/**
 * Whether this exit looks like a fault rather than the user or the system tidying up. Used to
 * pick the single "Last Exit" line in the report; the full list is printed too.
 */
fun ProcessExitRecord.isAbnormal(): Boolean =
    reason in
        setOf("ANR", "CRASH", "CRASH_NATIVE", "INITIALIZATION_FAILURE", "EXCESSIVE_RESOURCE_USAGE", "SIGNALED", "DEPENDENCY_DIED")

/**
 * What the platform reports about the active output, for the diagnostic report (roadmap 06, P5-8).
 *
 * Android has no public API for the negotiated Bluetooth codec (`BluetoothA2dp.getCodecStatus` is
 * hidden), so the report states the sample rates and encodings the output device announces, not
 * the negotiated codec. Everything may be empty on a given device.
 */
data class OutputAudioInfo(
    val deviceType: String,
    val isBluetooth: Boolean,
    val nativeSampleRateHz: Int?,
    val supportedSampleRatesHz: List<Int>,
    val encodings: List<String>,
)
