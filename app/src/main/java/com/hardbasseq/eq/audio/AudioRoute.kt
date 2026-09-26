package com.hardbasseq.eq.audio

enum class AudioDeviceType {
    SPEAKER,
    WIRED_HEADPHONES,
    BLUETOOTH,
    USB,
    UNKNOWN,
}

// Chat feature (item 1 of "setz alle Punkte um", not a roadmap-2026.md
// milestone): a starting guess for whether headphone-style DSP (Crossfeed -
// item 5 - and similar) should apply by default for a route of this type,
// before any explicit per-route override (DeviceProfileEntity.
// headphoneAcousticsOverride) exists. WIRED_HEADPHONES is the only type
// Android reports with certainty; BLUETOOTH/USB are a reasonable guess for
// mobile use (most likely a headset), not a guarantee - a Bluetooth speaker
// or a USB DAC feeding studio monitors both report the same type as their
// headphone counterparts (see AndroidAudioRouteRepository's own detection
// limits) - hence the override existing at all.
fun AudioDeviceType.defaultHeadphoneAcoustics(): Boolean =
    when (this) {
        AudioDeviceType.WIRED_HEADPHONES, AudioDeviceType.BLUETOOTH, AudioDeviceType.USB -> true
        AudioDeviceType.SPEAKER, AudioDeviceType.UNKNOWN -> false
    }

data class AudioRoute(
    val type: AudioDeviceType = AudioDeviceType.SPEAKER,
    val name: String = "Built-in Speaker",
    val id: String = "speaker_internal",
)
