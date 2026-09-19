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
                        return AudioRoute(
                            type = AudioDeviceType.BLUETOOTH,
                            name = device.productName?.toString()?.ifBlank { null } ?: "Bluetooth Device",
                            id = "bt_${device.id}",
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
                            id = "wired_${device.id}",
                        )
                    }

                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_ACCESSORY,
                    -> {
                        return AudioRoute(
                            type = AudioDeviceType.USB,
                            name = device.productName?.toString()?.ifBlank { null } ?: "USB Audio",
                            id = "usb_${device.id}",
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
    }
