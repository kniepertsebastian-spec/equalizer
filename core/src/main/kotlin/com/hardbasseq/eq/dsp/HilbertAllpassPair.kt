package com.hardbasseq.eq.dsp

import kotlin.math.PI
import kotlin.math.tan

// Zwei Ketten aus je vier Allpässen erster Ordnung, deren Ausgänge im Bassbereich 90° zueinander
// verschoben sind (IIR-Hilbert-Paar). Aus einem Ton A·cos(θ) werden so I ≈ A·cos(θ + φ) und
// Q ≈ A·sin(θ + φ): die Hüllkurve sqrt(I² + Q²) ist ohne Glättung und ohne Restwelligkeit genau A,
// und I/A, Q/A sind Kosinus und Sinus der momentanen Phase. Darauf baut der Oberton-Generator in
// BassExciter auf (siehe docs/DSP_VIRTUAL_BASS.md).
//
// Beide Ketten sind reine Allpässe: Betrag 1 bei jeder Frequenz, nur die Phase unterscheidet sich.
// φ selbst (die gemeinsame Phasendrehung) ist frequenzabhängig und für die Obertonerzeugung egal.
//
// Die Polfrequenzen sind offline optimiert (Minimax auf die Abweichung von 90° zwischen 16 und
// 800 Hz bei 48 kHz). Über die bilineare Transformation mit Vorverzerrung bleiben sie bei jeder
// Abtastrate an derselben Stelle; die Abweichung von 90° liegt im Bereich 16–800 Hz bei
// 44,1/48 kHz unter 0,2°, bei 96/192 kHz unter 0,4° (HilbertAllpassPairTest).
class HilbertAllpassPair {
    private val coefficientsI = DoubleArray(SECTIONS)
    private val coefficientsQ = DoubleArray(SECTIONS)
    private val previousInputI = DoubleArray(SECTIONS)
    private val previousOutputI = DoubleArray(SECTIONS)
    private val previousInputQ = DoubleArray(SECTIONS)
    private val previousOutputQ = DoubleArray(SECTIONS)
    private var configuredSampleRateHz = -1f

    /** In-phase output of the last [process] call. */
    var inPhase = 0.0
        private set

    /** Output of the last [process] call, 90° away from [inPhase] in the design band. */
    var quadrature = 0.0
        private set

    fun configure(sampleRateHz: Float) {
        if (sampleRateHz == configuredSampleRateHz) return
        for (k in 0 until SECTIONS) {
            coefficientsI[k] = allpassCoefficient(POLES_I_HZ[k], sampleRateHz)
            coefficientsQ[k] = allpassCoefficient(POLES_Q_HZ[k], sampleRateHz)
        }
        configuredSampleRateHz = sampleRateHz
    }

    // One sample, in stream order (the sections carry state between calls). configure() first.
    fun process(input: Double) {
        inPhase = runChain(input, coefficientsI, previousInputI, previousOutputI)
        quadrature = runChain(input, coefficientsQ, previousInputQ, previousOutputQ)
    }

    fun reset() {
        previousInputI.fill(0.0)
        previousOutputI.fill(0.0)
        previousInputQ.fill(0.0)
        previousOutputQ.fill(0.0)
        inPhase = 0.0
        quadrature = 0.0
    }

    private fun runChain(
        input: Double,
        coefficients: DoubleArray,
        previousInput: DoubleArray,
        previousOutput: DoubleArray,
    ): Double {
        var x = input
        for (k in 0 until SECTIONS) {
            // H(z) = (a + z^-1) / (1 + a·z^-1)
            val a = coefficients[k]
            val y = a * x + previousInput[k] - a * previousOutput[k]
            previousInput[k] = x
            previousOutput[k] = y
            x = y
        }
        return x
    }

    companion object {
        const val SECTIONS = 4

        // Design band of the 90° difference; outside it the phase difference drifts away.
        const val BAND_LOW_HZ = 16f
        const val BAND_HIGH_HZ = 800f

        private val POLES_I_HZ = doubleArrayOf(18.67, 80.72, 316.7, 2351.6)
        private val POLES_Q_HZ = doubleArrayOf(5.405, 40.45, 158.7, 685.6)

        // Bilinear transform of the analog allpass (p - s)/(p + s), prewarped so the pole lands at
        // poleHz. The poles sit close to z = 1 (a ≈ -0.998 at 16 Hz), hence double precision.
        private fun allpassCoefficient(
            poleHz: Double,
            sampleRateHz: Float,
        ): Double {
            val t = tan(PI * poleHz.coerceAtMost(sampleRateHz * 0.49) / sampleRateHz)
            return (t - 1.0) / (t + 1.0)
        }
    }
}
