package com.hardbasseq.eq.dsp

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

// Virtual Bass: psychoakustische Bass-Erweiterung (vgl. Waves MaxxBass). Ein kleiner Treiber
// kann 40 Hz oft gar nicht abstrahlen, egal wie stark man dort anhebt. Diese Klasse erzeugt aus
// dem Bassanteil Obertöne (Oktave und Duodezime), die der Treiber wiedergeben kann; das Gehör
// ergänzt den fehlenden Grundton dazu ("missing fundamental"). Das Original bleibt vollständig
// erhalten, die Obertöne werden zugemischt.
//
// Neuaufbau Roadmap 03, P4-3. Konzept, Messwerte und Abwägungen: docs/DSP_VIRTUAL_BASS.md.
// Signalfluss pro Sample:
// 1. Bass isolieren: Hochpass 20 Hz (2. Ordnung) und Butterworth-Tiefpass 4. Ordnung bei
//    cutoffHz. Die steile Flanke hält Stimmen und Gitarren aus dem Generator heraus.
// 2. Hilbert-Paar (HilbertAllpassPair): I und Q sind 90° verschoben. Die Hüllkurve
//    A = sqrt(I² + Q²) ist sofort da und welligkeitsfrei, I/A und Q/A sind cos und sin der Phase.
// 3. Kennlinie: Tschebyschow-Polynome auf der normierten Phase,
//        A · (w2 · cos 2θ + w3 · cos 3θ)  mit  cos 2θ = c² - s²,  cos 3θ = c · (c² - 3s²).
//    Ein Ton ergibt genau eine zweite und eine dritte Harmonische, keine weiteren und keinen
//    Gleichanteil. thirdHarmonicShare verteilt die Oberton-Leistung: w2 = sqrt(1 - share),
//    w3 = sqrt(share); die Gesamtleistung bleibt beim Verschieben gleich.
//    Weil die Kennlinie mit A skaliert, ist der Oberton-Anteil bei jedem Pegel gleich groß - die
//    alte tanh-Sättigung machte ihn bei leisen Stellen um bis zu 8 dB stärker.
// 4. Hüllkurvenfolger (Attack 10 ms, Release 150 ms) auf A steuert ein weiches Gate: unter
//    -66 dBFS Basspegel keine Obertöne, ab -54 dBFS volle Stärke. So bekommen Rauschen,
//    Hallfahnen und Ausklänge keinen eigenen Oberton-Teppich.
// 5. Bandpass für die Obertöne: Hochpass 65 Hz (2. Ordnung) gegen Reste unterhalb des Nutzbands,
//    Tiefpass bei 3 · cutoffHz (2. Ordnung), damit die dritte Harmonische des höchsten
//    isolierten Grundtons gerade noch durchkommt, aber nichts in die Mitten läuft.
// 6. Zumischen mit mix · OUTPUT_TRIM.
//
// Referenzimplementierung, offline gegen synthetische Testsignale geprüft (BassExciterTest,
// HilbertAllpassPairTest), nicht auf echter Hardware gehört. Arbeitet mit normalisierten
// Float-Samples (-1..1); :core bleibt Android-frei, die PCM16-Konvertierung macht PlayerDspPcm16.
data class BassExciterSettings(
    val enabled: Boolean = false,
    // Nur unterhalb dieser Frequenz wird der Bassanteil zur Obertonerzeugung herangezogen.
    val cutoffHz: Float = 150f,
    // 0..1: wie stark der erzeugte Oberton-Anteil zum Original zugemischt wird.
    val mix: Float = 0.5f,
    // 0..1: Anteil der dritten Harmonischen an der Oberton-Leistung. 0 = nur Oktave (weich),
    // 1 = nur Duodezime (härter, mehr "Punch"). Standard: überwiegend Oktave.
    val thirdHarmonicShare: Float = DEFAULT_THIRD_HARMONIC_SHARE,
) {
    fun clamped(): BassExciterSettings =
        copy(
            cutoffHz = cutoffHz.coerceIn(MIN_CUTOFF_HZ, MAX_CUTOFF_HZ),
            mix = mix.coerceIn(0f, 1f),
            thirdHarmonicShare = thirdHarmonicShare.coerceIn(0f, 1f),
        )

    companion object {
        const val MIN_CUTOFF_HZ = 60f
        const val MAX_CUTOFF_HZ = 300f
        const val DEFAULT_THIRD_HARMONIC_SHARE = 0.25f
    }
}

