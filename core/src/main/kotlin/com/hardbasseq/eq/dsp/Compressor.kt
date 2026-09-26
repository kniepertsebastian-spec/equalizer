package com.hardbasseq.eq.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

// Priorisierte Umsetzungsliste aus dem Nothing/Dirac-Opteo-Chat, Punkt 3:
// `ProcessingSettings`/`Preset` haben `mbcEnabled`/`mbcThresholdDb`/`mbcRatio`
// schon länger im Datenmodell, aber `Media3DspPipeline` wendet das aktuell
// nirgends auf echtes Audio an (nur `inputGainDb` wird verarbeitet). Das hier
// ist eine echte, funktionierende Kompressor-Implementierung, die genau diese
// beiden vorhandenen Werte (Threshold, Ratio) tatsächlich verwendet: ein
// Feed-Forward-Kompressor mit logarithmischer Hüllkurvenverfolgung (separate
// Attack-/Release-Zeitkonstanten) und einem einfachen Hard-Knee-Gain-Rechner
// oberhalb der Schwelle.
//
// "Multiband" im Namen der bestehenden Felder ist eher Zielbeschreibung als
// aktueller Stand: Es gibt nur einen einzigen Threshold/Ratio im ganzen
// Datenmodell, also verarbeitet das hier das Signal als eine einzige Band
// (Vollband-Kompressor). Ein echter Mehrband-Kompressor (mehrere Crossover-
// Bänder, je mit eigenem Detektor) bräuchte komplett neue Settings-Felder -
// außerhalb des Rahmens von "die bereits vorhandenen Felder tatsächlich
// benutzen".
//
// Wie BassExciter/BassMonoSummer eine Referenzimplementierung, nur gegen
// synthetische Testsignale offline geprüft (siehe CompressorTest), nicht auf
// echter Hardware verifiziert - siehe roadmap-2026.md M6 Phase 1
// "Umsetzungsstand" für denselben Vorbehalt.
data class CompressorSettings(
    val enabled: Boolean = false,
    val thresholdDb: Float = -8f,
    val ratio: Float = 2.5f,
) {
    fun clamped(): CompressorSettings =
        copy(
            thresholdDb = thresholdDb.coerceIn(MIN_THRESHOLD_DB, MAX_THRESHOLD_DB),
            ratio = ratio.coerceIn(MIN_RATIO, MAX_RATIO),
        )

    companion object {
        const val MIN_THRESHOLD_DB = -60f
        const val MAX_THRESHOLD_DB = 0f
        const val MIN_RATIO = 1f
        const val MAX_RATIO = 20f
    }
}

class Compressor(
    initialSettings: CompressorSettings = CompressorSettings(),
) {
    private var settings = initialSettings.clamped()

    // In dB, tracks the signal's recent level - starts at the silence floor so
    // a loud sample right at stream start doesn't see a false "already loud"
    // envelope from some prior, unrelated stream.
    private var envelopeDb = SILENCE_FLOOR_DB

    fun updateSettings(newSettings: CompressorSettings) {
        settings = newSettings.clamped()
    }

    // Muss mit exakt einem Sample pro Aufruf, in Stream-Reihenfolge, aufgerufen
    // werden - envelopeDb trägt Zustand zwischen Aufrufen weiter, wie die
    // Biquad-Filterzustände in BassExciter/BassMonoSummer.
    fun process(
        inputSample: Float,
        sampleRateHz: Float,
    ): Float {
        val currentSettings = settings
        if (!currentSettings.enabled) return inputSample

        val inputLevelDb = amplitudeToDb(abs(inputSample))
        val coefficient =
            if (inputLevelDb > envelopeDb) {
                timeConstantCoefficient(ATTACK_MS, sampleRateHz)
            } else {
                timeConstantCoefficient(RELEASE_MS, sampleRateHz)
            }
        envelopeDb = coefficient * envelopeDb + (1f - coefficient) * inputLevelDb

        val overshootDb = (envelopeDb - currentSettings.thresholdDb).coerceAtLeast(0f)
        // Hard-knee: only the portion of overshoot above 1:1 gets compressed
        // away - e.g. ratio 2.5 keeps 1/2.5 = 40% of the overshoot audible.
        val gainReductionDb = overshootDb - overshootDb / currentSettings.ratio
        val gainFactor = dbToAmplitude(-gainReductionDb)

        return (inputSample * gainFactor).coerceIn(-1f, 1f)
    }

    fun reset() {
        envelopeDb = SILENCE_FLOOR_DB
    }

    private fun timeConstantCoefficient(
        timeMs: Float,
        sampleRateHz: Float,
    ): Float {
        val timeConstantSamples = (timeMs / 1000f) * sampleRateHz
        return exp(-1f / timeConstantSamples)
    }

    companion object {
        // Fast enough to catch a kick's transient without fully flattening it,
        // slow enough release to avoid audible pumping on sustained bass -
        // reasonable fixed defaults for this app's bass-heavy material, not
        // exposed as settings since ProcessingSettings/Preset never had fields
        // for attack/release in the first place.
        private const val ATTACK_MS = 5f
        private const val RELEASE_MS = 80f
        private const val SILENCE_FLOOR_DB = -100f

        private fun amplitudeToDb(amplitude: Float): Float = 20f * log10(amplitude.coerceAtLeast(1e-6f))

        private fun dbToAmplitude(db: Float): Float = 10f.pow(db / 20f)
    }
}
