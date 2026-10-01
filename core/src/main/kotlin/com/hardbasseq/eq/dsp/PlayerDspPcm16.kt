package com.hardbasseq.eq.dsp

// The player's output stage on interleaved PCM16: mono bass first (stereo only),
// then a lookahead limiter per channel so the bass boost and virtual bass cannot
// clip the DAC. Each part can be switched off on its own; with both off the buffer
// is left untouched. Android-free like BassExciterPcm16 - the Media3 processor that
// feeds it lives in :player.
//
// The limiter runs per channel (not linked): when only one channel peaks, only that
// one is pulled down, which can nudge the stereo image for a few milliseconds.
data class PlayerDspSettings(
    val monoBassEnabled: Boolean = true,
    val monoBassCutoffHz: Float = DEFAULT_MONO_BASS_CUTOFF_HZ,
    val limiterEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_MONO_BASS_CUTOFF_HZ = 120f
    }
}

class PlayerDspPcm16 {
    private var settings = PlayerDspSettings()
    private val monoSummer = BassMonoSummer()
    private var limiters: List<LookaheadLimiter> = emptyList()

    fun updateSettings(newSettings: PlayerDspSettings) {
        // A limiter that was off holds stale samples: start it fresh.
        if (newSettings.limiterEnabled && !settings.limiterEnabled) limiters.forEach { it.reset() }
        settings = newSettings
        monoSummer.updateSettings(
            BassMonoSummerSettings(enabled = newSettings.monoBassEnabled, cutoffHz = newSettings.monoBassCutoffHz),
        )
    }

    // Call whenever the channel layout changes.
    fun configure(channelCount: Int) {
        limiters = List(channelCount.coerceAtLeast(1)) { LookaheadLimiter() }
        monoSummer.reset()
    }

    // A seek or track change: drop the filters' and limiters' memory of the old audio.
    fun reset() {
        monoSummer.reset()
        limiters.forEach { it.reset() }
    }

    fun process(
        samples: ShortArray,
        offset: Int,
        sampleCount: Int,
        sampleRateHz: Int,
    ) {
        val current = settings
        if (limiters.isEmpty() || !(current.monoBassEnabled || current.limiterEnabled)) return
        val channels = limiters.size
        val rate = sampleRateHz.toFloat()
        val monoActive = current.monoBassEnabled && channels == 2
        var i = offset
        val end = offset + sampleCount
        while (i + channels <= end) {
            if (monoActive) {
                val mixed = monoSummer.process(samples[i] / PCM16_SCALE, samples[i + 1] / PCM16_SCALE, rate)
                samples[i] = toPcm16(mixed.left)
                samples[i + 1] = toPcm16(mixed.right)
            }
            if (current.limiterEnabled) {
                for (c in 0 until channels) {
                    samples[i + c] = toPcm16(limiters[c].process(samples[i + c] / PCM16_SCALE, rate))
                }
            }
            i += channels
        }
    }

    private fun toPcm16(value: Float): Short =
        (value * PCM16_SCALE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    private companion object {
        const val PCM16_SCALE = 32768f
    }
}
