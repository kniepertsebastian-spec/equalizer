package com.auracurve.app.audio

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log
import com.auracurve.app.di.DefaultDispatcher
import com.auracurve.app.dsp.PreEqLayout
import com.auracurve.app.eq.MAX_SPEED_GAIN_DB
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AndroidAudioEngine"

// Roadmap 13, P13-1: five compressor bands, so the upper bass/mud (135-350 Hz) no longer shares a
// band with the vocals' fundamentals. A device that refuses five gets the earlier four.
internal const val MBC_BAND_COUNT = 5
internal const val MBC_FALLBACK_BAND_COUNT = 4

// Frame length for the frequency-domain DynamicsProcessing while the pre-EQ runs. The
// platform default of 10 ms gives an FFT bin spacing of roughly 47 Hz at 48 kHz (AOSP
// DPFrequency), far too coarse for the pre-EQ's bass bands 5-10 Hz apart: they would
// share one or two bins. 20 ms halves the spacing. Longer frames resolve the bass even
// finer but smear kick transients (pre-echo) and add latency; to be checked by ear and
// with a sweep on a real device.
private const val PRE_EQ_FRAME_DURATION_MS = 20f

// Limiter release. 50 ms let the gain recover within a few cycles of 40-60 Hz bass and
// follow the waveform - heard as distortion on heavily boosted bass.
private const val LIMITER_RELEASE_MS = 120f

