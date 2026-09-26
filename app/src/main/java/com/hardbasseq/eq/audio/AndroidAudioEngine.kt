package com.hardbasseq.eq.audio

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log
import com.hardbasseq.eq.di.DefaultDispatcher
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
private const val MBC_BAND_COUNT = 3

@Singleton
class AndroidAudioEngine
    @Inject
    constructor(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : AudioEngine {
        private val _capabilities = MutableStateFlow(AudioCapabilities())
        override val capabilities: StateFlow<AudioCapabilities> = _capabilities.asStateFlow()

        private val _state = MutableStateFlow<AudioEngineState>(AudioEngineState.Detached)
        override val state: StateFlow<AudioEngineState> = _state.asStateFlow()

        private val _currentSettings = MutableStateFlow(ProcessingSettings())
        override val currentSettings: StateFlow<ProcessingSettings> = _currentSettings.asStateFlow()

        private var equalizer: Equalizer? = null
        private var dynamicsProcessing: DynamicsProcessing? = null

        // Tracks the session a Retrying/Error cycle belongs to, so a stale retry
        // timer that fires after the session already changed/disappeared can
        // recognize it's obsolete (docs/STATE_MACHINE.md §4, "Timer-Abbruch").
        // Also what the manual "Erneut versuchen" button (retry()) re-attaches to.
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
                _state.value = AudioEngineState.Listening
            }

        override fun markDetached() {
            // Not suspend - Service.onDestroy() isn't a coroutine. Best-effort;
            // only meaningful at process/service teardown, not everyday operation.
            retryJob?.cancel()
            retryJob = null
            releaseEffectsLocked()
            currentSessionId = null
            _state.value = AudioEngineState.Detached
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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val dp =
                            try {
                                val fullConfig = createDynamicsConfig(mbcEnabled = true)
                                DynamicsProcessing(0, session.sessionId, fullConfig).also {
                                    dpMbcSupported = true
                                }
                            } catch (mbcError: Exception) {
                                Log.w(TAG, "MBC unavailable; falling back to limiter-only processing", mbcError)
                                val fallbackConfig = createDynamicsConfig(mbcEnabled = false)
                                DynamicsProcessing(0, session.sessionId, fallbackConfig)
                            }
                        dynamicsProcessing = dp
                        dpSupported = true
                        dpLimiterSupported = true
                        dpInputGainSupported = true
                    } catch (e: Exception) {
                        Log.w(TAG, "DynamicsProcessing not supported on session ${session.sessionId}", e)
                    }
                }

                _capabilities.value =
                    AudioCapabilities(
                        hasEqualizer = true,
                        hasDynamicsProcessing = dpSupported,
                        hasBassBoost = false,
                        hasLoudnessEnhancer = false,
                        totalEffectCount = if (dpSupported) 2 else 1,
                        bands = bandCaps,
                        hasInputGain = dpInputGainSupported,
                        hasLimiter = dpLimiterSupported,
                        hasMbc = dpMbcSupported,
                    )

                isAttached.set(true)
                // Only claim Active once apply() has actually succeeded - never
                // set it optimistically before that (roadmap-2026.md M1 acceptance
                // criterion: "Die UI behauptet nie 'Aktiv', wenn apply()
                // fehlgeschlagen ist"). applyInternal()'s own failure path already
                // transitions to LostControl/Retrying if this fails.
                return applyInternal(_currentSettings.value, session.sessionId)
            } catch (e: UnsupportedOperationException) {
                // Per AudioEffect's documented contract, thrown when the device
                // genuinely doesn't implement the effect - a real capability gap,
                // not worth retrying.
                Log.w(TAG, "Session ${session.sessionId} does not support the required audio effects", e)
                releaseEffectsLocked()
                _state.value = AudioEngineState.Unsupported("Device does not support the required audio effects: ${e.localizedMessage}")
                return false
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Session ${session.sessionId} rejected effect parameters", e)
                releaseEffectsLocked()
                _state.value = AudioEngineState.Unsupported("Unsupported session parameters: ${e.localizedMessage}")
                return false
            } catch (e: Exception) {
                // Per AudioEffect's documented contract, IllegalStateException means
                // "effect engine not initialized" - typically because the session
                // already went away again. Treat this (and anything else
                // unexpected) as transient and worth retrying rather than giving up.
                Log.w(TAG, "Transient failure attaching to session ${session.sessionId} (attempt $attempt)", e)
                releaseEffectsLocked()
                scheduleRetryLocked(session.sessionId, attempt + 1, e.localizedMessage ?: e.toString())
                return false
            }
        }

        private fun releaseEffectsLocked() {
            isAttached.set(false)
            try {
                equalizer?.enabled = false
                equalizer?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing Equalizer", e)
            } finally {
                equalizer = null
            }

            try {
                dynamicsProcessing?.enabled = false
                dynamicsProcessing?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing DynamicsProcessing", e)
            } finally {
                dynamicsProcessing = null
            }
        }

        override suspend fun apply(settings: ProcessingSettings): Boolean =
            mutex.withLock {
                _currentSettings.value = settings
                if (!isAttached.get()) return@withLock false
                val sessionId = currentSessionId ?: return@withLock false
                applyInternal(settings, sessionId)
            }

        private fun createDynamicsConfig(mbcEnabled: Boolean): DynamicsProcessing.Config =
            DynamicsProcessing.Config
                .Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    false,
                    0,
                    mbcEnabled,
                    if (mbcEnabled) MBC_BAND_COUNT else 0,
                    false,
                    0,
                    true,
                ).build()

        private fun applyInternal(
            settings: ProcessingSettings,
            sessionId: Int,
        ): Boolean {
            val eq = equalizer ?: return false
            try {
                val shouldEnable = settings.masterEnabled && !settings.bypass
                eq.enabled = shouldEnable

                if (shouldEnable) {
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

                        if (capabilities.value.hasMbc) {
                            val mbcRatio = if (settings.mbcEnabled) settings.mbcRatio.coerceIn(1f, 6f) else 1f
                            val mbcThresholdDb =
                                if (settings.mbcEnabled) settings.mbcThresholdDb.coerceIn(-30f, 0f) else 0f
                            // Standard compressor makeup-gain heuristic (a fraction of the average
                            // gain reduction the ratio/threshold combination implies), capped.
                            // Without this, every preset ends up net quieter than flat instead of
                            // punchier: the Limiter below (unchanged, hard 10:1 ceiling) still
                            // protects the final output, so this only restores loudness the
                            // compression itself removed. Restore fraction raised 0.5->0.7 and the
                            // cap 4dB->5dB in Session 24 (roadmap.md), alongside a matching
                            // loosening of the broadband input-gain safety ratio in
                            // MainViewModel, at the user's request to give back more of the punch
                            // these safety layers were taking out on kick-heavy material - same
                            // reasoning as the Session 17 input-gain change, the Limiter downstream
                            // is still the actual, unchanged clipping backstop.
                            val mbcMakeupGainDb =
                                if (mbcRatio > 1f) {
                                    ((-mbcThresholdDb) * (1f - 1f / mbcRatio) * 0.7f).coerceIn(0f, 5f)
                                } else {
                                    0f
                                }
                            // Low band's 180ms->100ms: a real-device report described the mix
                            // getting audibly "hallend"/pumpy with headphone mode on. Headphone
                            // mode adds a bass shelf ahead of this compressor (see
                            // HeadphoneComfortCurve/combinedCurve in MainViewModel), so the low
                            // band now hits the threshold harder and its long recovery after each
                            // kick was audible as a swelling tail. 100ms still comfortably clears
                            // the ~8-16ms period of 60-120Hz content (avoiding the compressor
                            // itself distorting the waveform, why this band's release was long
                            // to begin with) while cutting the audible recovery time nearly in
                            // half.
                            val mbcBands =
                                listOf(
                                    MbcBandSettings(120f, 15f, 100f),
                                    MbcBandSettings(1500f, 8f, 120f),
                                    MbcBandSettings(20000f, 3f, 80f),
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
                                        mbcThresholdDb,
                                        6f, // soft knee
                                        -80f, // effectively disable the noise gate
                                        1f, // no expansion
                                        0f, // preGain
                                        mbcMakeupGainDb, // postGain
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
                                1f, // attack ms
                                50f, // release ms
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
                _state.value = AudioEngineState.LostControl(sessionId, "Error applying settings: ${e.localizedMessage}")
                // Same tick, per docs/STATE_MACHINE.md §3: LostControl has no
                // dwell time of its own, it's only a UI distinction from "cold"
                // Retrying.
                scheduleRetryLocked(sessionId, attempt = 1, reason = e.localizedMessage ?: e.toString())
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
                        // The session may have changed or disappeared while this
                        // timer was waiting - a newer attach()/detach() already
                        // moved currentSessionId on, so this retry is obsolete.
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
)
