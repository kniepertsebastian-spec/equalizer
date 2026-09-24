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
private const val MBC_BAND_COUNT = 3

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
                        dp.setInputGainAllChannelsTo(settings.inputGainDb.coerceIn(-15f, 0f))

                        if (capabilities.value.hasMbc) {
                            val mbcRatio = if (settings.mbcEnabled) settings.mbcRatio.coerceIn(1f, 6f) else 1f
                            val mbcThresholdDb =
                                if (settings.mbcEnabled) settings.mbcThresholdDb.coerceIn(-30f, 0f) else 0f
                            // Standard compressor makeup-gain heuristic (half the average gain
                            // reduction the ratio/threshold combination implies), capped
                            // conservatively. Without this, every preset ends up net quieter
                            // than flat instead of punchier: the Limiter below (unchanged, hard
                            // 10:1 ceiling) still protects the final output, so this only
                            // restores loudness the compression itself removed.
                            val mbcMakeupGainDb =
                                if (mbcRatio > 1f) {
                                    ((-mbcThresholdDb) * (1f - 1f / mbcRatio) * 0.5f).coerceIn(0f, 4f)
                                } else {
                                    0f
                                }
                            val mbcBands =
                                listOf(
                                    MbcBandSettings(120f, 15f, 180f),
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
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error applying settings to AudioEngine", e)
                _state.value = AudioEngineState.LostControl("Error applying settings: ${e.localizedMessage}")
                return false
            }
        }
    }

private data class MbcBandSettings(
    val cutoffFrequencyHz: Float,
    val attackMs: Float,
    val releaseMs: Float,
)
