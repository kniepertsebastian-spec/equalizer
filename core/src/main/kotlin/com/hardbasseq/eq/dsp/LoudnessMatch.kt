package com.hardbasseq.eq.dsp

import kotlin.math.log10
import kotlin.math.pow

// Fairer Original/EQ-Vergleich: Ein EQ, der insgesamt lauter klingt, wirkt schnell "besser".
// Diese Schätzung bestimmt, wie viel lauter (positiv) oder leiser (negativ) die EQ-Seite gegenüber
// dem Original ist, und verteilt die Angleichung so, dass jeweils die lautere Seite leiser wird
// (der Effekt kann nur abschwächen, nicht verstärken). Eine Schätzung aus der Kurve, keine Messung.
object LoudnessMatch {
    private const val MAX_ATTENUATION_DB = 15f

    // Gewichtung der Hörempfindlichkeit (A-Bewertung, als Leistungsverhältnis). Musik hat pro Oktave
    // ungefähr gleich viel Energie, ein Graphik-EQ besteht aus ~oktavbreiten Bändern - daher zählt
    // jedes Band mit dem Gewicht seiner Mittenfrequenz.
    fun aWeightPower(frequencyHz: Float): Double {
        val f2 = frequencyHz.toDouble().pow(2)
        val ra =
            (12194.0.pow(2) * f2 * f2) /
                ((f2 + 20.6.pow(2)) * kotlin.math.sqrt((f2 + 107.7.pow(2)) * (f2 + 737.9.pow(2))) * (f2 + 12194.0.pow(2)))
        return ra * ra
    }

    /** Leistungsgewichtetes Mittel der Bandverstärkungen in dB (0 bei leerer Liste). */
    fun meanGainDb(bands: List<Pair<Float, Float>>): Float {
        if (bands.isEmpty()) return 0f
        var weightSum = 0.0
        var powerSum = 0.0
        bands.forEach { (frequencyHz, gainDb) ->
            val weight = aWeightPower(frequencyHz)
            weightSum += weight
            powerSum += weight * 10.0.pow(gainDb / 10.0)
        }
        if (weightSum <= 0.0) return 0f
        return (10.0 * log10(powerSum / weightSum)).toFloat()
    }

    /** Geschätzter Pegelunterschied EQ gegenüber Original: Bandmittel plus Eingangsverstärkung. */
    fun eqLevelDeltaDb(
        bands: List<Pair<Float, Float>>,
        inputGainDb: Float,
    ): Float = meanGainDb(bands) + inputGainDb

    /** Wie viel jede Seite abgesenkt wird, damit beide gleich laut sind (beide Werte <= 0). */
    data class Plan(
        val originalGainDb: Float,
        val eqExtraGainDb: Float,
    )

    fun plan(eqLevelDeltaDb: Float): Plan {
        val delta = eqLevelDeltaDb.coerceIn(-MAX_ATTENUATION_DB, MAX_ATTENUATION_DB)
        return if (delta >= 0f) Plan(originalGainDb = 0f, eqExtraGainDb = -delta) else Plan(originalGainDb = delta, eqExtraGainDb = 0f)
    }
}
