package com.hardbasseq.eq.dsp

// Priorisierte Umsetzungsliste aus dem Nothing/Dirac-Opteo-Chat, Punkt 2:
// Sub-Bass unterhalb einer Grenzfrequenz wird vom Ohr kaum noch räumlich
// wahrgenommen, aber Pegel-/Phasenunterschiede zwischen L/R dort führen bei
// In-Ears (kein akustisches Übersprechen wie bei Lautsprechern) oft zu
// "wabbeligem" statt druckvollem Bass. Diese Klasse summiert den Bassanteil
// beider Kanäle zu einem gemeinsamen Mono-Signal und mischt ihn zurück in
// beide Kanäle, während alles oberhalb der Grenzfrequenz unangetastet
// stereo bleibt.
//
// Signalfluss pro Kanal:
// 1. Tiefpass isoliert den Bassanteil jedes Kanals separat.
// 2. Beide Bassanteile werden gemittelt -> ein gemeinsames Mono-Bass-Signal.
// 3. Hochpass entfernt den ursprünglichen (noch stereo, potenziell
//    problematischen) Bassanteil aus jedem Kanal.
// 4. Das Mono-Bass-Signal wird zu beiden hochpassgefilterten Kanälen addiert.
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

    private val leftLowpassState = BiquadFilterState()
    private val rightLowpassState = BiquadFilterState()
    private val leftHighpassState = BiquadFilterState()
    private val rightHighpassState = BiquadFilterState()

    private var coefficientsSampleRateHz = -1f
    private var coefficientsCutoffHz = -1f
    private var lowpassCoefficients = SILENT_COEFFICIENTS
    private var highpassCoefficients = SILENT_COEFFICIENTS

    fun updateSettings(newSettings: BassMonoSummerSettings) {
        settings = newSettings.clamped()
    }

    // Muss mit genau einem Stereo-Sample-Paar pro Aufruf, in Stream-
    // Reihenfolge, aufgerufen werden - die vier Filterzustände tragen IIR-
    // Zustand zwischen Aufrufen weiter (siehe BiquadFilterState).
    fun process(
        left: Float,
        right: Float,
        sampleRateHz: Float,
    ): StereoSample {
        val currentSettings = settings
        if (!currentSettings.enabled) return StereoSample(left, right)

        ensureCoefficients(currentSettings.cutoffHz, sampleRateHz)

        val leftBass = leftLowpassState.process(left, lowpassCoefficients)
        val rightBass = rightLowpassState.process(right, lowpassCoefficients)
        val monoBass = (leftBass + rightBass) * 0.5f

        val leftAboveCutoff = leftHighpassState.process(left, highpassCoefficients)
        val rightAboveCutoff = rightHighpassState.process(right, highpassCoefficients)

        return StereoSample(
            left = (leftAboveCutoff + monoBass).coerceIn(-1f, 1f),
            right = (rightAboveCutoff + monoBass).coerceIn(-1f, 1f),
        )
    }

    fun reset() {
        leftLowpassState.reset()
        rightLowpassState.reset()
        leftHighpassState.reset()
        rightHighpassState.reset()
    }

    private fun ensureCoefficients(
        cutoffHz: Float,
        sampleRateHz: Float,
    ) {
        if (coefficientsSampleRateHz == sampleRateHz && coefficientsCutoffHz == cutoffHz) return

        lowpassCoefficients =
            BiquadFilterDesigner.design(
                ParametricFilter(ParametricFilterType.LOW_PASS, cutoffHz, 0f, BUTTERWORTH_Q),
                sampleRateHz,
            )
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
