package com.hardbasseq.eq.audio

/** A reduced view of an `AudioDeviceInfo`, so the selection logic runs on the plain JVM. */
data class RouteCandidate(
    /** `AudioDeviceInfo.TYPE_*` constant. */
    val type: Int,
    val productName: String?,
)

/**
 * Decides which output device counts as "the route". Android lists every connected
 * output; the first match is not necessarily where media actually plays.
 */
object RouteSelection {
    // Raw values of the AudioDeviceInfo.TYPE_* constants. LE Audio types only exist
    // from API 31/33, but comparing plain ints is harmless on older devices.
    const val TYPE_BUILTIN_SPEAKER = 2
    const val TYPE_WIRED_HEADSET = 3
    const val TYPE_WIRED_HEADPHONES = 4
    const val TYPE_LINE_ANALOG = 5
    const val TYPE_LINE_DIGITAL = 6
    const val TYPE_BLUETOOTH_SCO = 7
    const val TYPE_BLUETOOTH_A2DP = 8
    const val TYPE_USB_DEVICE = 11
    const val TYPE_USB_ACCESSORY = 12
    const val TYPE_BUS = 21
    const val TYPE_USB_HEADSET = 22
    const val TYPE_HEARING_AID = 23
    const val TYPE_BLE_HEADSET = 26
    const val TYPE_BLE_SPEAKER = 27
    const val TYPE_BLE_BROADCAST = 30

    private val bluetoothTypes =
        setOf(TYPE_BLUETOOTH_A2DP, TYPE_BLE_HEADSET, TYPE_BLE_SPEAKER, TYPE_BLE_BROADCAST, TYPE_HEARING_AID)
    private val usbTypes = setOf(TYPE_USB_DEVICE, TYPE_USB_HEADSET, TYPE_USB_ACCESSORY)
    private val wiredTypes = setOf(TYPE_WIRED_HEADSET, TYPE_WIRED_HEADPHONES, TYPE_LINE_ANALOG, TYPE_LINE_DIGITAL)

    // Fallback order when Android does not name a media device.
    private val priority: List<Set<Int>> =
        listOf(bluetoothTypes, usbTypes, wiredTypes, setOf(TYPE_BUS))

    fun isBluetooth(type: Int): Boolean = type in bluetoothTypes || type == TYPE_BLUETOOTH_SCO

    fun isUsb(type: Int): Boolean = type in usbTypes

    /**
     * @param mediaDeviceType type of the device Android reports as the media target
     *   (API 33+ `getAudioDevicesForAttributes`), or null if unknown.
     * @param inCommunication true while the audio mode is a call; only then does a
     *   lone SCO link count as a route.
     * @return the chosen candidate, or null for the built-in speaker.
     */
    fun pick(
        candidates: List<RouteCandidate>,
        mediaDeviceType: Int?,
        inCommunication: Boolean = false,
    ): RouteCandidate? {
        if (mediaDeviceType != null && mediaDeviceType != TYPE_BLUETOOTH_SCO) {
            candidates.firstOrNull { it.type == mediaDeviceType && isSupported(it.type) }?.let { return it }
        }
        for (group in priority) {
            candidates.firstOrNull { it.type in group }?.let { return it }
        }
        if (inCommunication) {
            candidates.firstOrNull { it.type == TYPE_BLUETOOTH_SCO }?.let { return it }
        }
        return null
    }

    private fun isSupported(type: Int): Boolean = priority.any { type in it }
}
