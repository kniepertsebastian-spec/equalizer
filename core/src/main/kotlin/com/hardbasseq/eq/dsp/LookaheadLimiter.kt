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
    // (O(1) per sample instead of scanning the whole window). The rings are walked with
    // counters that wrap, not with a modulo: a division per ring access was a large part of
    // the limiter's cost. That matters: with the screen off the phone leaves the player on
    // its slow cores, and a slow limiter makes the music stutter.
    // `slot` is where the next frame goes; the frame `window - 1` behind it leaves from the
    // slot after it.
    private var window = 0
    private var delayLine = FloatArray(0)
    private var requiredGains = FloatArray(0)
    private var slot = 0

    // Slots of the frames that can still become the window minimum, oldest first.
    private var minCandidates = IntArray(0)
    private var minHead = 0
    private var minCount = 0

    // The held minimum of the last `window` frames, averaged over the last `window`
    // frames again: the ramp.
    private var heldGains = FloatArray(0)
    private var heldSum = 0.0

    private var currentGain = 1f
    private var configuredSampleRateHz = -1f
    private var configuredLookaheadMs = -1f

    // pow/exp per sample would be wasted work: both only change with the settings.
    private var cachedThresholdDb = Float.NaN
    private var thresholdLinear = 1f
    private var releaseCoefficient = 0f

    // Single-channel only, kept for callers that process one stream at a time.
    private val single = FloatArray(1)

    // One frame as a block of length 1, for processFrame().
    private val frameBlock = Array(channels) { DoubleArray(1) }

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
        if (!settings.enabled) return
        for (c in 0 until channels) frameBlock[c][0] = frame[c].toDouble()
        processBlock(frameBlock, 1, sampleRateHz)
        for (c in 0 until channels) frame[c] = frameBlock[c][0].toFloat()
    }

    /**
     * The first [frames] frames at once, limited in place; one array per channel. The same
     * result and the same state as [processFrame] frame by frame. What the player's audio
     * thread uses.
     */
    fun processBlock(
        block: Array<DoubleArray>,
        frames: Int,
        sampleRateHz: Float,
    ) {
        val currentSettings = settings
        if (!currentSettings.enabled) return

        ensureLookaheadWindowSize(currentSettings.lookaheadMs, sampleRateHz)
        if (currentSettings.thresholdDb != cachedThresholdDb) {
            cachedThresholdDb = currentSettings.thresholdDb
            thresholdLinear = dbToAmplitude(cachedThresholdDb)
        }

        val channels = channels
        val window = window
        val history = history
        val delayLine = delayLine
        val requiredGains = requiredGains
        val heldGains = heldGains
        val minCandidates = minCandidates
        val threshold = thresholdLinear
        val release = releaseCoefficient
        val shiftedSamples = (HISTORY_SIZE - 1) * channels
        val newest = (HISTORY_SIZE - 1) * channels
        val middleOffset = DETECTOR_DELAY * channels
        var slot = slot
        var minHead = minHead
        var minCount = minCount
        var heldSum = heldSum
        var currentGain = currentGain
        var previousIntervalPeak = previousIntervalPeak

        for (n in 0 until frames) {
            // Shift the history by one frame and append the new one; the frame in the
            // middle (index DETECTOR_DELAY) is the one detected now and handed to the core.
            for (k in 0 until shiftedSamples) history[k] = history[k + channels]
            for (c in 0 until channels) history[newest + c] = block[c][n].toFloat()

            var samplePeak = 0f
            var nextIntervalPeak = 0f
            for (c in 0 until channels) {
                val middle = history[middleOffset + c]
                val magnitude = abs(middle)
                if (magnitude > samplePeak) samplePeak = magnitude
                val between =
                    interpolatedPeak(
                        history[1 * channels + c],
                        middle,
                        history[3 * channels + c],
                        history[4 * channels + c],
                    )
                if (between > nextIntervalPeak) nextIntervalPeak = between
            }
            var peakEstimate = samplePeak
            if (previousIntervalPeak > peakEstimate) peakEstimate = previousIntervalPeak
            if (nextIntervalPeak > peakEstimate) peakEstimate = nextIntervalPeak
            previousIntervalPeak = nextIntervalPeak
            val requiredGain = if (peakEstimate > threshold) threshold / peakEstimate else 1f

            // The frame `window - 1` behind the newest one leaves the delay line now.
            val outSlot = if (slot + 1 == window) 0 else slot + 1

            // The candidate that fell out of the window: the frame this slot held `window`
            // frames ago. Checked before the slot is overwritten.
            if (minCount > 0 && minCandidates[minHead] == slot) {
                minHead = if (minHead + 1 == window) 0 else minHead + 1
                minCount--
            }
            for (c in 0 until channels) delayLine[slot * channels + c] = history[middleOffset + c]
            requiredGains[slot] = requiredGain
            // ... and anything not smaller than the new gain (it can never be the minimum again).
            while (minCount > 0) {
                var last = minHead + minCount - 1
                if (last >= window) last -= window
                if (requiredGains[minCandidates[last]] < requiredGain) break
                minCount--
            }
            var insertAt = minHead + minCount
            if (insertAt >= window) insertAt -= window
            minCandidates[insertAt] = slot
            minCount++

            val held = requiredGains[minCandidates[minHead]]
            heldSum += held - heldGains[slot]
            heldGains[slot] = held
            val ramped = (heldSum / window).toFloat()

            currentGain =
                if (ramped < currentGain) {
                    // Already a smooth ramp that lands on the peak's gain in time.
                    ramped
                } else {
                    release * currentGain + (1f - release) * ramped
                }

            for (c in 0 until channels) {
                val limited = delayLine[outSlot * channels + c] * currentGain
                block[c][n] = limited.coerceIn(-1f, 1f).toDouble()
            }
            slot = outSlot
        }

        this.slot = slot
        this.minHead = minHead
        this.minCount = minCount
        this.heldSum = heldSum
        this.currentGain = currentGain
        this.previousIntervalPeak = previousIntervalPeak
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
        slot = 0
        currentGain = 1f
    }

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
        minCandidates = IntArray(window)
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
