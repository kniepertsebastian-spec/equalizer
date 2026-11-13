package com.hardbasseq.eq.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Platform facts that only the diagnostic report needs; an interface so tests need no Android. */
interface SystemDiagnosticsSource {
    /** Newest first, at most [limit] entries. Empty below Android 11 or if the platform refuses. */
    fun recentProcessExits(limit: Int = 5): List<ProcessExitRecord>

    /** Null if no output device is known. */
    fun outputAudioInfo(): OutputAudioInfo?

    /** Whether the optional DUMP permission (extended app detection, roadmap 06 P5-2) was granted over ADB. */
    fun extendedAppDetectionGranted(): Boolean
}

object NoSystemDiagnosticsSource : SystemDiagnosticsSource {
    override fun recentProcessExits(limit: Int): List<ProcessExitRecord> = emptyList()

    override fun outputAudioInfo(): OutputAudioInfo? = null

    override fun extendedAppDetectionGranted(): Boolean = false
}

@Singleton
class AndroidSystemDiagnosticsSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : SystemDiagnosticsSource {
        override fun recentProcessExits(limit: Int): List<ProcessExitRecord> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
            return runCatching {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                manager
                    .getHistoricalProcessExitReasons(context.packageName, 0, limit)
                    .map {
                        ProcessExitRecord(
                            timestampMillis = it.timestamp,
                            reason = exitReasonName(it.reason),
                            description = it.description?.ifBlank { null },
                            importance = it.importance,
                        )
                    }
            }.getOrDefault(emptyList())
        }

        override fun extendedAppDetectionGranted(): Boolean =
            context.checkSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED

        override fun outputAudioInfo(): OutputAudioInfo? =
            runCatching {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val device = outputs.firstOrNull { it.isBluetooth() } ?: outputs.firstOrNull()
                device?.let {
                    OutputAudioInfo(
                        deviceType = deviceTypeName(it.type),
                        isBluetooth = it.isBluetooth(),
                        nativeSampleRateHz = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull(),
                        supportedSampleRatesHz = it.sampleRates.sorted(),
                        encodings = it.encodings.map(::encodingName),
                    )
                }
            }.getOrNull()

        private fun AudioDeviceInfo.isBluetooth(): Boolean =
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET)

        private fun deviceTypeName(type: Int): String =
            when (type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
                else ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                        "BLE_HEADSET"
                    } else {
                        "TYPE_$type"
                    }
            }

        private fun encodingName(encoding: Int): String =
            when (encoding) {
                AudioFormat.ENCODING_PCM_16BIT -> "PCM_16"
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM_24"
                AudioFormat.ENCODING_PCM_32BIT -> "PCM_32"
                AudioFormat.ENCODING_PCM_FLOAT -> "PCM_FLOAT"
                AudioFormat.ENCODING_AC3 -> "AC3"
                AudioFormat.ENCODING_E_AC3 -> "E_AC3"
                AudioFormat.ENCODING_DTS -> "DTS"
                AudioFormat.ENCODING_AAC_LC -> "AAC_LC"
                AudioFormat.ENCODING_MP3 -> "MP3"
                else -> "ENCODING_$encoding"
            }
    }
