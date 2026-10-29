package com.hardbasseq.eq.dsp

// Priorisierte Umsetzungsliste aus dem Nothing/Dirac-Opteo-Chat, Punkt 2:
// Sub-Bass unterhalb einer Grenzfrequenz wird vom Ohr kaum noch räumlich
// wahrgenommen, aber Pegel-/Phasenunterschiede zwischen L/R dort führen bei
// In-Ears (kein akustisches Übersprechen wie bei Lautsprechern) oft zu
// "wabbeligem" statt druckvollem Bass. Diese Klasse summiert den Bassanteil
// beider Kanäle zu einem gemeinsamen Mono-Signal, während alles oberhalb der
// Grenzfrequenz unangetastet stereo bleibt.
//
// Signalfluss (Mitte/Seite):
// 1. Mitte M = (L+R)/2 und Seite S = (L-R)/2.
// 2. Nur die Seite wird hochpassgefiltert (24 dB/Oktave, zwei Butterworth-
//    Stufen) - unterhalb der Grenzfrequenz bleibt so keine L/R-Differenz übrig.
// 3. L = M + HP(S), R = M - HP(S).
//
// Die Mitte wird nie gefiltert: Monomaterial (der Normalfall für Bass) kommt
// unverändert heraus. Die frühere Variante (Tiefpass + Hochpass je Kanal,
// danach summiert) löschte genau an der Grenzfrequenz alles aus - zwei
// Butterworth-Filter 2. Ordnung sind dort 180° gegeneinander verschoben, bei
// 120 Hz entstand also ein tiefes Loch mitten im Kick.
//
// Wie BassExciter/BiquadFilterDesigner eine Referenzimplementierung, nur
// gegen synthetische Testsignale offline geprüft (siehe BassMonoSummerTest),
// nicht auf echter Hardware verifiziert - siehe roadmap-2026.md M6 Phase 1
// "Umsetzungsstand" für denselben Vorbehalt. Arbeitet mit normalisierten
// Float-Samples (-1..1), nicht mit PCM16 Short-Werten - :core bleibt
// Android-frei.
data class BassMonoSummerSettings(
    val enabled: Boolean = false,
    // Nur unterhalb dieser Frequenz wird zu Mono zusammengefasst.
    val cutoffHz: Float = 120f,
) {
    fun clamped(): BassMonoSummerSettings = copy(cutoffHz = cutoffHz.coerceIn(MIN_CUTOFF_HZ, MAX_CUTOFF_HZ))

    companion object {
        const val MIN_CUTOFF_HZ = 40f
        const val MAX_CUTOFF_HZ = 200f
    }
}

data class StereoSample(
    val left: Float,
    val right: Float,
)

class BassMonoSummer(
    initialSettings: BassMonoSummerSettings = BassMonoSummerSettings(),
) {
    private var settings = initialSettings.clamped()

    private val sideHighpassStates = listOf(BiquadFilterState(), BiquadFilterState())

    private var coefficientsSampleRateHz = -1f
    private var coefficientsCutoffHz = -1f
    private var highpassCoefficients = SILENT_COEFFICIENTS

    fun updateSettings(newSettings: BassMonoSummerSettings) {
        settings = newSettings.clamped()
    }

    // Muss mit genau einem Stereo-Sample-Paar pro Aufruf, in Stream-
    // Reihenfolge, aufgerufen werden - die Filterzustände tragen IIR-
    // Zustand zwischen Aufrufen weiter (siehe BiquadFilterState).
    fun process(
        left: Float,
        right: Float,
        sampleRateHz: Float,
    ): StereoSample {
        val currentSettings = settings
        if (!currentSettings.enabled) return StereoSample(left, right)

        ensureCoefficients(currentSettings.cutoffHz, sampleRateHz)

        val mid = (left + right) * 0.5f
        var side = (left - right) * 0.5f
        for (state in sideHighpassStates) side = state.process(side, highpassCoefficients)

        return StereoSample(left = mid + side, right = mid - side)
    }

    fun reset() {
        sideHighpassStates.forEach { it.reset() }
    }

    private fun ensureCoefficients(
        cutoffHz: Float,
        sampleRateHz: Float,
    ) {
        if (coefficientsSampleRateHz == sampleRateHz && coefficientsCutoffHz == cutoffHz) return

        highpassCoefficients =
            BiquadFilterDesigner.design(
                ParametricFilter(ParametricFilterType.HIGH_PASS, cutoffHz, 0f, BUTTERWORTH_Q),
                sampleRateHz,
            )
        coefficientsSampleRateHz = sampleRateHz
        coefficientsCutoffHz = cutoffHz
    }

    companion object {
        private const val BUTTERWORTH_Q = 0.70710678f
        private val SILENT_COEFFICIENTS = BiquadCoefficients(0f, 0f, 0f, 0f, 0f)
    }
}
