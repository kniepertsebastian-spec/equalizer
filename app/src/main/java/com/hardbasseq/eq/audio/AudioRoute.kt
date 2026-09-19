package com.hardbasseq.eq.audio

enum class AudioDeviceType {
    SPEAKER,
    WIRED_HEADPHONES,
    BLUETOOTH,
    USB,
    UNKNOWN,
}

data class AudioRoute(
    val type: AudioDeviceType = AudioDeviceType.SPEAKER,
    val name: String = "Built-in Speaker",
    val id: String = "speaker_internal",
)
