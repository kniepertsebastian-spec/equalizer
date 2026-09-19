package com.hardbasseq.eq.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AndroidAudioEngine"

@Singleton
class AndroidAudioEngine @Inject constructor() : AudioEngine {

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

    override suspend fun attach(session: AudioSession): Boolean {
        detachInternal()

        _state.value = AudioEngineState.Attaching(session.sessionId)
        currentSessionId = session.sessionId

        try {
            val eq = Equalizer(0, session.sessionId)
            val numBands = eq.numberOfBands.toInt()
            val minGainMb = eq.bandLevelRange[0]
            val maxGainMb = eq.bandLevelRange[1]

            val bandCaps = (0 until numBands).map { bandIdx ->
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
            var dpMbcSupported = false
            var dpInputGainSupported = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val dpConfig = DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        2, // 2 channels
                        true, 1, // PreEQ: 1 band
                        true, 1, // MBC: 1 band
                        true, 1, // PostEQ: 1 band
                        true // Limiter
                    ).build()
                    val dp = DynamicsProcessing(0, session.sessionId, dpConfig)
                    dynamicsProcessing = dp
                    dpSupported = true
                    dpLimiterSupported = true
                    dpMbcSupported = true
                    dpInputGainSupported = true
                } catch (e: Exception) {
                    Log.w(TAG, "DynamicsProcessing not supported on session ${session.sessionId}", e)
                }
            }

            _capabilities.value = AudioCapabilities(
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

    override suspend fun detach() {
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

    override suspend fun apply(settings: ProcessingSettings): Boolean {
        _currentSettings.value = settings
        if (!isAttached.get()) return false
        return applyInternal(settings)
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
                    if (settings.limiterEnabled) {
                        val safeThresholdDb = settings.limiterThresholdDb.coerceAtMost(0f)
                        val limiter = DynamicsProcessing.Limiter(
                            true,
                            true,
                            0, // channel 0
                            1f, // attack ms
                            50f, // release ms
                            10f, // ratio
                            safeThresholdDb, // threshold
                            0f // postGain
                        )
                        dp.setLimiterAllChannelsTo(limiter)
                    }
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
