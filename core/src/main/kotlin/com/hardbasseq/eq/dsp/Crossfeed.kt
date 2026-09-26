package com.hardbasseq.eq.dsp

// Priorisierte Umsetzungsliste aus dem Nothing/Dirac-Opteo-Chat, Punkt 5:
// Über Lautsprecher hört jedes Ohr beide Kanäle - zeitversetzt, gedämpft und
// gefiltert durch den Umweg um den Kopf ("akustisches Übersprechen"). Bei
// Kopfhörern fehlt das komplett: jedes Ohr bekommt nur seinen eigenen Kanal,
// was auf Dauer unnatürlich und ermüdend wirken kann. Crossfeed simuliert
// diesen fehlenden Übersprech-Effekt, indem ein tiefpassgefilterter Anteil
// des jeweils anderen Kanals zugemischt wird (natürliches Übersprechen ist
// durch die Kopfbeugung vor allem für tiefere/mittlere Frequenzen relevant,
// nicht für Höhen - daher der Tiefpass, ähnlich dem bs2b-Algorithmus mit
// typischerweise ~700 Hz Grenzfrequenz).
//
// Bewusst *kein* Ersatz für BassMonoSummer (Punkt 2): Der mischt Bassanteile
// unterhalb einer sehr niedrigen Grenzfrequenz zu echtem Mono zusammen (löst
// Phasenprobleme), Crossfeed hier mischt nur einen *Teil* des jeweils anderen
// Kanals über einen deutlich höheren Frequenzbereich bei (verändert das
// Stereobild, ersetzt es nicht) - unterschiedliche Zwecke, beide unabhängig
// voneinander nutzbar.
//
// Wie BassMonoSummer/Compressor eine Referenzimplementierung, nur gegen
// synthetische Testsignale offline geprüft (siehe CrossfeedTest), nicht auf
// echter Hardware verifiziert. Sollte laut App-Logik nur aktiv sein, wenn
// MainViewModel.effectiveHeadphoneAcoustics (Punkt 1) true ist - diese reine
// DSP-Klasse kennt AudioRoute aber nicht (würde eine Android-Abhängigkeit in
// :core ziehen) und verlässt sich stattdessen einfach auf `enabled`, das der
// Aufrufer entsprechend setzt.
data class CrossfeedSettings(
    val enabled: Boolean = false,
    val cutoffHz: Float = 700f,
    // 0..1: wie stark der andere Kanal zugemischt wird.
    val amount: Float = 0.5f,
) {
    fun clamped(): CrossfeedSettings =
        copy(
            cutoffHz = cutoffHz.coerceIn(MIN_CUTOFF_HZ, MAX_CUTOFF_HZ),
            amount = amount.coerceIn(0f, 1f),
        )

    companion object {
        const val MIN_CUTOFF_HZ = 300f
        const val MAX_CUTOFF_HZ = 1200f
    }
}

class Crossfeed(
    initialSettings: CrossfeedSettings = CrossfeedSettings(),
) {
    private var settings = initialSettings.clamped()

    private val leftLowpassState = BiquadFilterState()
    private val rightLowpassState = BiquadFilterState()

    private var configuredSampleRateHz = -1f
    private var configuredCutoffHz = -1f
    private var lowpassCoefficients = SILENT_COEFFICIENTS

    fun updateSettings(newSettings: CrossfeedSettings) {
        settings = newSettings.clamped()
    }

    // Muss mit genau einem Stereo-Sample-Paar pro Aufruf, in Stream-
    // Reihenfolge, aufgerufen werden - siehe BiquadFilterState.
    fun process(
        left: Float,
        right: Float,
        sampleRateHz: Float,
    ): StereoSample {
        val currentSettings = settings
        if (!currentSettings.enabled || currentSettings.amount <= 0f) return StereoSample(left, right)

        ensureCoefficients(currentSettings.cutoffHz, sampleRateHz)

        val leftLow = leftLowpassState.process(left, lowpassCoefficients)
        val rightLow = rightLowpassState.process(right, lowpassCoefficients)

        val crossGain = currentSettings.amount * MAX_CROSS_GAIN
        // Normalizes so a mono (left == right) signal comes back out at
        // roughly the same level it went in - without this, mixing in a
        // correlated cross-channel component would make already-mono content
        // louder purely as a side effect of crossfeed being on.
        val normalization = 1f / (1f + crossGain)

        val outputLeft = (left + crossGain * rightLow) * normalization
        val outputRight = (right + crossGain * leftLow) * normalization

        return StereoSample(
            left = outputLeft.coerceIn(-1f, 1f),
            right = outputRight.coerceIn(-1f, 1f),
        )
    }

    fun reset() {
        leftLowpassState.reset()
        rightLowpassState.reset()
    }

    private fun ensureCoefficients(
        cutoffHz: Float,
        sampleRateHz: Float,
    ) {
        if (configuredSampleRateHz == sampleRateHz && configuredCutoffHz == cutoffHz) return

        lowpassCoefficients =
            BiquadFilterDesigner.design(
                ParametricFilter(ParametricFilterType.LOW_PASS, cutoffHz, 0f, BUTTERWORTH_Q),
                sampleRateHz,
            )
        configuredSampleRateHz = sampleRateHz
        configuredCutoffHz = cutoffHz
    }

    companion object {
        private const val BUTTERWORTH_Q = 0.70710678f

        // Even at amount=1.0, the cross-channel component never fully
        // overtakes the direct signal - bs2b-style crossfeed blends the
        // acoustic-crosstalk illusion in, it doesn't collapse the stereo
        // image to mono.
        private const val MAX_CROSS_GAIN = 0.6f
        private val SILENT_COEFFICIENTS = BiquadCoefficients(0f, 0f, 0f, 0f, 0f)
    }
}