@Singleton
class AndroidAudioEngine
    @Inject
    constructor(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
        private val effectRepository: AudioEffectRepository,
    ) : AudioEngine {
        // Secondary constructor preserves compatibility with any callers/tests that only pass a dispatcher.
        constructor(dispatcher: CoroutineDispatcher) : this(dispatcher, AndroidAudioEffectRepository())

        private val staticDeviceCapabilities: AudioCapabilities by lazy {
            runCatching {
                AudioCapabilitiesMapper.map(
                    effectRepository.queryAvailableEffects(),
                    effectRepository.knownEffectTypeIds,
                )
            }.getOrDefault(AudioCapabilities())
        }

        private val _capabilities = MutableStateFlow(staticDeviceCapabilities)
        override val capabilities: StateFlow<AudioCapabilities> = _capabilities.asStateFlow()

        private val _state = MutableStateFlow<AudioEngineState>(AudioEngineState.Detached)
        override val state: StateFlow<AudioEngineState> = _state.asStateFlow()

        private val _currentSettings = MutableStateFlow(ProcessingSettings())
        override val currentSettings: StateFlow<ProcessingSettings> = _currentSettings.asStateFlow()

        @Volatile
        private var equalizer: Equalizer? = null

        @Volatile
        private var dynamicsProcessing: DynamicsProcessing? = null

        // Tracks the session a Retrying/Error cycle belongs to, so a stale retry
        // timer that fires after the session already changed/disappeared can
        // recognize it's obsolete (docs/STATE_MACHINE.md §4, "Timer-Abbruch").
        // Also what the manual "Erneut versuchen" button (retry()) re-attaches to.
        @Volatile
        private var currentSessionId: Int? = null

        private val isAttached = AtomicBoolean(false)

        // Serializes attach/detach/apply so a fast run of UI events (e.g. dragging
        // a band slider while a route change triggers a re-attach) can't interleave
        // and leave the Equalizer/DynamicsProcessing instances in a half-updated
        // state (roadmap.md M5: "Schutz vor Parameter-Sprüngen und Race Conditions").
        // Also now guards the retry state machine's transitions.
        private val mutex = Mutex()

        // Backs the Retrying backoff timers. A SupervisorJob so one cancelled/failed
        // retry can't take down others, though in practice only one is ever
        // in flight at a time (each new attach()/detach() cancels the previous).
        private val engineScope = CoroutineScope(SupervisorJob() + dispatcher)
        private var retryJob: Job? = null

        override suspend fun attach(session: AudioSession): Boolean =
            mutex.withLock {
                cancelRetryLocked()
                attachLocked(session, attempt = 0)
            }

        override suspend fun detach() =
            mutex.withLock {
                cancelRetryLocked()
                releaseEffectsLocked()
                currentSessionId = null
                _capabilities.value = staticDeviceCapabilities
                _state.value = AudioEngineState.Listening
            }

        override fun markDetached() {
            // Not suspend - Service.onDestroy() isn't a coroutine. Best-effort immediate
            // teardown, plus serialized cleanup on engineScope if mutex is currently held.
            retryJob?.cancel()
            retryJob = null
            currentSessionId = null
            _state.value = AudioEngineState.Detached
            if (mutex.tryLock()) {
                try {
                    releaseEffectsLocked()
                    _capabilities.value = staticDeviceCapabilities
                } finally {
                    mutex.unlock()
                }
            } else {
                engineScope.launch {
                    mutex.withLock {
                        if (_state.value is AudioEngineState.Detached) {
                            releaseEffectsLocked()
                            _capabilities.value = staticDeviceCapabilities
                        }
                    }
                }
            }
        }

        override suspend fun retry(): Boolean =
            mutex.withLock {
                val sessionId = currentSessionId ?: return@withLock false
                cancelRetryLocked()
                attachLocked(AudioSession(sessionId = sessionId), attempt = 0)
            }

        // attempt == 0 means "fresh session, no backoff" (Listening -> Attaching,
        // or a manual retry()/player switch) and is shown as Attaching. attempt > 0
        // means this call originated from a Retrying backoff timer.
        private fun attachLocked(
            session: AudioSession,
            attempt: Int,
        ): Boolean {
            releaseEffectsLocked()

            if (attempt == 0) {
                _state.value = AudioEngineState.Attaching(session.sessionId)
            }
            currentSessionId = session.sessionId

            try {
                val eq = Equalizer(0, session.sessionId)
                val numBands = eq.numberOfBands.toInt()
                val minGainMb = eq.bandLevelRange[0]
                val maxGainMb = eq.bandLevelRange[1]

                val bandCaps =
                    (0 until numBands).map { bandIdx ->
                        val centerHz = eq.getCenterFreq(bandIdx.toShort()) / 1000
                        EqualizerBandCapabilities(
                            index = bandIdx,
                            centerFreqHz = centerHz,
                            minGainDb = minGainMb / 100f,
                            maxGainDb = maxGainMb / 100f,
                        )
                    }
                equalizer = eq

                var dpSupported = false
                var chosen: DynamicsAttempt? = null
                val failures = mutableListOf<Pair<DynamicsAttempt, String>>()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // Most capable configuration first; the first one the device accepts wins
                    // (roadmap 13: PreEQ + 5-band MBC + PostEQ, then fewer stages and bands).
                    for (attempt in DynamicsAttempts.order(_currentSettings.value.highResolutionEq)) {
                        try {
                            dynamicsProcessing = DynamicsProcessing(0, session.sessionId, createDynamicsConfig(attempt))
                            chosen = attempt
                            dpSupported = true
                            break
                        } catch (e: Exception) {
                            Log.w(
                                TAG,
                                "DynamicsProcessing config (preEq=${attempt.preEqCutoffsHz?.size}, mbc=${attempt.mbcBandCount}, " +
                                    "postEq=${attempt.postEq}) unavailable",
                                e,
                            )
                            failures += attempt to "${e.javaClass.simpleName}: ${e.localizedMessage}"
                        }
                    }
                    if (!dpSupported) {
                        Log.w(TAG, "DynamicsProcessing not supported on session ${session.sessionId}")
                    }
                }
                val dpPreEqCutoffs = chosen?.preEqCutoffsHz.orEmpty()

                _capabilities.value =
                    AudioCapabilities(
                        hasEqualizer = true,
                        hasDynamicsProcessing = dpSupported,
                        hasBassBoost = staticDeviceCapabilities.hasBassBoost,
                        hasLoudnessEnhancer = staticDeviceCapabilities.hasLoudnessEnhancer,
                        totalEffectCount = if (dpSupported) 2 else 1,
                        bands = bandCaps,
                        hasInputGain = dpSupported,
                        hasLimiter = dpSupported,
                        hasMbc = (chosen?.mbcBandCount ?: 0) > 0,
                        preEqCutoffsHz = dpPreEqCutoffs,
                        // Also reported when a coarser layout worked: the finer one was refused.
                        preEqUnavailableReason = DynamicsAttempts.preEqUnavailableReason(failures, chosen),
                        mbcBandCount = chosen?.mbcBandCount ?: 0,
                        postEqCutoffsHz = if (chosen?.postEq == true) dpPreEqCutoffs else emptyList(),
                    )

                isAttached.set(true)
                // Only claim Active once apply() has actually succeeded - never
                // set it optimistically before that (roadmap-2026.md M1 acceptance
                // criterion: "Die UI behauptet nie 'Aktiv', wenn apply()
                // fehlgeschlagen ist"). applyInternal()'s own failure path already
                // transitions to LostControl/Retrying if this fails.
                return applyInternal(_currentSettings.value, session.sessionId)
            } catch (e: UnsupportedOperationException) {
                Log.w(TAG, "Session ${session.sessionId} does not support the required audio effects", e)
                releaseEffectsLocked()
                _capabilities.value = staticDeviceCapabilities
                _state.value = AudioEngineState.Unsupported("Device does not support the required audio effects: ${e.localizedMessage}")
                return false
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Session ${session.sessionId} rejected effect parameters", e)
                releaseEffectsLocked()
                _capabilities.value = staticDeviceCapabilities
                _state.value = AudioEngineState.Unsupported("Unsupported session parameters: ${e.localizedMessage}")
                return false
            } catch (e: Exception) {
                Log.w(TAG, "Transient failure attaching to session ${session.sessionId} (attempt $attempt)", e)
                releaseEffectsLocked()
                _capabilities.value = staticDeviceCapabilities
                scheduleRetryLocked(session.sessionId, attempt + 1, "${e.javaClass.simpleName}: ${e.localizedMessage ?: e.toString()}")
                return false
            }
        }

        private fun releaseEffectsLocked() {
            isAttached.set(false)
            val eqToRelease = equalizer
            equalizer = null
            try {
                eqToRelease?.enabled = false
                eqToRelease?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing Equalizer", e)
            }

            val dpToRelease = dynamicsProcessing
            dynamicsProcessing = null
            try {
                dpToRelease?.enabled = false
                dpToRelease?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing DynamicsProcessing", e)
            }
        }

        override suspend fun apply(settings: ProcessingSettings): Boolean =
            mutex.withLock {
                val previousHighRes = _currentSettings.value.highResolutionEq
                _currentSettings.value = settings
                if (!isAttached.get()) return@withLock false
                val sessionId = currentSessionId ?: return@withLock false

                // If the user toggled highResolutionEq while attached, re-configure
                // DynamicsProcessing so switching between 5-band and 34-band takes
                // effect immediately and deterministically.
                if (previousHighRes != settings.highResolutionEq) {
                    return@withLock attachLocked(AudioSession(sessionId = sessionId), attempt = 0)
                }
                applyInternal(settings, sessionId)
            }

        private fun createDynamicsConfig(attempt: DynamicsAttempt): DynamicsProcessing.Config {
            val cutoffs = attempt.preEqCutoffsHz.orEmpty()
            val preEqEnabled = attempt.preEqCutoffsHz != null
            val postEqEnabled = preEqEnabled && attempt.postEq
            val mbcEnabled = attempt.mbcBandCount > 0
            val builder =
                DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    preEqEnabled,
                    if (preEqEnabled) cutoffs.size else 0,
                    mbcEnabled,
                    attempt.mbcBandCount,
                    postEqEnabled,
                    if (postEqEnabled) cutoffs.size else 0,
                    true,
                )
            if (preEqEnabled) {
                builder.setPreferredFrameDuration(PRE_EQ_FRAME_DURATION_MS)
                // The band edges are fixed at creation; only the gains change afterwards.
                builder.setPreEqAllChannelsTo(flatEq(cutoffs))
                // Roadmap 13, P13-2: the post-EQ has the pre-EQ's layout, so pre + post per band is
                // exactly the curve (PostEqSplit).
                if (postEqEnabled) builder.setPostEqAllChannelsTo(flatEq(cutoffs))
            }
            return builder.build()
        }

        private fun flatEq(cutoffs: List<Float>): DynamicsProcessing.Eq =
            DynamicsProcessing.Eq(true, true, cutoffs.size).apply {
                cutoffs.forEachIndexed { i, hz -> setBand(i, DynamicsProcessing.EqBand(true, hz, 0f)) }
            }

        private fun applyInternal(
            settings: ProcessingSettings,
            sessionId: Int,
        ): Boolean {
            val eq = equalizer ?: return false
            try {
                val shouldEnable = settings.masterEnabled && !settings.bypass
                val preEqCutoffs = capabilities.value.preEqCutoffsHz
                val usePreEq = settings.highResolutionEq && preEqCutoffs.isNotEmpty() && dynamicsProcessing != null
                // Never both: the pre-EQ replaces the classic equalizer, which stays only as
                // the source of the band layout for the UI.
                eq.enabled = shouldEnable && !usePreEq

                if (shouldEnable && !usePreEq) {
                    settings.bandGainsDb.forEach { (bandIdx, gainDb) ->
                        val bandCaps = capabilities.value.bands.getOrNull(bandIdx)
                        if (bandCaps != null) {
                            val clampedGainDb = gainDb.coerceIn(bandCaps.minGainDb, bandCaps.maxGainDb)
                            val levelMb = (clampedGainDb * 100f).toInt().toShort()
                            eq.setBandLevel(bandIdx.toShort(), levelMb)
                        }
                    }
                }

                dynamicsProcessing?.let { dp ->
                    dp.enabled = shouldEnable
                    if (shouldEnable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        // Above 0 dB only with the F6 speed gain (EqController.setSpeedGainDb), which forces the limiter on.
                        dp.setInputGainAllChannelsTo(
                            settings.inputGainDb.coerceIn(-15f, if (settings.limiterEnabled) MAX_SPEED_GAIN_DB else 0f),
                        )

                        if (usePreEq) {
                            preEqCutoffs.forEachIndexed { i, cutoffHz ->
                                val gainDb = (settings.preEqGainsDb.getOrNull(i) ?: 0f).coerceIn(-15f, 15f)
                                dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoffHz, gainDb))
                            }
                        }
                        // Roadmap 13, P13-2: the share of the tone shaping behind the compressor. Settings
                        // computed before the post-EQ existed have no post gains: 0 dB, all of it in the pre-EQ.
                        capabilities.value.postEqCutoffsHz.forEachIndexed { i, cutoffHz ->
                            val gainDb = if (usePreEq) (settings.postEqGainsDb.getOrNull(i) ?: 0f).coerceIn(-15f, 15f) else 0f
                            dp.setPostEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoffHz, gainDb))
                        }

                        val mbcBands =
                            MbcTuning.bands(
                                bandCount = capabilities.value.mbcBandCount,
                                enabled = settings.mbcEnabled,
                                ratio = settings.mbcRatio,
                                thresholdDb = settings.mbcThresholdDb,
                            )
                        mbcBands.forEachIndexed { bandIndex, band ->
                            dp.setMbcBandAllChannelsTo(
                                bandIndex,
                                DynamicsProcessing.MbcBand(
                                    true,
                                    band.cutoffFrequencyHz,
                                    band.attackMs,
                                    band.releaseMs,
                                    band.ratio,
                                    band.thresholdDb,
                                    band.kneeWidthDb,
                                    -80f, // effectively disable the noise gate
                                    1f, // no expansion
                                    0f, // preGain
                                    band.postGainDb,
                                ),
                            )
                        }

                        val safeThresholdDb = settings.limiterThresholdDb.coerceAtMost(0f)
                        val limiter =
                            DynamicsProcessing.Limiter(
                                true,
                                settings.limiterEnabled,
                                0, // channel 0
                                2f, // slightly gentler 2ms attack preserves kick transient click/clarity
                                LIMITER_RELEASE_MS,
                                10f, // ratio
                                safeThresholdDb, // threshold
                                0f, // postGain
                            )
                        dp.setLimiterAllChannelsTo(limiter)
                    }
                }
                _state.value = AudioEngineState.Active(sessionId)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error applying settings to AudioEngine", e)
                val detail = "${e.javaClass.simpleName}: ${e.localizedMessage ?: e.toString()}"
                _state.value = AudioEngineState.LostControl(sessionId, "Error applying settings: $detail")
                scheduleRetryLocked(sessionId, attempt = 1, reason = detail)
                return false
            }
        }

        private fun cancelRetryLocked() {
            retryJob?.cancel()
            retryJob = null
        }

        private fun scheduleRetryLocked(
            sessionId: Int,
            attempt: Int,
            reason: String,
        ) {
            if (attempt > MAX_RETRY_ATTEMPTS) {
                _state.value = AudioEngineState.Error("Giving up on session $sessionId after $MAX_RETRY_ATTEMPTS attempts: $reason")
                return
            }

            val delayMs = RetryBackoff.delayMillisFor(attempt)
            _state.value = AudioEngineState.Retrying(sessionId, attempt, System.currentTimeMillis() + delayMs)

            retryJob =
                engineScope.launch {
                    delay(delayMs)
                    mutex.withLock {
                        if (currentSessionId != sessionId) return@withLock
                        attachLocked(AudioSession(sessionId = sessionId), attempt)
                    }
                }
        }
    }

