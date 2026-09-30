package com.hardbasseq.eq.audio

import com.hardbasseq.eq.context.SoundContext

enum class AudioDeviceType {
    SPEAKER,
    WIRED_HEADPHONES,
    BLUETOOTH,

    // Bluetooth routes whose product name looks like a car / a speaker (see
    // SoundContextClassifier - a name heuristic, not a certainty) and Android's
    // own automotive bus. Stored as their name in DeviceProfileEntity.routeType,
    // which is informational only (routes are keyed by routeId), so adding values
    // needs no schema change.
    CAR,
    BLUETOOTH_SPEAKER,
    USB,
    UNKNOWN,
}

// The listening context this route type implies, if it has one. Drives which
// built-in context preset a route without a saved profile starts on.
fun AudioDeviceType.soundContext(): SoundContext? =
    when (this) {
        AudioDeviceType.CAR -> SoundContext.CAR
        AudioDeviceType.BLUETOOTH_SPEAKER -> SoundContext.BLUETOOTH_SPEAKER
        else -> null
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
        AudioDeviceType.SPEAKER, AudioDeviceType.UNKNOWN, AudioDeviceType.CAR, AudioDeviceType.BLUETOOTH_SPEAKER -> false
    }

data class AudioRoute(
    val type: AudioDeviceType = AudioDeviceType.SPEAKER,
    val name: String = "Built-in Speaker",
    val id: String = "speaker_internal",
)
