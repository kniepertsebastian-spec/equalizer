package com.auracurve.app.audio

data class EqualizerBandCapabilities(
    val index: Int,
    val centerFreqHz: Int,
    val minGainDb: Float,
    val maxGainDb: Float,
)
