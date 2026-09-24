package com.hardbasseq.eq.audio.spike

import android.content.Context
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which test signal [SessionAttachSpikeController.run] should play. SWEEP
 * covers the full audible range instead of one fixed pitch, so a broadband
 * EQ change (e.g. a dipped band) is audible as a dip in the sweep rather
 * than only at one frequency - roadmap-2026.md M0 sprint backlog item 5.
 */
enum class TestSignal { SINE, SWEEP }

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
@Singleton
class SessionAttachSpikeController
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val appContext = context.applicationContext
        private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        private val player = TestTonePlayer(audioManager)
        private val controlIntentSpike = ControlSessionIntentSpike(appContext)
        private val rootSessionZeroProbe = RootSessionZeroProbe(appContext)
        private val mutex = Mutex()

        suspend fun run(signal: TestSignal = TestSignal.SINE): SessionAttachSpikeResult =
            mutex.withLock {
                when (signal) {
                    TestSignal.SINE -> player.start()
                    TestSignal.SWEEP -> player.startSweep()
                }
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
         *
         * Actively waits (up to [timeoutMillis]) for both the OPEN and CLOSE
         * broadcasts to round-trip, rather than a fixed delay, since broadcast
         * dispatch latency is not guaranteed and a first attempt with a blind
         * 300ms delay reported zero events on a real device.
         */
        suspend fun testControlIntents(timeoutMillis: Long = 3000): ControlIntentTestResult =
            mutex.withLock {
                val sessionId = if (player.isPlaying) player.audioSessionId else audioManager.generateAudioSessionId()
                val events = Channel<ControlSessionBroadcastEvent>(capacity = Channel.UNLIMITED)
                val received = mutableListOf<ControlSessionBroadcastEvent>()

                controlIntentSpike.startListening { events.trySendBlocking(it) }
                try {
                    controlIntentSpike.sendTestOpenBroadcast(sessionId)
                    controlIntentSpike.sendTestCloseBroadcast(sessionId)
                    withTimeoutOrNull(timeoutMillis) {
                        while (received.size < 2) {
                            received += events.receive()
                        }
                    }
                } finally {
                    controlIntentSpike.stopListening()
                    events.close()
                }
                ControlIntentTestResult(audioSessionId = sessionId, receivedEvents = received.toList())
            }

        /** See [SessionZeroExperiment] - never enables an effect, read-only probe. */
        suspend fun probeSessionZero(): SessionZeroProbeResult =
            mutex.withLock {
                SessionZeroExperiment.probe()
            }

        /** Runs the read-only session-0 construction probe in a root process. */
        suspend fun probeRootSessionZero(): RootSessionZeroProbeResult = rootSessionZeroProbe.run()

        suspend fun stop() =
            mutex.withLock {
                controlIntentSpike.stopListening()
                player.stop()
            }
    }
