package com.auracurve.app.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.auracurve.app.audio.AudioEngine
import com.auracurve.app.audio.AudioEngineState
import com.auracurve.app.dsp.SpectrumBands
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pixel-Car module 11, Z1: the spectrum of what plays, as `spectrum` events for the web remote.
 *
 * Only while a remote asks for it: `POST /api/v0/visualizer` gives a lease of [LEASE_MS] that the
 * open page renews; when it lapses the Visualizer is released and nothing is sent. Android's
 * Visualizer needs RECORD_AUDIO although it never uses the microphone (it could tap the output).
 */
@Singleton
class SpectrumHub
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val engine: AudioEngine,
    ) {
        private val leaseUntil = MutableStateFlow(0L)
        private val active = MutableStateFlow(false)

        fun hasPermission(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        /** `POST /api/v0/visualizer {"enabled": …}`; 409 NO_MIC_PERMISSION without the permission. */
        fun request(
            enabled: Boolean,
            nowMs: Long = System.currentTimeMillis(),
        ): JsonObject {
            if (enabled && !hasPermission()) {
                throw ApiError(HttpStatusCode.Conflict, "NO_MIC_PERMISSION", "Am Pixel fehlt die Mikrofonberechtigung für den Visualizer")
            }
            leaseUntil.value = if (enabled) nowMs + LEASE_MS else 0L
            active.value = enabled
            return buildJsonObject {
                put("active", enabled)
                put("leaseS", LEASE_MS / 1000)
            }
        }

        /** `spectrum` events while a lease runs; nothing otherwise. */
        suspend fun run(emit: (RemoteEvent) -> Unit): Unit =
            coroutineScope {
                // The lease runs out even when no frame comes (silence repeats the same frame).
                launch {
                    while (true) {
                        if (active.value && System.currentTimeMillis() > leaseUntil.value) active.value = false
                        delay(WATCH_MS)
                    }
                }
                active.collectLatest { on ->
                    if (!on) return@collectLatest
                    frames().collect { bands -> emit(RemoteEvent("spectrum", json(System.currentTimeMillis(), bands))) }
                }
            }

        /** The session the EQ is attached to (0, the output mix, otherwise); started again if it ends. */
        private fun frames(): Flow<IntArray> =
            flow {
                while (true) {
                    capture(sessionId()).collect { emit(it) }
                    delay(RETRY_MS)
                }
            }

        private fun sessionId(): Int =
            when (val s = engine.state.value) {
                is AudioEngineState.Active -> s.sessionId
                else -> 0
            }

        private fun capture(sessionId: Int): Flow<IntArray> {
            if (!hasPermission()) return emptyFlow()
            return callbackFlow {
                val visualizer =
                    try {
                        Visualizer(sessionId).apply {
                            captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(CAPTURE_SIZE)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Visualizer on session $sessionId unavailable: ${e.javaClass.simpleName}")
                        close()
                        return@callbackFlow
                    }
                val rate = Visualizer.getMaxCaptureRate().coerceAtMost(MAX_RATE_MILLIHZ)
                visualizer.setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int,
                        ) = Unit

                        override fun onFftDataCapture(
                            v: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int,
                        ) {
                            if (fft != null) trySend(SpectrumBands.fromFft(fft, samplingRate / 1000))
                        }
                    },
                    rate,
                    false,
                    true,
                )
                visualizer.enabled = true
                awaitClose {
                    runCatching {
                        visualizer.enabled = false
                        visualizer.release()
                    }
                }
            }.distinctUntilChanged { a, b -> a.contentEquals(b) }
        }

        companion object {
            const val LEASE_MS = 10_000L
            private const val RETRY_MS = 2_000L
            private const val WATCH_MS = 1_000L
            private const val CAPTURE_SIZE = 1024

            /** 20 frames a second at most (about 1 KB/s with 32 bars). */
            private const val MAX_RATE_MILLIHZ = 20_000
            private const val TAG = "SpectrumHub"

            fun json(
                timeMs: Long,
                bands: IntArray,
            ): JsonObject =
                buildJsonObject {
                    put("t", timeMs)
                    putJsonArray("bands") { bands.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                }
        }
    }
