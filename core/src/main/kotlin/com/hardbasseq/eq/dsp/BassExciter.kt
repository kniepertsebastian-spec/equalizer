package com.hardbasseq.eq.dsp

import kotlin.math.abs
import kotlin.math.tanh

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
// 2. Sättigung (tanh, Stärke über "drive") und Vollweg-Gleichrichtung (abs)
//    erzeugen Obertöne, dominant die Oktave über der jeweiligen Grundfrequenz -
//    aber auch einen DC-Offset (der Mittelwert von |x| liegt über 0), den ein
//    Signal ohne Gleichrichter nie hätte. Ohne die Sättigung wäre "drive" nur
//    eine Verstärkung vor und eine Abschwächung nach dem Gleichrichter und
//    würde sich rechnerisch aufheben (abs(x * d) / d == abs(x)).
// 3. Hochpass bei HARMONIC_HIGHPASS_HZ (65 Hz) entfernt den DC-Anteil und
//    Subsonic-Reste, lässt aber die Oktave über einem 40-Hz-Grundton (80 Hz)
//    fast ungedämpft durch. Früher lag er auf cutoffHz (150 Hz) und schnitt
//    diese Oktave um rund 11 dB ab - also dort am stärksten, wo kleine Treiber
//    den Effekt am dringendsten brauchen.
// 4. Tiefpass bei 2 * cutoffHz (150 Hz -> 300 Hz) begrenzt die Obertöne nach
//    oben: mehr als die Oktave über dem höchsten isolierten Grundton soll nicht
//    in die unteren Mitten laufen.
// 5. Der Oberton-Anteil wird mit "mix" zurückgemischt statt den Grundton zu
//    ersetzen - Original bleibt vollständig erhalten. OUTPUT_TRIM kalibriert
//    den Pegel der zweiten Harmonischen bei Standard-Drive und einem 100-Hz-
//    Ton auf den Wert vor dieser Korrektur, damit bestehende Presets nicht
//    plötzlich deutlich lauter klingen; geändert hat sich die Lage im Bass.
//
// Wie BiquadFilterDesigner ist das eine Referenzimplementierung, die nur
// gegen synthetische Testsignale offline geprüft ist (siehe
// BassExciterTest), nicht auf echter Hardware verifiziert (hörbare
// Artefakte, CPU/Akku) - siehe roadmap-2026.md M6 Phase 1 "Umsetzungsstand"
// für denselben Vorbehalt bei den linearen Filtern. Arbeitet bewusst mit
// normalisierten Float-Samples (-1..1), nicht mit PCM16 Short-Werten -
// :core bleibt Android-frei, die Short<->Float-Konvertierung macht der
// Aufrufer (PlayerDspPcm16).
//
// Bekannte Grenze: Eine Sättigung flacht laute Spitzen ab. Bei sehr lautem
// Bass (Spitzen nahe Vollaussteuerung) wächst die zweite Harmonische mit
// "drive" deshalb nicht mehr, dafür kommen höhere Obertöne dazu. Der
// grundlegende Neuaufbau (Roadmap 03, P4-3) ersetzt dieses Verfahren.
data class BassExciterSettings(
    val enabled: Boolean = false,
    // Nur unterhalb dieser Frequenz wird der Bassanteil zur Obertonerzeugung
    // herangezogen.
    val cutoffHz: Float = 150f,
    // 0..1: wie stark der erzeugte Oberton-Anteil zum Original zugemischt wird.
    val mix: Float = 0.5f,
    // 0..1: Sättigungsstärke vor der Gleichrichtung - mehr Drive erzeugt bei
    // moderatem Bass mehr Oberton-Energie, aber auch mehr Verzerrung des
    // Bassanteils selbst.
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
    private val harmonicLimitState = BiquadFilterState()

    private var coefficientsSampleRateHz = -1f
    private var coefficientsCutoffHz = -1f
    private var lowpassCoefficients = SILENT_COEFFICIENTS
    private var dcBlockCoefficients = SILENT_COEFFICIENTS
    private var harmonicLimitCoefficients = SILENT_COEFFICIENTS

    private var saturationGain = 1f
    private var saturationNormalizer = 1f

    init {
        updateSaturation(settings.drive)
    }

    fun updateSettings(newSettings: BassExciterSettings) {
        settings = newSettings.clamped()
        updateSaturation(settings.drive)
    }

    // Muss mit exakt einem Sample pro Aufruf, in Stream-Reihenfolge, aufgerufen
    // werden - lowpassState/dcBlockState/harmonicLimitState tragen IIR-
    // Filterzustand zwischen Aufrufen weiter (siehe BiquadFilterState).
    fun process(
        inputSample: Float,
        sampleRateHz: Float,
    ): Float = processUnclamped(inputSample, sampleRateHz).coerceIn(-1f, 1f)

    // Same as process(), but may go past full scale: for a float chain with a limiter
    // behind it, where clipping here would only add distortion the limiter cannot undo.
    fun processUnclamped(
        inputSample: Float,
        sampleRateHz: Float,
    ): Float {
        val currentSettings = settings
        if (!currentSettings.enabled || currentSettings.mix <= 0f) return inputSample

        ensureCoefficients(currentSettings.cutoffHz, sampleRateHz)

        val bassBand = lowpassState.process(inputSample, lowpassCoefficients)

        // tanh(g * x) / tanh(g): für g = 1 fast linear, für größere g zunehmend
        // gesättigt. Die Normierung hält den Vollausschlag bei 1, die Kleinsignal-
        // Verstärkung steigt mit g - mehr Drive heißt mehr Oberton-Energie.
        val saturated = tanh(bassBand * saturationGain) / saturationNormalizer
        val rectified = abs(saturated)
        // Vollweg-Gleichrichtung verschiebt den Mittelwert nach oben (DC) -
        // der Hochpass hier entfernt genau das (und Subsonic-Reste), ohne die
        // Oktave über einem tiefen Grundton abzuschneiden; der Tiefpass dahinter
        // hält die Obertöne im Bassbereich.
        val harmonics =
            harmonicLimitState.process(
                dcBlockState.process(rectified, dcBlockCoefficients),
                harmonicLimitCoefficients,
            )

        return inputSample + harmonics * OUTPUT_TRIM * currentSettings.mix
    }

    fun reset() {
        lowpassState.reset()
        dcBlockState.reset()
        harmonicLimitState.reset()
    }

    private fun updateSaturation(drive: Float) {
        saturationGain = 1f + drive * SATURATION_RANGE
        saturationNormalizer = tanh(saturationGain)
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
                ParametricFilter(ParametricFilterType.HIGH_PASS, HARMONIC_HIGHPASS_HZ, 0f, BUTTERWORTH_Q),
                sampleRateHz,
            )
        harmonicLimitCoefficients =
            BiquadFilterDesigner.design(
                ParametricFilter(ParametricFilterType.LOW_PASS, cutoffHz * HARMONIC_LOWPASS_RATIO, 0f, BUTTERWORTH_Q),
                sampleRateHz,
            )
        coefficientsSampleRateHz = sampleRateHz
        coefficientsCutoffHz = cutoffHz
    }

    companion object {
        // Q = 1/sqrt(2): maximally-flat (Butterworth) response, no resonant
        // peak at the cutoff that would color the isolated bass band.
        private const val BUTTERWORTH_Q = 0.70710678f

        // Hochpass hinter dem Gleichrichter: 65 Hz lässt 80 Hz (Oktave über 40 Hz)
        // mit -1,6 dB durch (vorher bei 150 Hz: -11 dB) und trennt DC ab.
        const val HARMONIC_HIGHPASS_HZ = 65f

        // Tiefpass hinter dem Gleichrichter: eine Oktave über dem Isolations-
        // Tiefpass (Standard 150 Hz -> 300 Hz).
        const val HARMONIC_LOWPASS_RATIO = 2f

        // drive 0..1 -> Sättigungsverstärkung 1..3.
        private const val SATURATION_RANGE = 2f

        // Kalibriert: zweite Harmonische eines 100-Hz-Tons (Amplitude 0,3) bei
        // Standard-Drive und mix = 1 liegt wie vor der Korrektur bei etwa -9,4 dB
        // zum Eingang (siehe BassExciterTest.secondHarmonicLevel_*).
        private const val OUTPUT_TRIM = 0.5f

        private val SILENT_COEFFICIENTS = BiquadCoefficients(0f, 0f, 0f, 0f, 0f)
    }
}
