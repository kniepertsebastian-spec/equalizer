package com.hardbasseq.eq.audio.spike

import android.media.audiofx.Equalizer

data class EqualizerBandInfo(
    val index: Int,
    val centerFreqMilliHz: Int,
    val minFreqMilliHz: Int,
    val maxFreqMilliHz: Int,
)

data class EqualizerCapabilitySnapshot(
    val numberOfBands: Int,
    val minLevelMillibel: Int,
    val maxLevelMillibel: Int,
    val bands: List<EqualizerBandInfo>,
)

object EqualizerSpike {
    /**
     * Attaches a throwaway [Equalizer] to [audioSessionId], reads its real
     * band count, center frequencies, frequency ranges and gain range, then
     * releases it again. Never leaves an enabled effect behind — this is a
     * read-only capability probe, not the app's actual EQ engine (that's M2).
     */
    fun readCapabilities(audioSessionId: Int): EqualizerCapabilitySnapshot {
        val equalizer = Equalizer(0, audioSessionId)
        try {
            val bandCount = equalizer.numberOfBands.toInt()
            val levelRange = equalizer.bandLevelRange
            val bands =
                (0 until bandCount).map { index ->
                    val band = index.toShort()
                    val freqRange = equalizer.getBandFreqRange(band)
                    EqualizerBandInfo(
                        index = index,
                        centerFreqMilliHz = equalizer.getCenterFreq(band),
                        minFreqMilliHz = freqRange[0],
                        maxFreqMilliHz = freqRange[1],
                    )
                }
            return EqualizerCapabilitySnapshot(
                numberOfBands = bandCount,
                minLevelMillibel = levelRange[0].toInt(),
                maxLevelMillibel = levelRange[1].toInt(),
                bands = bands,
            )
        } finally {
            equalizer.release()
        }
    }
}