/** One DynamicsProcessing layout to try (roadmap 13); the engine takes the first the device accepts. */
internal data class DynamicsAttempt(
    // Upper band edges of the pre-EQ; null = no pre-EQ (the classic equalizer shapes the curve).
    val preEqCutoffsHz: List<Float>?,
    // 0 = no multiband compressor.
    val mbcBandCount: Int,
    // A post-EQ behind the compressor, with the pre-EQ's layout. Only together with a pre-EQ.
    val postEq: Boolean = false,
)

internal object DynamicsAttempts {
    /**
     * Most capable first. With the high-resolution EQ, per pre-EQ layout (fine, then coarse):
     * PreEQ + 5-band MBC + PostEQ, then PreEQ + 5-band MBC, + 4-band MBC, without MBC; then the same
     * MBC steps without a pre-EQ. Without it only those last three.
     */
    fun order(highResolutionEq: Boolean): List<DynamicsAttempt> {
        val withoutPreEq = mbcSteps(null)
        if (!highResolutionEq) return withoutPreEq
        val layouts = listOf(PreEqLayout.CUTOFFS_HZ, PreEqLayout.COARSE_CUTOFFS_HZ)
        return layouts.flatMap { layout -> listOf(DynamicsAttempt(layout, MBC_BAND_COUNT, postEq = true)) + mbcSteps(layout) } +
            withoutPreEq
    }

