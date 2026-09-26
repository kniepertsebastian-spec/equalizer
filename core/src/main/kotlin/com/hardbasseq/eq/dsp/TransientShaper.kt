package com.hardbasseq.eq.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

// Priorisierte Umsetzungsliste aus dem Nothing/Dirac-Opteo-Chat, Punkt 6:
// ersetzt die bisherige "Punch"-Notlösung (macroPunchDb in EqualizerInterpolator
// - ein reiner EQ-Peak bei 70-160 Hz plus Dip bei 220-360 Hz, siehe dortiger
// Kommentar zu Session 17: die Fenster mussten schon einmal empirisch
// nachjustiert werden, weil es auf echten Geräten kaum wirkte) durch einen
// echten Transient-Shaper. EQ kann "Punch" bestenfalls indirekt über
// Frequenzinhalt annähern - ein Transient-Designer erkennt tatsächliche
// Attack-Transienten (unabhängig von ihrem Frequenzinhalt) und verstärkt
// (oder dämpft) gezielt nur die ansteigende Flanke, nicht den Sustain-Teil.
//
// Funktionsprinzip: zwei Hüllkurvenverfolger auf demselben Signal, einer
// schnell (folgt echten Transienten fast verzögerungsfrei), einer langsam
// (folgt nur dem allgemeinen Pegeltrend). Die Differenz zwischen beiden ist
// nahe 0, solange sich der Pegel nur allmählich ändert (beide folgen
// gleichermaßen) - sie schnellt aber nach oben, sobald der Pegel schneller
// ansteigt, als die langsame Hüllkurve folgen kann: genau das ist ein
// Transient. Dieser Differenzwert steuert die angewandte Verstärkung.
//
// Wie Crossfeed eine Referenzimplementierung, nur gegen
// synthetische Testsignale offline geprüft (siehe TransientShaperTest), nicht
// auf echter Hardware verifiziert.
data class TransientShaperSettings(
    val enabled: Boolean = false,
    // -1..1: negativ dämpft Transienten (weicher/runder), positiv betont sie
    // (mehr "Punch"), 0 = keine Wirkung.
    val punchAmount: Float = 0.5f,
) {
    fun clamped(): TransientShaperSettings = copy(punchAmount = punchAmount.coerceIn(-1f, 1f))
}

class TransientShaper(
    initialSettings: TransientShaperSettings = TransientShaperSettings(),
) {
    private var settings = initialSettings.clamped()

    private var fastEnvelopeDb = SILENCE_FLOOR_DB
    private var slowEnvelopeDb = SILENCE_FLOOR_DB

    fun updateSettings(newSettings: TransientShaperSettings) {
        settings = newSettings.clamped()
    }

    // Muss mit exakt einem Sample pro Aufruf, in Stream-Reihenfolge, aufgerufen
    // werden - beide Hüllkurven tragen Zustand zwischen Aufrufen weiter.
    fun process(
        inputSample: Float,
        sampleRateHz: Float,
    ): Float {
        val currentSettings = settings
        if (!currentSettings.enabled || currentSettings.punchAmount == 0f) return inputSample

        val inputLevelDb = amplitudeToDb(abs(inputSample))
        fastEnvelopeDb = followEnvelope(fastEnvelopeDb, inputLevelDb, FAST_ATTACK_MS, FAST_RELEASE_MS, sampleRateHz)
        slowEnvelopeDb = followEnvelope(slowEnvelopeDb, inputLevelDb, SLOW_ATTACK_MS, SLOW_RELEASE_MS, sampleRateHz)

        // Nur die positive Differenz zählt als Transient - wenn der Pegel
        // fällt, folgt die schnelle Hüllkurve schneller nach unten als die
        // langsame, was sonst fälschlich als "Transient" erkannt würde.
        val transientDb = (fastEnvelopeDb - slowEnvelopeDb).coerceAtLeast(0f)

        val gainDb = currentSettings.punchAmount * transientDb * PUNCH_SENSITIVITY
        val gainFactor = dbToAmplitude(gainDb)

        return (inputSample * gainFactor).coerceIn(-1f, 1f)
    }

    fun reset() {
        fastEnvelopeDb = SILENCE_FLOOR_DB
        slowEnvelopeDb = SILENCE_FLOOR_DB
    }

    private fun followEnvelope(
        currentDb: Float,
        inputDb: Float,
        attackMs: Float,
        releaseMs: Float,
        sampleRateHz: Float,
    ): Float {
        val coefficient =
            if (inputDb > currentDb) {
                timeConstantCoefficient(attackMs, sampleRateHz)
            } else {
                timeConstantCoefficient(releaseMs, sampleRateHz)
            }
        return coefficient * currentDb + (1f - coefficient) * inputDb
    }

    private fun timeConstantCoefficient(
        timeMs: Float,
        sampleRateHz: Float,
    ): Float {
        val timeConstantSamples = (timeMs / 1000f) * sampleRateHz
        return exp(-1f / timeConstantSamples)
    }

    companion object {
        // Fast envelope: reacts almost immediately, so it actually catches a
        // kick's attack instead of averaging it away.
        private const val FAST_ATTACK_MS = 1f
        private const val FAST_RELEASE_MS = 10f

        // Slow envelope: deliberately sluggish, so it represents "the level
        // this passage has generally been at", not the attack itself.
        private const val SLOW_ATTACK_MS = 30f
        private const val SLOW_RELEASE_MS = 100f

        private const val SILENCE_FLOOR_DB = -100f

        // Scales the fast/slow envelope gap (which can be tens of dB for a
        // sharp transient) down to a musically reasonable gain range at
        // punchAmount = 1.0 (a handful of dB, not tens).
        private const val PUNCH_SENSITIVITY = 0.35f

        private fun amplitudeToDb(amplitude: Float): Float = 20f * log10(amplitude.coerceAtLeast(1e-6f))

        private fun dbToAmplitude(db: Float): Float = 10f.pow(db / 20f)
    }
}
