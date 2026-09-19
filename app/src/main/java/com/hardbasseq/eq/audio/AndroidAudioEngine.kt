package com.hardbasseq.eq.audio

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AndroidAudioEngine"

@Singleton
class AndroidAudioEngine
    @Inject
    constructor() : AudioEngine {
        private val _capabilities = MutableStateFlow(AudioCapabilities())
        override val capabilities: StateFlow<AudioCapabilities> = _capabilities.asStateFlow()

        private val _state = MutableStateFlow<AudioEngineState>(AudioEngineState.Detached)
        override val state: StateFlow<AudioEngineState> = _state.asStateFlow()

        private val _currentSettings = MutableStateFlow(ProcessingSettings())
        override val currentSettings: StateFlow<ProcessingSettings> = _currentSettings.asStateFlow()

        private var equalizer: Equalizer? = null
        private var dynamicsProcessing: DynamicsProcessing? = null
        private var currentSessionId: Int? = null

        private val isAttached = AtomicBoolean(false)

        // Serializes attach/detach/apply so a fast run of UI events (e.g. dragging
        // a band slider while a route change triggers a re-attach) can't interleave
        // and leave the Equalizer/DynamicsProcessing instances in a half-updated
        // state (roadmap.md M5: "Schutz vor Parameter-Sprüngen und Race Conditions").
        private val mutex = Mutex()

        override suspend fun attach(session: AudioSession): Boolean =
            mutex.withLock {
                attachLocked(session)
            }

        private fun attachLocked(session: AudioSession): Boolean {
            detachInternal()

            _state.value = AudioEngineState.Attaching(session.sessionId)
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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val dpConfig =
                            DynamicsProcessing.Config
                                .Builder(
                                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                                    2, // 2 channels
                                    // PreEQ and MBC are left out of the requested config, not just
                                    // unconfigured: an unconfigured-but-active band still processes
                                    // audio with whatever default the OEM engine picks, which
                                    // violates the "every DSP stage needs defined bounds" principle
                                    // (roadmap.md §1). Only the limiter is actually configured below.
                                    false,
                                    0, // PreEQ: not requested
                                    false,
                                    0, // MBC: not requested
                                    false,
                                    0, // PostEQ: not requested
                                    true, // Limiter
                                ).build()
                        val dp = DynamicsProcessing(0, session.sessionId, dpConfig)
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
                        // Not requested in the DynamicsProcessing config above (no MBC
                        // configuration exists yet), so it isn't actually available.
                        hasMbc = false,
                    )

                isAttached.set(true)
                _state.value = AudioEngineState.Active(session.sessionId)
                applyInternal(_currentSettings.value)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach engine to session ${session.sessionId}", e)
                detachInternal()
                _state.value = AudioEngineState.Error("Failed to attach session ${session.sessionId}: ${e.localizedMessage}")
                return false
            }
        }

        override suspend fun detach() =
            mutex.withLock {
                detachInternal()
                _state.value = AudioEngineState.Detached
            }

        private fun detachInternal() {
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
            currentSessionId = null
        }

        override suspend fun apply(settings: ProcessingSettings): Boolean =
            mutex.withLock {
                _currentSettings.value = settings
                if (!isAttached.get()) return@withLock false
                applyInternal(settings)
            }

        private fun applyInternal(settings: ProcessingSettings): Boolean {
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
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error applying settings to AudioEngine", e)
                _state.value = AudioEngineState.LostControl("Error applying settings: ${e.localizedMessage}")
                return false
            }
        }
    }