    private fun mbcSteps(layout: List<Float>?) = listOf(MBC_BAND_COUNT, MBC_FALLBACK_BAND_COUNT, 0).map { DynamicsAttempt(layout, it) }

    /**
     * Why the high-resolution EQ is not (fully) in use: the refusals of every pre-EQ layout other
     * than the one in use - a finer one when the coarse layout runs, all of them when none does.
     * A refused post-EQ or 5-band MBC with the layout that does run is no reason: the EQ is there.
     */
    fun preEqUnavailableReason(
        failures: List<Pair<DynamicsAttempt, String>>,
        chosen: DynamicsAttempt?,
    ): String? =
        failures
            .filter { (attempt, _) -> attempt.preEqCutoffsHz != null && attempt.preEqCutoffsHz != chosen?.preEqCutoffsHz }
            .map { (attempt, cause) -> "${attempt.preEqCutoffsHz?.size} bands, mbc=${attempt.mbcBandCount}: $cause" }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString("; ")
}

internal data class MbcBandSettings(
    val cutoffFrequencyHz: Float,
    val attackMs: Float,
    val releaseMs: Float,
    val ratio: Float,
    val thresholdDb: Float,
    val kneeWidthDb: Float,
    val postGainDb: Float,
)

/**
 * The multiband compressor's bands (roadmap 13, P13-1). The preset gives one ratio and threshold;
 * each band takes what suits its range instead of all of them the same: sub and kick get the full
 * (capped) ratio with attacks slow enough to let the kick's transient through, the mud band a
 * moderate one, vocals and treble only a gentle one with a higher threshold and little makeup gain -
 * so a hard-compressed bass no longer drags the voices and hi-hats down with it.
 */
