package com.hardbasseq.eq.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

// Priorisierte Umsetzungsliste aus dem Nothing/Dirac-Opteo-Chat, Punkt 4:
// Ein normaler ("reaktiver") Limiter kann eine Pegelspitze erst reduzieren,
// *nachdem* er sie gesehen hat - bei einem plötzlichen Transienten (Kick!)
// reicht die Reaktionszeit oft nicht, um wirklich vor der Spitze zu greifen,
// ohne hörbar zu "pumpen". Ein Lookahead-Limiter verzögert das Ausgangssignal
// um ein paar Millisekunden und kann die Gain-Reduktion dadurch schon
// *bevor* die Spitze am Ausgang ankommt einleiten - technisch gesehen "Attack
// = 0" (keine Verzögerung mehr nötig, weil man die Zukunft schon kennt),
// nur die Freigabe (Release) danach läuft weiterhin geglättet.
//
// Zusätzlich: ein reiner Sample-Peak-Limiter sieht nicht, dass der
// *rekonstruierte* Analogpegel zwischen zwei Samples höher liegen kann als
// jedes einzelne Sample selbst zeigt ("Inter-Sample-Peak") - bei stark
// bassgeboostetem Signal (Kernfeature dieser App) ein reales Clipping-Risiko
// auf dem DAC. Diese Implementierung schätzt das über eine simple lineare
// Interpolation des Mittelpunkts zwischen zwei aufeinanderfolgenden Samples -
// das erkennt den klassischen Einzelsample-Überschwinger, ist aber *keine*
// vollwertige 4x-oversampelte True-Peak-Messung nach ITU-R BS.1770 (die
// bräuchte einen echten Polyphasen-Interpolationsfilter) - für eine
// Referenzimplementierung bewusst außerhalb des Rahmens.
//
// Wie BassExciter/Compressor eine Referenzimplementierung, nur gegen
// synthetische Testsignale offline geprüft (siehe LookaheadLimiterTest),
// nicht auf echter Hardware verifiziert.
data class LookaheadLimiterSettings(
    val enabled: Boolean = true,
    val thresholdDb: Float = -1f,
    val lookaheadMs: Float = 5f,
) {
    fun clamped(): LookaheadLimiterSettings =
        copy(
            thresholdDb = thresholdDb.coerceIn(MIN_THRESHOLD_DB, MAX_THRESHOLD_DB),
            lookaheadMs = lookaheadMs.coerceIn(MIN_LOOKAHEAD_MS, MAX_LOOKAHEAD_MS),
        )

    companion object {
        const val MIN_THRESHOLD_DB = -24f
        const val MAX_THRESHOLD_DB = 0f
        const val MIN_LOOKAHEAD_MS = 1f
        const val MAX_LOOKAHEAD_MS = 20f
    }
}

class LookaheadLimiter(
    initialSettings: LookaheadLimiterSettings = LookaheadLimiterSettings(),
) {
    private var settings = initialSettings.clamped()

    // The delay line the lookahead scheme needs: process() can only emit the
    // *oldest* buffered sample once it has "seen" every sample up to
    // lookaheadSamples ahead of it - callers get silence for the first
    // lookaheadSamples calls as a result, exactly like any real lookahead
    // limiter's inherent startup latency.
    private val sampleDelay = ArrayDeque<Float>()

    // One entry per buffered sample: the gain that sample alone would need to
    // stay under the threshold. The gain actually applied to the oldest
    // sample is the minimum across this whole window, not just its own entry -
    // that's what lets a future peak pull gain down before it ever reaches
    // the output.
    private val requiredGainWindow = ArrayDeque<Float>()

    private var currentGain = 1f
    private var previousSample = 0f
    private var lookaheadSamples = -1
    private var configuredSampleRateHz = -1f
    private var configuredLookaheadMs = -1f

    fun updateSettings(newSettings: LookaheadLimiterSettings) {
        settings = newSettings.clamped()
    }

    // Muss mit exakt einem Sample pro Aufruf, in Stream-Reihenfolge, aufgerufen
    // werden. Gibt 0f zurück, solange der Lookahead-Puffer noch nicht voll ist
    // (Startlatenz) - siehe Klassendokumentation.
    fun process(
        inputSample: Float,
        sampleRateHz: Float,
    ): Float {
        val currentSettings = settings
        if (!currentSettings.enabled) return inputSample

        ensureLookaheadWindowSize(currentSettings.lookaheadMs, sampleRateHz)

        val thresholdLinear = dbToAmplitude(currentSettings.thresholdDb)
        val interpolatedMidpoint = (previousSample + inputSample) / 2f
        val peakEstimate = maxOf(abs(inputSample), abs(interpolatedMidpoint))
        previousSample = inputSample

        val requiredGain = if (peakEstimate > thresholdLinear) thresholdLinear / peakEstimate else 1f

        sampleDelay.addLast(inputSample)
        requiredGainWindow.addLast(requiredGain)

        if (sampleDelay.size <= lookaheadSamples) return 0f

        val windowMinGain = requiredGainWindow.min()
        currentGain =
            if (windowMinGain < currentGain) {
                // Lookahead already saw this coming - no attack ramp needed.
                windowMinGain
            } else {
                val releaseCoefficient = timeConstantCoefficient(RELEASE_MS, sampleRateHz)
                releaseCoefficient * currentGain + (1f - releaseCoefficient) * windowMinGain
            }

        val outputSample = sampleDelay.removeFirst() * currentGain
        requiredGainWindow.removeFirst()
        return outputSample.coerceIn(-1f, 1f)
    }

    fun reset() {
        sampleDelay.clear()
        requiredGainWindow.clear()
        currentGain = 1f
        previousSample = 0f
    }

    private fun ensureLookaheadWindowSize(
        lookaheadMs: Float,
        sampleRateHz: Float,
    ) {
        if (configuredSampleRateHz == sampleRateHz && configuredLookaheadMs == lookaheadMs) return
        lookaheadSamples = ((lookaheadMs / 1000f) * sampleRateHz).roundToInt().coerceAtLeast(1)
        configuredSampleRateHz = sampleRateHz
        configuredLookaheadMs = lookaheadMs
    }

    private fun timeConstantCoefficient(
        timeMs: Float,
        sampleRateHz: Float,
    ): Float {
        val timeConstantSamples = (timeMs / 1000f) * sampleRateHz
        return exp(-1f / timeConstantSamples)
    }

    companion object {
        // Gain recovers back toward unity over this time constant once the
        // lookahead window no longer contains anything above threshold - fast
        // enough to not needlessly duck quiet passages for long, slow enough
        // to avoid an audible "un-ducking" jump.
        private const val RELEASE_MS = 50f

        private fun dbToAmplitude(db: Float): Float = 10f.pow(db / 20f)
    }
}
