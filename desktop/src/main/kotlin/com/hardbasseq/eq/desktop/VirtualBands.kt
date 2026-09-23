package com.hardbasseq.eq.desktop

import com.hardbasseq.eq.audio.EqualizerBandCapabilities

/**
 * Desktop EQ engines (Equalizer APO, EasyEffects) support arbitrary
 * parametric filters rather than a fixed set of hardware bands, so this
 * grid isn't a real device's capability - it's a deliberately chosen
 * resolution for turning a [com.hardbasseq.eq.preset.Preset]'s target curve
 * into a concrete band list, reusing the same
 * [com.hardbasseq.eq.dsp.EqualizerInterpolator] logic the Android app uses
 * for real hardware bands. 15 log-ish spaced points give the sub-bass/kick
 * region (where the uptempo-hardcore presets do most of their shaping)
 * more resolution than a typical 10-band graphic EQ would.
 */
object VirtualBands {
    private val frequenciesHz =
        listOf(31, 45, 63, 90, 125, 175, 250, 350, 500, 700, 1000, 2000, 4000, 8000, 16000)

    val bands: List<EqualizerBandCapabilities> =
        frequenciesHz.mapIndexed { index, freq ->
            EqualizerBandCapabilities(
                index = index,
                centerFreqHz = freq,
                minGainDb = -15f,
                maxGainDb = 15f,
            )
        }
}