internal object MbcTuning {
    // Balanced makeup-gain heuristic (0.45 / 3 dB cap): more drove the final limiter into constant
    // saturation, crushing transients and mid/high clarity.
    private const val MAKEUP_SHARE = 0.45f
    private const val MAX_MAKEUP_DB = 3f

    fun bands(
        bandCount: Int,
        enabled: Boolean,
        ratio: Float,
        thresholdDb: Float,
    ): List<MbcBandSettings> {
        if (bandCount <= 0) return emptyList()
        val r = if (enabled) ratio.coerceIn(1f, 6f) else 1f
        val threshold = if (enabled) thresholdDb.coerceIn(-30f, 0f) else 0f
        val makeup = if (r > 1f) ((-threshold) * (1f - 1f / r) * MAKEUP_SHARE).coerceIn(0f, MAX_MAKEUP_DB) else 0f

        fun scaled(share: Float) = 1f + (r - 1f) * share

        fun band(
            cutoffHz: Float,
            attackMs: Float,
            releaseMs: Float,
            ratio: Float,
            thresholdOffsetDb: Float,
            kneeDb: Float,
            makeupShare: Float,
        ) = MbcBandSettings(
            cutoffFrequencyHz = cutoffHz,
            attackMs = attackMs,
            releaseMs = releaseMs,
            ratio = ratio,
            thresholdDb = (threshold + thresholdOffsetDb).coerceAtMost(0f),
            kneeWidthDb = kneeDb,
            postGainDb = makeup * makeupShare,
        )
        return if (bandCount >= MBC_BAND_COUNT) {
            listOf(
                // Sub-bass: slow, and a little later, so it does not hold the kick's fundamental down.
                band(50f, 28f, 170f, minOf(r, 3.5f), 3f, 10f, 1f),
                // Kick punch: the attack lets the transient through, the release follows the tail.
                band(135f, 20f, 100f, minOf(r, 4f), 0f, 8f, 1f),
                // Low mids / mud: its own band now, decoupled from the vocals.
                band(350f, 13f, 100f, scaled(0.55f), 0f, 8f, 0.35f),
                // Vocals and mids: gentle, so voices stay steady when the bass is compressed.
                band(3000f, 10f, 110f, minOf(scaled(0.25f), 1.8f), 4f, 8f, 0.25f),
                // Presence and air: gentle, no hissing hi-hats.
                band(20000f, 5f, 80f, minOf(scaled(0.2f), 1.6f), 4f, 8f, 0.2f),
            )
        } else {
            // Four bands: the earlier crossovers, with the same per-range ratios.
            listOf(
                band(45f, 25f, 160f, minOf(r, 3.5f), 3f, 10f, 1f),
                band(130f, 18f, 100f, minOf(r, 4f), 0f, 8f, 1f),
                // Mud and vocals still share this band: between the mud and the vocal ratio.
                band(1500f, 12f, 110f, scaled(0.4f), 2f, 8f, 0.35f),
                band(20000f, 5f, 80f, minOf(scaled(0.2f), 1.6f), 4f, 8f, 0.2f),
            )
        }
    }
}
