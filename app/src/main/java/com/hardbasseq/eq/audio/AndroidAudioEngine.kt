package com.hardbasseq.eq.audio

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log
import com.hardbasseq.eq.di.DefaultDispatcher
import com.hardbasseq.eq.dsp.PreEqLayout
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
private const val MBC_BAND_COUNT = 4

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

// The sub-bass band compresses this much later than the others: it carries the most
// energy, so with a shared threshold it is the one that pumps first.
private const val SUB_BAND_THRESHOLD_OFFSET_DB = 3f

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
                var dpLimiterSupported = false
                var dpInputGainSupported = false
                var dpMbcSupported = false
                var dpPreEqCutoffs: List<Float> = emptyList()
                val preEqFailures = mutableListOf<String>()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // Most capable configuration first; the first one the device accepts wins.
                    // Without the experimental switch the order is the long-standing
                    // MBC + limiter, then limiter only.
                    val attempts: List<Pair<List<Float>?, Boolean>> =
                        if (_currentSettings.value.highResolutionEq) {
                            // Finest layout first, then the coarser one, then no pre-EQ at all.
                            val layouts = listOf(PreEqLayout.CUTOFFS_HZ, PreEqLayout.COARSE_CUTOFFS_HZ)
                            layouts.flatMap { listOf(it to true, it to false) } + listOf(null to true, null to false)
                        } else {
                            listOf(null to true, null to false)
                        }
                    for ((preEqCutoffs, useMbc) in attempts) {
                        try {
                            dynamicsProcessing =
                                DynamicsProcessing(0, session.sessionId, createDynamicsConfig(useMbc, preEqCutoffs))
                            dpPreEqCutoffs = preEqCutoffs.orEmpty()
                            dpMbcSupported = useMbc
                            dpSupported = true
                            dpLimiterSupported = true
                            dpInputGainSupported = true
                            break
                        } catch (e: Exception) {
                            Log.w(TAG, "DynamicsProcessing config (preEq=${preEqCutoffs?.size}, mbc=$useMbc) unavailable", e)
                            if (preEqCutoffs != null) {
                                val cause = "${e.javaClass.simpleName}: ${e.localizedMessage}"
                                preEqFailures += "${preEqCutoffs.size} bands, mbc=$useMbc: $cause"
                            }
                        }
                    }
                    if (!dpSupported) {
                        Log.w(TAG, "DynamicsProcessing not supported on session ${session.sessionId}")
                    }
                }

                _capabilities.value =
                    AudioCapabilities(
                        hasEqualizer = true,
                        hasDynamicsProcessing = dpSupported,
                        hasBassBoost = staticDeviceCapabilities.hasBassBoost,
                        hasLoudnessEnhancer = staticDeviceCapabilities.hasLoudnessEnhancer,
                        totalEffectCount = if (dpSupported) 2 else 1,
                        bands = bandCaps,
                        hasInputGain = dpInputGainSupported,
                        hasLimiter = dpLimiterSupported,
                        hasMbc = dpMbcSupported,
                        preEqCutoffsHz = dpPreEqCutoffs,
                        // Also reported when a coarser layout worked: the finer one was refused.
                        preEqUnavailableReason = preEqFailures.takeIf { it.isNotEmpty() }?.joinToString("; "),
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

        private fun createDynamicsConfig(
            mbcEnabled: Boolean,
            preEqCutoffs: List<Float>?,
        ): DynamicsProcessing.Config {
            val preEqEnabled = preEqCutoffs != null
            val cutoffs = preEqCutoffs.orEmpty()
            val builder =
                DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    preEqEnabled,
                    if (preEqEnabled) cutoffs.size else 0,
                    mbcEnabled,
                    if (mbcEnabled) MBC_BAND_COUNT else 0,
                    false,
                    0,
                    true,
                )
            if (preEqEnabled) {
                builder.setPreferredFrameDuration(PRE_EQ_FRAME_DURATION_MS)
                // The band edges are fixed at creation; only the gains change afterwards.
                val eq = DynamicsProcessing.Eq(true, true, cutoffs.size)
                cutoffs.forEachIndexed { i, hz -> eq.setBand(i, DynamicsProcessing.EqBand(true, hz, 0f)) }
                builder.setPreEqAllChannelsTo(eq)
            }
            return builder.build()
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
                        dp.setInputGainAllChannelsTo(settings.inputGainDb.coerceIn(-15f, 0f))

                        if (usePreEq) {
                            preEqCutoffs.forEachIndexed { i, cutoffHz ->
                                val gainDb = (settings.preEqGainsDb.getOrNull(i) ?: 0f).coerceIn(-15f, 15f)
                                dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoffHz, gainDb))
                            }
                        }

                        if (capabilities.value.hasMbc) {
                            val mbcRatio = if (settings.mbcEnabled) settings.mbcRatio.coerceIn(1f, 6f) else 1f
                            val mbcThresholdDb =
                                if (settings.mbcEnabled) settings.mbcThresholdDb.coerceIn(-30f, 0f) else 0f
                            // Balanced makeup-gain heuristic (reduced from 0.85 / 6 dB cap to
                            // 0.45 / 3 dB cap): previously, adding up to +6 dB postGain across all
                            // 4 MBC bands drove the downstream brickwall limiter into constant
                            // saturation, crushing transients and mid/high clarity even with 34-band
                            // mode turned off.
                            val mbcMakeupGainDb =
                                if (mbcRatio > 1f) {
                                    ((-mbcThresholdDb) * (1f - 1f / mbcRatio) * 0.45f).coerceIn(0f, 3.0f)
                                } else {
                                    0f
                                }
                            val mbcBands =
                                listOf(
                                    // Sub-bass: slow, and its threshold sits a little higher so the compressor
                                    // does not hold the kick's fundamental down.
                                    MbcBandSettings(45f, 20f, 160f, mbcMakeupGainDb, SUB_BAND_THRESHOLD_OFFSET_DB),
                                    // Kick body: tight enough to catch the hit without pumping the tail.
                                    MbcBandSettings(130f, 12f, 90f, mbcMakeupGainDb),
                                    // Midrange & Treble: gentler makeup gain so vocals and highs stay clear
                                    // and don't slam the final limiter on every kick hit.
                                    MbcBandSettings(1500f, 10f, 110f, mbcMakeupGainDb * 0.6f),
                                    MbcBandSettings(20000f, 5f, 80f, mbcMakeupGainDb * 0.5f),
                                )
                            mbcBands.forEachIndexed { bandIndex, band ->
                                dp.setMbcBandAllChannelsTo(
                                    bandIndex,
                                    DynamicsProcessing.MbcBand(
                                        true,
                                        band.cutoffFrequencyHz,
                                        band.attackMs,
                                        band.releaseMs,
                                        mbcRatio,
                                        (mbcThresholdDb + band.thresholdOffsetDb).coerceAtMost(0f),
                                        6f, // soft knee
                                        -80f, // effectively disable the noise gate
                                        1f, // no expansion
                                        0f, // preGain
                                        band.postGainDb, // band-appropriate postGain
                                    ),
                                )
                            }
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

private data class MbcBandSettings(
    val cutoffFrequencyHz: Float,
    val attackMs: Float,
    val releaseMs: Float,
    val postGainDb: Float,
    val thresholdOffsetDb: Float = 0f,
)
