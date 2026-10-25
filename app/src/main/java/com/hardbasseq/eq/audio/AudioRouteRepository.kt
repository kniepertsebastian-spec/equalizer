package com.hardbasseq.eq.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.hardbasseq.eq.context.SoundContext
import com.hardbasseq.eq.context.SoundContextClassifier
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
            val candidates =
                audioManager
                    .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .map { RouteCandidate(it.type, it.productName?.toString()?.ifBlank { null }) }
            val inCommunication =
                audioManager.mode == AudioManager.MODE_IN_COMMUNICATION || audioManager.mode == AudioManager.MODE_IN_CALL
            val picked = RouteSelection.pick(candidates, mediaDeviceType(), inCommunication)
            return picked?.let(::toRoute) ?: AudioRoute(
                type = AudioDeviceType.SPEAKER,
                name = "Built-in Speaker",
                id = "speaker_internal",
            )
        }

        // The device media really plays on (API 33+); null when unknown or empty.
        private fun mediaDeviceType(): Int? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
            return audioManager.getAudioDevicesForAttributes(attributes).firstOrNull()?.type
        }

        private fun toRoute(device: RouteCandidate): AudioRoute {
            val type = device.type
            return when {
                RouteSelection.isBluetooth(type) -> {
                    val name = device.productName ?: "Bluetooth Device"
                    val routeType =
                        if (type == RouteSelection.TYPE_BLE_SPEAKER) {
                            // Android reports this type reliably, no name check needed.
                            AudioDeviceType.BLUETOOTH_SPEAKER
                        } else {
                            when (SoundContextClassifier.classifyBluetoothName(name)) {
                                SoundContext.CAR -> AudioDeviceType.CAR
                                SoundContext.BLUETOOTH_SPEAKER -> AudioDeviceType.BLUETOOTH_SPEAKER
                                null -> AudioDeviceType.BLUETOOTH
                            }
                        }
                    // The id stays "bt_<hash of name>" whatever the detected type or
                    // transport (Classic or LE), so a profile saved for this device keeps
                    // matching even if the classifier's keyword list changes later.
                    AudioRoute(type = routeType, name = name, id = stableRouteFingerprint("bt", name))
                }

                // Android Automotive's own output bus: a built-in car audio system
                // rather than a phone connected to one.
                type == RouteSelection.TYPE_BUS ->
                    AudioRoute(type = AudioDeviceType.CAR, name = device.productName ?: "Car Audio", id = "car_bus")

                RouteSelection.isUsb(type) -> {
                    val name = device.productName ?: "USB Audio"
                    AudioRoute(type = AudioDeviceType.USB, name = name, id = stableRouteFingerprint("usb", name))
                }

                else ->
                    AudioRoute(
                        type = AudioDeviceType.WIRED_HEADPHONES,
                        name = "Wired Headphones",
                        id = "wired_headphones",
                    )
            }
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
