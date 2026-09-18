package com.hardbasseq.eq.audio.spike

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SessionAttachSpikeResult(
    val audioSessionId: Int,
    val equalizer: EqualizerCapabilitySnapshot?,
    val equalizerError: String?,
    val dynamicsProcessingStages: List<DynamicsProcessingStageResult>,
)

/**
 * Owns the single test-tone session used by the M0 session-attach spike UI.
 * All start/stop/probe operations are serialized through a [Mutex] so they
 * can never race each other (roadmap §7: "Erstellen, Anwenden und Freigeben
 * seriell über einen Mutex ... Bei Session-Wechsel alte Effekte immer in
 * finally freigeben").
 */
class SessionAttachSpikeController(context: Context) {

    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val player = TestTonePlayer(audioManager)
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

    suspend fun stop() = mutex.withLock {
        player.stop()
    }
}
