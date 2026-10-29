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
// *bevor* die Spitze am Ausgang ankommt einleiten.
//
// Die Absenkung wird über das Lookahead-Fenster *gerampt* (Minimum über das
// Fenster, danach gleitender Mittelwert über dieselbe Länge): Die Verstärkung
// erreicht genau beim Eintreffen der Spitze den nötigen Wert, ohne Sprung. Ein
// Sprung der Verstärkung wäre selbst ein breitbandiger Klick - genau das, was
// ein Limiter bei jedem Kick vermeiden soll.
//
// Zusätzlich: ein reiner Sample-Peak-Limiter sieht nicht, dass der
// *rekonstruierte* Analogpegel zwischen zwei Samples höher liegen kann als
// jedes einzelne Sample selbst zeigt ("Inter-Sample-Peak") - bei stark
// bassgeboostetem Signal ein reales Clipping-Risiko auf dem DAC. Geschätzt wird
// das mit 4-fach-Interpolation (Catmull-Rom über vier Nachbarsamples) - die
// kann, anders als der Mittelwert zweier Samples, über die Samples hinaus
// schwingen. Keine normgerechte True-Peak-Messung nach ITU-R BS.1770 (die
// bräuchte einen längeren Polyphasenfilter), aber sie fängt den typischen
// Überschwinger zwischen zwei hohen Samples.
//
// Mehrkanalig arbeitet der Limiter gekoppelt: eine Verstärkung für alle
// Kanäle, bestimmt vom lautesten. Getrennte Limiter je Kanal würden das
// Stereobild bei jeder Spitze verschieben.
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
    channelCount: Int = 1,
) {
    private var settings = initialSettings.clamped()
    private val channels = channelCount.coerceAtLeast(1)

    // The last five input frames per channel (oldest first). The peak estimate for a
    // sample needs two neighbours on each side, so the detector runs DETECTOR_DELAY
    // samples behind the input; the core delay is shortened by the same amount, so the
    // total latency stays exactly lookaheadMs.
    private val history = FloatArray(HISTORY_SIZE * channels)

    // Interpolated peak between history samples 1 and 2, from the previous frame: the
    // interval just before the sample being detected.
    private var previousIntervalPeak = 0f

    // Runs on the audio thread for every sample, so everything below is primitive ring
    // buffers (no boxing, no allocation) and a monotonic deque for the window minimum
    // (O(1) per sample instead of scanning the whole window). That matters: with the
    // screen off the CPU clocks down, and a slow limiter makes the music stutter.
    // Frames are numbered 0, 1, 2 ... in the order they enter the core; `pushed` counts them.
    private var window = 0
    private var delayLine = FloatArray(0)
    private var requiredGains = FloatArray(0)
    private var minCandidates = LongArray(0)
    private var minHead = 0
    private var minCount = 0

    // The held minimum of the last `window` frames, averaged over the last `window`
    // frames again: the ramp.
    private var heldGains = FloatArray(0)
    private var heldSum = 0.0
    private var pushed = 0L

    private var currentGain = 1f
    private var configuredSampleRateHz = -1f
    private var configuredLookaheadMs = -1f

    // pow/exp per sample would be wasted work: both only change with the settings.
    private var cachedThresholdDb = Float.NaN
    private var thresholdLinear = 1f
    private var releaseCoefficient = 0f

    // Single-channel only, kept for callers that process one stream at a time.
    private val single = FloatArray(1)

    fun updateSettings(newSettings: LookaheadLimiterSettings) {
        settings = newSettings.clamped()
    }

    // Muss mit exakt einem Sample pro Aufruf, in Stream-Reihenfolge, aufgerufen
    // werden. Gibt 0f zurück, solange der Lookahead-Puffer noch nicht voll ist
    // (Startlatenz) - siehe Klassendokumentation. Nur für einkanalige Limiter.
    fun process(
        inputSample: Float,
        sampleRateHz: Float,
    ): Float {
        if (!settings.enabled) return inputSample
        single[0] = inputSample
        processFrame(single, sampleRateHz)
        return single[0]
    }

    /** One frame (one sample per channel), limited in place. */
    fun processFrame(
        frame: FloatArray,
        sampleRateHz: Float,
    ) {
        val currentSettings = settings
        if (!currentSettings.enabled) return

        ensureLookaheadWindowSize(currentSettings.lookaheadMs, sampleRateHz)
        if (currentSettings.thresholdDb != cachedThresholdDb) {
            cachedThresholdDb = currentSettings.thresholdDb
            thresholdLinear = dbToAmplitude(cachedThresholdDb)
        }

        // Shift the history by one frame and append the new one; the frame in the
        // middle (index DETECTOR_DELAY) is the one detected now and handed to the core.
        System.arraycopy(history, channels, history, 0, (HISTORY_SIZE - 1) * channels)
        for (c in 0 until channels) history[(HISTORY_SIZE - 1) * channels + c] = frame[c]

        var samplePeak = 0f
        var nextIntervalPeak = 0f
        for (c in 0 until channels) {
            val middle = history[DETECTOR_DELAY * channels + c]
            samplePeak = maxOf(samplePeak, abs(middle))
            nextIntervalPeak =
                maxOf(
                    nextIntervalPeak,
                    interpolatedPeak(
                        history[1 * channels + c],
                        middle,
                        history[3 * channels + c],
                        history[4 * channels + c],
                    ),
                )
        }
        val peakEstimate = maxOf(samplePeak, previousIntervalPeak, nextIntervalPeak)
        previousIntervalPeak = nextIntervalPeak
        val requiredGain = if (peakEstimate > thresholdLinear) thresholdLinear / peakEstimate else 1f

        val slot = (pushed % window).toInt()
        // The frame `window - 1` behind the newest one leaves the delay line now.
        val outSlot = ((pushed + 1) % window).toInt()

        for (c in 0 until channels) delayLine[slot * channels + c] = history[DETECTOR_DELAY * channels + c]
        requiredGains[slot] = requiredGain
        // Candidates that fell out of the window, and anything not smaller than the
        // new gain (it can never be the minimum again).
        if (minCount > 0 && minCandidates[minHead] <= pushed - window) {
            minHead = (minHead + 1) % window
            minCount--
        }
        while (minCount > 0 && requiredGains[(minCandidates[lastCandidate()] % window).toInt()] >= requiredGain) minCount--
        minCandidates[(minHead + minCount) % window] = pushed
        minCount++

        val held = requiredGains[(minCandidates[minHead] % window).toInt()]
        heldSum += held - heldGains[slot]
        heldGains[slot] = held
        val ramped = (heldSum / window).toFloat()
        pushed++

        currentGain =
            if (ramped < currentGain) {
                // Already a smooth ramp that lands on the peak's gain in time.
                ramped
            } else {
                releaseCoefficient * currentGain + (1f - releaseCoefficient) * ramped
            }

        for (c in 0 until channels) {
            frame[c] = (delayLine[outSlot * channels + c] * currentGain).coerceIn(-1f, 1f)
        }
    }

    fun reset() {
        history.fill(0f)
        previousIntervalPeak = 0f
        delayLine.fill(0f)
        requiredGains.fill(1f)
        heldGains.fill(1f)
        heldSum = window.toDouble()
        minHead = 0
        minCount = 0
        pushed = 0L
        currentGain = 1f
    }

    private fun lastCandidate(): Int = (minHead + minCount - 1) % window

    private fun ensureLookaheadWindowSize(
        lookaheadMs: Float,
        sampleRateHz: Float,
    ) {
        if (configuredSampleRateHz == sampleRateHz && configuredLookaheadMs == lookaheadMs) return
        val lookaheadSamples = ((lookaheadMs / 1000f) * sampleRateHz).roundToInt().coerceAtLeast(DETECTOR_DELAY + 1)
        configuredSampleRateHz = sampleRateHz
        configuredLookaheadMs = lookaheadMs
        releaseCoefficient = timeConstantCoefficient(RELEASE_MS, sampleRateHz)
        // Core delay = window - 1; together with the detector that is lookaheadSamples.
        window = lookaheadSamples - DETECTOR_DELAY + 1
        delayLine = FloatArray(window * channels)
        requiredGains = FloatArray(window)
        heldGains = FloatArray(window)
        minCandidates = LongArray(window)
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
        // lookahead window no longer contains anything above threshold. Long enough
        // that the recovery does not follow single bass cycles (50 Hz = 20 ms),
        // which would modulate the bass and sound like distortion.
        internal const val RELEASE_MS = 80f
        internal const val DETECTOR_DELAY = 2
        private const val HISTORY_SIZE = 2 * DETECTOR_DELAY + 1

        private fun dbToAmplitude(db: Float): Float = 10f.pow(db / 20f)

        // Largest magnitude of the Catmull-Rom curve between p1 and p2 at the quarter
        // points (p0 and p3 are the outer neighbours); 4x oversampling of that interval.
        internal fun interpolatedPeak(
            p0: Float,
            p1: Float,
            p2: Float,
            p3: Float,
        ): Float {
            var peak = 0f
            for (t in QUARTERS) {
                val t2 = t * t
                val t3 = t2 * t
                val value =
                    0.5f * (
                        2f * p1 +
                            (-p0 + p2) * t +
                            (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
                            (-p0 + 3f * p1 - 3f * p2 + p3) * t3
                    )
                peak = maxOf(peak, abs(value))
            }
            return peak
        }

        private val QUARTERS = floatArrayOf(0.25f, 0.5f, 0.75f)
    }
}
