package com.hardbasseq.eq.dsp

// Runs BassExciter over interleaved PCM16 (the format a Media3/ExoPlayer audio
// processor sees for decoded music), one exciter per channel so each channel
// keeps its own IIR filter state. BassExciter itself works on normalized floats
// one sample at a time; this is the Short <-> Float bridge its own header
// comment leaves to the caller. Stays Android-free so it is unit-testable here;
// the Media3 AudioProcessor that feeds it lives in the :player module.
class BassExciterPcm16 {
    private var exciters: List<BassExciter> = emptyList()
    private var settings = BassExciterSettings()

    fun updateSettings(newSettings: BassExciterSettings) {
        settings = newSettings
        exciters.forEach { it.updateSettings(newSettings) }
    }

    // Must be called whenever the stream's channel layout changes or playback is
    // flushed/seeked - otherwise stale filter state would leak into the new
    // audio as a click.
    fun configure(channelCount: Int) {
        exciters = List(channelCount.coerceAtLeast(1)) { BassExciter(settings) }
    }

    fun reset() {
        exciters.forEach { it.reset() }
    }

    // In-place. `frameCount * channelCount` samples starting at `offset` are
    // processed; anything past that is left alone. No-op while disabled, so the
    // caller can keep the processor in the chain at zero cost to the audio.
    fun process(
        samples: ShortArray,
        offset: Int,
        sampleCount: Int,
        sampleRateHz: Int,
    ) {
        if (!settings.enabled || settings.mix <= 0f || exciters.isEmpty()) return
        val channels = exciters.size
        val rate = sampleRateHz.toFloat()
        for (i in offset until offset + sampleCount) {
            val normalized = samples[i] / PCM16_SCALE
            val out = exciters[(i - offset) % channels].process(normalized, rate)
            samples[i] = (out * PCM16_SCALE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private companion object {
        const val PCM16_SCALE = 32768f
    }
}