class BassExciter(
    initialSettings: BassExciterSettings = BassExciterSettings(),
) {
    private var settings = initialSettings.clamped()
    private var secondHarmonicWeight = 0.0
    private var thirdHarmonicWeight = 0.0

    private val isolationHighpassState = BiquadFilterState()
    private val isolationLowpassStateA = BiquadFilterState()
    private val isolationLowpassStateB = BiquadFilterState()
    private val hilbert = HilbertAllpassPair()
    private val harmonicHighpassState = BiquadFilterState()
    private val harmonicLowpassState = BiquadFilterState()

    private var coefficientsSampleRateHz = -1f
    private var coefficientsCutoffHz = -1f
    private var isolationHighpass = SILENT_COEFFICIENTS
    private var isolationLowpassA = SILENT_COEFFICIENTS
    private var isolationLowpassB = SILENT_COEFFICIENTS
    private var harmonicHighpass = SILENT_COEFFICIENTS
    private var harmonicLowpass = SILENT_COEFFICIENTS

    private var envelopeAttack = 0.0
    private var envelopeRelease = 0.0
    private var smoothedEnvelope = 0.0

    init {
        updateWeights()
    }

    fun updateSettings(newSettings: BassExciterSettings) {
        settings = newSettings.clamped()
        updateWeights()
    }

    // Muss mit exakt einem Sample pro Aufruf, in Stream-Reihenfolge, aufgerufen werden - die
    // Filter tragen ihren Zustand zwischen Aufrufen weiter.
    fun process(
        inputSample: Float,
        sampleRateHz: Float,
    ): Float = processUnclamped(inputSample, sampleRateHz).coerceIn(-1f, 1f)

    // Same as process(), but may go past full scale: for a float chain with a limiter behind it,
    // where clipping here would only add distortion the limiter cannot undo.
    fun processUnclamped(
        inputSample: Float,
        sampleRateHz: Float,
    ): Float {
        val currentSettings = settings
        if (!currentSettings.enabled || currentSettings.mix <= 0f) return inputSample

        ensureCoefficients(currentSettings.cutoffHz, sampleRateHz)

        var bass = isolationHighpassState.process(inputSample, isolationHighpass)
        bass = isolationLowpassStateA.process(bass, isolationLowpassA)
        bass = isolationLowpassStateB.process(bass, isolationLowpassB)

        val generated = generateHarmonics(bass.toDouble())
        val harmonics =
            harmonicLowpassState.process(
                harmonicHighpassState.process(generated.toFloat(), harmonicHighpass),
                harmonicLowpass,
            )

        return inputSample + harmonics * OUTPUT_TRIM * currentSettings.mix
    }

    fun reset() {
        isolationHighpassState.reset()
        isolationLowpassStateA.reset()
        isolationLowpassStateB.reset()
        hilbert.reset()
        harmonicHighpassState.reset()
        harmonicLowpassState.reset()
        smoothedEnvelope = 0.0
    }

    private fun generateHarmonics(bass: Double): Double {
        hilbert.process(bass)
        val i = hilbert.inPhase
        val q = hilbert.quadrature
        val envelope = sqrt(i * i + q * q)

        val coefficient = if (envelope > smoothedEnvelope) envelopeAttack else envelopeRelease
        smoothedEnvelope = coefficient * smoothedEnvelope + (1.0 - coefficient) * envelope

        if (envelope < MIN_ENVELOPE) return 0.0
        val gate = gateGain(smoothedEnvelope)
        if (gate <= 0.0) return 0.0

        val c = i / envelope
        val s = q / envelope
        val cos2 = c * c - s * s
        val cos3 = c * (c * c - 3.0 * s * s)
        return envelope * gate * (secondHarmonicWeight * cos2 + thirdHarmonicWeight * cos3)
    }

    private fun updateWeights() {
        val share = settings.thirdHarmonicShare.toDouble()
        secondHarmonicWeight = sqrt(1.0 - share)
        thirdHarmonicWeight = sqrt(share)
    }

    private fun ensureCoefficients(
        cutoffHz: Float,
        sampleRateHz: Float,
    ) {
        if (coefficientsSampleRateHz == sampleRateHz && coefficientsCutoffHz == cutoffHz) return

        isolationHighpass = design(ParametricFilterType.HIGH_PASS, ISOLATION_HIGHPASS_HZ, BUTTERWORTH_Q, sampleRateHz)
        isolationLowpassA = design(ParametricFilterType.LOW_PASS, cutoffHz, BUTTERWORTH4_Q1, sampleRateHz)
        isolationLowpassB = design(ParametricFilterType.LOW_PASS, cutoffHz, BUTTERWORTH4_Q2, sampleRateHz)
        harmonicHighpass = design(ParametricFilterType.HIGH_PASS, HARMONIC_HIGHPASS_HZ, BUTTERWORTH_Q, sampleRateHz)
        harmonicLowpass =
            design(ParametricFilterType.LOW_PASS, cutoffHz * HARMONIC_LOWPASS_RATIO, BUTTERWORTH_Q, sampleRateHz)
        hilbert.configure(sampleRateHz)

        envelopeAttack = exp(-1.0 / (ENVELOPE_ATTACK_SECONDS * sampleRateHz))
        envelopeRelease = exp(-1.0 / (ENVELOPE_RELEASE_SECONDS * sampleRateHz))

        coefficientsSampleRateHz = sampleRateHz
        coefficientsCutoffHz = cutoffHz
    }

    private fun design(
        type: ParametricFilterType,
        frequencyHz: Float,
        q: Float,
        sampleRateHz: Float,
    ): BiquadCoefficients = BiquadFilterDesigner.design(ParametricFilter(type, frequencyHz, 0f, q), sampleRateHz)

    companion object {
        // Q = 1/sqrt(2): maximally flat, no resonant peak at the cutoff.
        private const val BUTTERWORTH_Q = 0.70710678f

        // The two stages of a 4th-order Butterworth low-pass (24 dB/octave).
        private const val BUTTERWORTH4_Q1 = 0.54119610f
        private const val BUTTERWORTH4_Q2 = 1.30656296f

        // Below this, nothing a driver or the ear would turn into bass; keeps rumble and DC out of
        // the generator.
        const val ISOLATION_HIGHPASS_HZ = 20f

        // Harmonic band-pass: 65 Hz passes the octave above a 40 Hz kick (80 Hz) at -1.6 dB.
        const val HARMONIC_HIGHPASS_HZ = 65f

        // ... and 3 x cutoff passes the 3rd harmonic of the highest isolated fundamental at -3 dB.
        const val HARMONIC_LOWPASS_RATIO = 3f

        // Gate on the smoothed bass envelope: closed below -66 dBFS, fully open from -54 dBFS.
        const val GATE_CLOSED_DBFS = -66f
        const val GATE_OPEN_DBFS = -54f
        private val GATE_CLOSED = 10.0.pow(GATE_CLOSED_DBFS / 20.0)
        private val GATE_OPEN = 10.0.pow(GATE_OPEN_DBFS / 20.0)

        const val ENVELOPE_ATTACK_SECONDS = 0.010
        const val ENVELOPE_RELEASE_SECONDS = 0.150

        // Below this the phase of I/Q is noise; nothing to generate from.
        private const val MIN_ENVELOPE = 1e-9

        // Calibrated: the total harmonic level (2nd + 3rd) of a 100 Hz tone (amplitude 0.3) at
        // mix = 1 and the default share matches the 2nd harmonic of the P0-5 exciter (-9.7 dB re
        // input), so existing presets do not get louder (BassExciterTest.totalHarmonicLevel_*).
        private const val OUTPUT_TRIM = 0.35f

        private val SILENT_COEFFICIENTS = BiquadCoefficients(0f, 0f, 0f, 0f, 0f)

        // Smoothstep from closed to open: no corner where the gate starts to act.
        private fun gateGain(envelope: Double): Double {
            val t = ((envelope - GATE_CLOSED) / (GATE_OPEN - GATE_CLOSED)).coerceIn(0.0, 1.0)
            return t * t * (3.0 - 2.0 * t)
        }
    }
}
