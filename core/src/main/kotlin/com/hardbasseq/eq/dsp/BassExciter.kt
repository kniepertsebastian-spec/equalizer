package com.hardbasseq.eq.dsp

import kotlin.math.abs

// Feature-Idee aus dem Nothing/Dirac-Opteo-Vergleich (Chat, nicht roadmap-2026.md
// verortet): harmonische Bass-Erweiterung ("Bass Exciter"/"psychoacoustic bass
// enhancement", vgl. Waves MaxxBass, SRS TruBass). Anders als jeder andere Typ
// in diesem Package ist das *nichtlinear*: BiquadFilterDesigner/CurveComposer/
// EqualizerInterpolator verschieben nur den Pegel vorhandener Frequenzen - ein
// kleiner Treiber kann aber z. B. 40 Hz oft gar nicht sauber abstrahlen, egal
// wie stark man dort anhebt (Verzerrung/Clipping statt mehr Bass). Diese Klasse
// erzeugt stattdessen aus dem Bassanteil neue Obertöne (v. a. eine Oktave
// höher), die der Treiber tatsächlich wiedergeben kann - das Gehör ergänzt den
// fehlenden Grundton psychoakustisch dazu ("missing fundamental").
//
// Signalfluss pro Sample:
// 1. Tiefpass bei cutoffHz isoliert den Bassanteil.
// 2. Vollweg-Gleichrichtung (abs) dieses Anteils erzeugt Obertöne, dominant
//    die Oktave über der jeweiligen Grundfrequenz - aber auch einen DC-Offset
//    (der Mittelwert von |x| liegt über 0), den ein Signal ohne Gleichrichter
//    nie hätte.
// 3. Hochpass bei derselben Cutoff-Frequenz entfernt DC und Restanteile nahe
//    des Grundtons, sodass primär der neu erzeugte Oberton übrig bleibt.
// 4. Der Oberton-Anteil wird mit "mix" zurückgemischt statt den Grundton zu
//    ersetzen - Original bleibt vollständig erhalten.
//
// Wie BiquadFilterDesigner ist das eine Referenzimplementierung, die nur
// gegen synthetische Testsignale offline geprüft ist (siehe
// BassExciterTest), nicht auf echter Hardware verifiziert (hörbare
// Artefakte, CPU/Akku) - siehe roadmap-2026.md M6 Phase 1 "Umsetzungsstand"
// für denselben Vorbehalt bei den linearen Filtern. Arbeitet bewusst mit
// normalisierten Float-Samples (-1..1), nicht mit PCM16 Short-Werten wie
// Media3DspPipeline - :core bleibt Android-frei, eine Short<->Float-
// Konvertierung wäre Sache des Aufrufers.
data class BassExciterSettings(
    val enabled: Boolean = false,
    // Nur unterhalb dieser Frequenz wird der Bassanteil zur Obertonerzeugung
    // herangezogen.
    val cutoffHz: Float = 150f,
    // 0..1: wie stark der erzeugte Oberton-Anteil zum Original zugemischt wird.
    val mix: Float = 0.5f,
    // 0..1: Sättigungsstärke vor der Gleichrichtung - mehr Drive erzeugt mehr
    // Oberton-Energie, aber auch mehr Verzerrung des Bassanteils selbst.
    val drive: Float = 0.5f,
) {
    fun clamped(): BassExciterSettings =
        copy(
            cutoffHz = cutoffHz.coerceIn(MIN_CUTOFF_HZ, MAX_CUTOFF_HZ),
            mix = mix.coerceIn(0f, 1f),
            drive = drive.coerceIn(0f, 1f),
        )

    companion object {
        const val MIN_CUTOFF_HZ = 60f
        const val MAX_CUTOFF_HZ = 300f
    }
}

class BassExciter(
    initialSettings: BassExciterSettings = BassExciterSettings(),
) {
    private var settings = initialSettings.clamped()

    private val lowpassState = BiquadFilterState()
    private val dcBlockState = BiquadFilterState()

    private var coefficientsSampleRateHz = -1f
    private var coefficientsCutoffHz = -1f
    private var lowpassCoefficients = SILENT_COEFFICIENTS
    private var dcBlockCoefficients = SILENT_COEFFICIENTS

    fun updateSettings(newSettings: BassExciterSettings) {
        settings = newSettings.clamped()
    }

    // Muss mit exakt einem Sample pro Aufruf, in Stream-Reihenfolge, aufgerufen
    // werden - lowpassState/dcBlockState tragen IIR-Filterzustand zwischen
    // Aufrufen weiter (siehe BiquadFilterState).
    fun process(
        inputSample: Float,
        sampleRateHz: Float,
    ): Float {
        val currentSettings = settings
        if (!currentSettings.enabled || currentSettings.mix <= 0f) return inputSample

        ensureCoefficients(currentSettings.cutoffHz, sampleRateHz)

        val bassBand = lowpassState.process(inputSample, lowpassCoefficients)

        val driveFactor = 1f + currentSettings.drive * MAX_DRIVE_MULTIPLIER
        val rectified = abs(bassBand * driveFactor)
        // Vollweg-Gleichrichtung verschiebt den Mittelwert nach oben (DC) -
        // der Hochpass hier entfernt genau das, plus Restanteile nahe des
        // Grundtons, statt nur eine allgemeine Rauschunterdrückung zu sein.
        val harmonics = dcBlockState.process(rectified, dcBlockCoefficients)
        val normalizedHarmonics = harmonics / driveFactor

        return (inputSample + normalizedHarmonics * currentSettings.mix).coerceIn(-1f, 1f)
    }

    fun reset() {
        lowpassState.reset()
        dcBlockState.reset()
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
        dcBlockCoefficients =
            BiquadFilterDesigner.design(
                ParametricFilter(ParametricFilterType.HIGH_PASS, cutoffHz, 0f, BUTTERWORTH_Q),
                sampleRateHz,
            )
        coefficientsSampleRateHz = sampleRateHz
        coefficientsCutoffHz = cutoffHz
    }

    companion object {
        // Q = 1/sqrt(2): maximally-flat (Butterworth) response, no resonant
        // peak at the cutoff that would color the isolated bass band.
        private const val BUTTERWORTH_Q = 0.70710678f
        private const val MAX_DRIVE_MULTIPLIER = 9f
        private val SILENT_COEFFICIENTS = BiquadCoefficients(0f, 0f, 0f, 0f, 0f)
    }
}
