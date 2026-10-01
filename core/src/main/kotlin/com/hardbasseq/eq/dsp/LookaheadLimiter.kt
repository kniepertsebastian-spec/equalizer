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
// Wie BassExciter eine Referenzimplementierung, nur gegen
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
    //
    // Runs on the audio thread for every sample, so it uses primitive ring buffers
    // (no boxing, no allocation) and a monotonic deque for the window minimum
    // (O(1) per sample instead of scanning the whole window). That matters: with the
    // screen off the CPU clocks down, and a slow limiter makes the music stutter.
    // Samples are numbered 0, 1, 2 ... in the order they arrive; `pushed` is how many
    // came in, `popped` how many went out again.
    private var delayLine = FloatArray(0)

    // The gain each buffered sample alone would need to stay under the threshold. The
    // gain actually applied to the oldest sample is the minimum across the whole
    // window, not just its own entry - that's what lets a future peak pull gain down
    // before it ever reaches the output.
    private var requiredGains = FloatArray(0)

    // Sample numbers whose required gain is smaller than every later one's: the front
    // of this queue is always the minimum of the current window.
    private var minCandidates = LongArray(0)
    private var minHead = 0
    private var minCount = 0
    private var pushed = 0L
    private var popped = 0L

    private var currentGain = 1f
    private var previousSample = 0f
    private var lookaheadSamples = -1
    private var configuredSampleRateHz = -1f
    private var configuredLookaheadMs = -1f

    // pow/exp per sample would be wasted work: both only change with the settings.
    private var cachedThresholdDb = Float.NaN
    private var thresholdLinear = 1f
    private var releaseCoefficient = 0f

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

        if (currentSettings.thresholdDb != cachedThresholdDb) {
            cachedThresholdDb = currentSettings.thresholdDb
            thresholdLinear = dbToAmplitude(cachedThresholdDb)
        }
        val interpolatedMidpoint = (previousSample + inputSample) / 2f
        val peakEstimate = maxOf(abs(inputSample), abs(interpolatedMidpoint))
        previousSample = inputSample

        val requiredGain = if (peakEstimate > thresholdLinear) thresholdLinear / peakEstimate else 1f

        val capacity = delayLine.size
        val slot = (pushed % capacity).toInt()
        delayLine[slot] = inputSample
        requiredGains[slot] = requiredGain
        // Anything not smaller than the new gain can never be the minimum again.
        while (minCount > 0 && requiredGains[(minCandidates[lastCandidate(capacity)] % capacity).toInt()] >= requiredGain) minCount--
        minCandidates[(minHead + minCount) % capacity] = pushed
        minCount++
        pushed++

        if (pushed - popped <= lookaheadSamples) return 0f

        val windowMinGain = requiredGains[(minCandidates[minHead] % capacity).toInt()]
        currentGain =
            if (windowMinGain < currentGain) {
                // Lookahead already saw this coming - no attack ramp needed.
                windowMinGain
            } else {
                releaseCoefficient * currentGain + (1f - releaseCoefficient) * windowMinGain
            }

        val outputSample = delayLine[(popped % capacity).toInt()] * currentGain
        if (minCandidates[minHead] == popped) {
            minHead = (minHead + 1) % capacity
            minCount--
        }
        popped++
        return outputSample.coerceIn(-1f, 1f)
    }

    fun reset() {
        pushed = 0L
        popped = 0L
        minHead = 0
        minCount = 0
        currentGain = 1f
        previousSample = 0f
    }

    private fun lastCandidate(capacity: Int): Int = (minHead + minCount - 1) % capacity

    private fun ensureLookaheadWindowSize(
        lookaheadMs: Float,
        sampleRateHz: Float,
    ) {
        if (configuredSampleRateHz == sampleRateHz && configuredLookaheadMs == lookaheadMs) return
        lookaheadSamples = ((lookaheadMs / 1000f) * sampleRateHz).roundToInt().coerceAtLeast(1)
        configuredSampleRateHz = sampleRateHz
        configuredLookaheadMs = lookaheadMs
        releaseCoefficient = timeConstantCoefficient(RELEASE_MS, sampleRateHz)
        // The window holds lookaheadSamples + 1 samples at most; a new size starts clean.
        val capacity = lookaheadSamples + 2
        delayLine = FloatArray(capacity)
        requiredGains = FloatArray(capacity)
        minCandidates = LongArray(capacity)
        reset()
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
