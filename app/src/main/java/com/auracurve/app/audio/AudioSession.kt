package com.auracurve.app.audio

data class AudioSession(
    val sessionId: Int,
    val packageName: String = "unknown",
    val active: Boolean = true,
)
