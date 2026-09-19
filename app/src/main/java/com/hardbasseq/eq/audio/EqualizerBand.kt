package com.hardbasseq.eq.audio

data class EqualizerBandCapabilities(
    val index: Int,
    val centerFreqHz: Int,
    val minGainDb: Float,
    val maxGainDb: Float,
)
