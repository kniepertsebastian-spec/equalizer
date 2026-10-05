package com.hardbasseq.eq.desktop.player

import com.hardbasseq.eq.desktop.DESKTOP_FILTER_Q
import com.hardbasseq.eq.desktop.DESKTOP_SUBSONIC_Q
import com.hardbasseq.eq.desktop.ResolvedSound
import com.hardbasseq.eq.desktop.VirtualBands
import com.hardbasseq.eq.dsp.BiquadCoefficients
import com.hardbasseq.eq.dsp.BiquadFilterDesigner
import com.hardbasseq.eq.dsp.BiquadFilterState
import com.hardbasseq.eq.dsp.ParametricFilter
import com.hardbasseq.eq.dsp.ParametricFilterType
import com.hardbasseq.eq.dsp.PlayerDspPcm16
import com.hardbasseq.eq.dsp.PlayerDspSettings
import com.hardbasseq.eq.dsp.SubsonicFilterCurve
import kotlin.math.pow

/**
 * The desktop player's own DSP: the same sound the Equalizer APO export
 * describes (preamp, optional true subsonic high-pass, the 15 peaking bands),
 * followed by the lookahead limiter from [PlayerDspPcm16] that APO cannot
 * provide. It only touches audio the desktop player itself plays - there is no
 * system-wide hook here, by design.
 *
 * Works on interleaved PCM16. Call [configure] whenever the stream format
 * changes, [reset] on seek or track change.
 */
class DesktopDspChain(
    private val sound: ResolvedSound,
    private val tailSettings: PlayerDspSettings = PlayerDspSettings(monoBassEnabled = false),
) {
    private val preampLinear = 10f.pow(sound.preampDb / 20f)
    private val tail = PlayerDspPcm16().also { it.updateSettings(tailSettings) }
    private var channelCount = 0
    private var sampleRateHz = 0
    private var coefficients: List<BiquadCoefficients> = emptyList()
    private var states: List<List<BiquadFilterState>> = emptyList()

    fun configure(
        channels: Int,
        sampleRate: Int,
    ) {
        require(channels >= 1) { "channels must be >= 1" }
        require(sampleRate >= 8000) { "sample rate too low: $sampleRate" }
        channelCount = channels
        sampleRateHz = sampleRate
        coefficients = designFilters(sampleRate.toFloat())
        states = List(channels) { coefficients.map { BiquadFilterState() } }
        tail.configure(channels)
    }

    fun reset() {
        states.forEach { channel -> channel.forEach { it.reset() } }
        tail.reset()
    }

    /** Filters [sampleCount] interleaved samples in place, starting at [offset]. */
    fun process(
        samples: ShortArray,
        offset: Int = 0,
        sampleCount: Int = samples.size - offset,
    ) {
        check(channelCount > 0) { "configure() must be called before process()" }
        var i = offset
        val end = offset + sampleCount
        while (i + channelCount <= end) {
            for (c in 0 until channelCount) {
                var x = samples[i + c] / PCM16_SCALE * preampLinear
                val channelStates = states[c]
                for (f in coefficients.indices) x = channelStates[f].process(x, coefficients[f])
                samples[i + c] = (x * PCM16_SCALE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            i += channelCount
        }
        tail.process(samples, offset, sampleCount, sampleRateHz)
    }

    private fun designFilters(rate: Float): List<BiquadCoefficients> {
        val filters = mutableListOf<ParametricFilter>()
        if (sound.subsonicCutoffHz > 0f) {
            val cutoff = sound.subsonicCutoffHz.coerceIn(SubsonicFilterCurve.MIN_CUTOFF_HZ, SubsonicFilterCurve.MAX_CUTOFF_HZ)
            filters += ParametricFilter(ParametricFilterType.HIGH_PASS, cutoff, 0f, DESKTOP_SUBSONIC_Q)
        }
        VirtualBands.bands.forEach { band ->
            val gain = sound.bandGainsDb[band.index] ?: 0f
            // A band above Nyquist (low sample rates) cannot be designed.
            if (gain != 0f && band.centerFreqHz < rate / 2f * NYQUIST_MARGIN) {
                filters += ParametricFilter(ParametricFilterType.PEAK, band.centerFreqHz.toFloat(), gain, DESKTOP_FILTER_Q)
            }
        }
        return filters.map { BiquadFilterDesigner.design(it, rate) }
    }

    private companion object {
        const val PCM16_SCALE = 32768f
        const val NYQUIST_MARGIN = 0.9f
    }
}
