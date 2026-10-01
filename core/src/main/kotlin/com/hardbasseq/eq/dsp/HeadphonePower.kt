package com.hardbasseq.eq.dsp

import kotlinx.serialization.Serializable

// "Kopfhörer-Power": die bisher festen Kopfhörer-Werte (Bass +2,5 dB, entschärfte
// Dynamik, Loudness bis 9 dB) einstellbar. DEFAULT entspricht exakt dem alten
// Verhalten; KNALL ist das kräftige Preset (mehr Bass, volle Dynamik, mehr
// Loudness) - bewusst laut, die Eingangsverstärkung wird weiterhin über den
// Headroom-Schutz begrenzt.
@Serializable
data class HeadphonePower(
    val bassDb: Float = DEFAULT_BASS_DB,
    val easeDynamics: Boolean = true,
    val loudnessMaxDb: Float = DEFAULT_LOUDNESS_DB,
) {
    fun sanitized(): HeadphonePower =
        copy(
            bassDb = bassDb.coerceIn(0f, MAX_BASS_DB),
            loudnessMaxDb = loudnessMaxDb.coerceIn(0f, MAX_LOUDNESS_DB),
        )

    companion object {
        const val DEFAULT_BASS_DB = 2.5f
        const val DEFAULT_LOUDNESS_DB = 9f
        const val MAX_BASS_DB = 9f
        const val MAX_LOUDNESS_DB = 15f

        val DEFAULT = HeadphonePower()
        val KNALL = HeadphonePower(bassDb = 7f, easeDynamics = false, loudnessMaxDb = 12f)
    }
}
