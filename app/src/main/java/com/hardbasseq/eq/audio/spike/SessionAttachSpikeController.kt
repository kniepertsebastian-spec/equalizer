package com.hardbasseq.eq.audio.spike

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SessionAttachSpikeResult(
    val audioSessionId: Int,
    val equalizer: EqualizerCapabilitySnapshot?,
    val equalizerError: String?,
    val dynamicsProcessingStages: List<DynamicsProcessingStageResult>,
)

data class ControlIntentTestResult(
    val audioSessionId: Int,
    val receivedEvents: List<ControlSessionBroadcastEvent>,
)

/**
 * Owns the single test-tone session and its related probes used by the M0
 * session-attach spike UI. All start/stop/probe operations are serialized
 * through a [Mutex] so they can never race each other (roadmap §7:
 * "Erstellen, Anwenden und Freigeben seriell über einen Mutex ... Bei
 * Session-Wechsel alte Effekte immer in finally freigeben").
 */
class SessionAttachSpikeController(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val player = TestTonePlayer(audioManager)
    private val controlIntentSpike = ControlSessionIntentSpike(appContext)
    private val mutex = Mutex()

    suspend fun run(): SessionAttachSpikeResult = mutex.withLock {
        player.start()
        val sessionId = player.audioSessionId

        val equalizerResult = runCatching { EqualizerSpike.readCapabilities(sessionId) }
        val dpStages = DynamicsProcessingSpike.probeStages(sessionId)

        SessionAttachSpikeResult(
            audioSessionId = sessionId,
            equalizer = equalizerResult.getOrNull(),
            equalizerError = equalizerResult.exceptionOrNull()?.let { it.message ?: it.toString() },
            dynamicsProcessingStages = dpStages,
        )
    }

    /**
     * Sends our own OPEN/CLOSE control-session broadcasts and reports what
     * our own receiver observed. See [ControlSessionIntentSpike] for what
     * this does and does not prove.
     */
    suspend fun testControlIntents(): ControlIntentTestResult = mutex.withLock {
        val sessionId = if (player.isPlaying) player.audioSessionId else audioManager.generateAudioSessionId()
        val received = mutableListOf<ControlSessionBroadcastEvent>()
        controlIntentSpike.startListening { received += it }
        try {
            controlIntentSpike.sendTestOpenBroadcast(sessionId)
            controlIntentSpike.sendTestCloseBroadcast(sessionId)
            delay(300)
        } finally {
            controlIntentSpike.stopListening()
        }
        ControlIntentTestResult(audioSessionId = sessionId, receivedEvents = received.toList())
    }

    /** See [SessionZeroExperiment] - never enables an effect, read-only probe. */
    suspend fun probeSessionZero(): SessionZeroProbeResult = mutex.withLock {
        SessionZeroExperiment.probe()
    }

    suspend fun stop() = mutex.withLock {
        controlIntentSpike.stopListening()
        player.stop()
    }
}
