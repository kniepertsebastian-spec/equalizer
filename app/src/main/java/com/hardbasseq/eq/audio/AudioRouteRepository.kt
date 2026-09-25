package com.hardbasseq.eq.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AudioRouteRepository"

interface AudioRouteRepository {
    val activeRoute: StateFlow<AudioRoute>

    fun startMonitoring()

    fun stopMonitoring()
}

@Singleton
class AndroidAudioRouteRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : AudioRouteRepository {
        private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        private val _activeRoute = MutableStateFlow(detectCurrentRoute())
        override val activeRoute: StateFlow<AudioRoute> = _activeRoute.asStateFlow()

        private var isMonitoring = false

        private val deviceCallback =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    Log.d(TAG, "Audio devices added")
                    updateActiveRoute()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    Log.d(TAG, "Audio devices removed")
                    updateActiveRoute()
                }
            }

        override fun startMonitoring() {
            if (isMonitoring) return
            audioManager.registerAudioDeviceCallback(deviceCallback, null)
            isMonitoring = true
            updateActiveRoute()
        }

        override fun stopMonitoring() {
            if (!isMonitoring) return
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            isMonitoring = false
        }

        private fun updateActiveRoute() {
            _activeRoute.value = detectCurrentRoute()
        }

        private fun detectCurrentRoute(): AudioRoute {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in outputs) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_HEARING_AID,
                    -> {
                        val name = device.productName?.toString()?.ifBlank { null } ?: "Bluetooth Device"
                        return AudioRoute(
                            type = AudioDeviceType.BLUETOOTH,
                            name = name,
                            id = stableRouteFingerprint("bt", name),
                        )
                    }

                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_LINE_ANALOG,
                    AudioDeviceInfo.TYPE_LINE_DIGITAL,
                    -> {
                        return AudioRoute(
                            type = AudioDeviceType.WIRED_HEADPHONES,
                            name = "Wired Headphones",
                            id = "wired_headphones",
                        )
                    }

                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_ACCESSORY,
                    -> {
                        val name = device.productName?.toString()?.ifBlank { null } ?: "USB Audio"
                        return AudioRoute(
                            type = AudioDeviceType.USB,
                            name = name,
                            id = stableRouteFingerprint("usb", name),
                        )
                    }
                }
            }
            return AudioRoute(
                type = AudioDeviceType.SPEAKER,
                name = "Built-in Speaker",
                id = "speaker_internal",
            )
        }

        // roadmap-2026.md M3: "Datenschutzarmen, stabilen Route-Fingerprint definieren" -
        // used as DeviceProfileEntity's primary key, so it must survive reconnects and
        // reboots. AudioDeviceInfo.getId() looked like the obvious choice but its own
        // documentation says the ID is "not persistent across sessions"; the device may
        // get a different one on the very next connect. AudioDeviceInfo.getAddress()
        // would be stable, but reading a real Bluetooth MAC needs the runtime
        // BLUETOOTH_CONNECT permission (Android 12+) that this app doesn't otherwise
        // need - without it, most OEMs redact it to a placeholder anyway. Hashing
        // type+productName avoids both problems without a new permission: it's stable
        // for the same physical device and doesn't store/compare a raw identifier - the
        // one real tradeoff is that two identical-model headphones the user owns would
        // share one fingerprint/profile, an accepted edge case.
        private fun stableRouteFingerprint(
            prefix: String,
            productName: String,
        ): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("$prefix|$productName".toByteArray(Charsets.UTF_8))
            val shortHash = digest.take(8).joinToString("") { "%02x".format(it) }
            return "${prefix}_$shortHash"
        }
    }
