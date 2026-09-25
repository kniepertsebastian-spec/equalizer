package com.hardbasseq.eq.audio

import kotlinx.coroutines.flow.StateFlow

interface AudioEngine {
    val capabilities: StateFlow<AudioCapabilities>
    val state: StateFlow<AudioEngineState>
    val currentSettings: StateFlow<ProcessingSettings>

    suspend fun attach(session: AudioSession): Boolean

    suspend fun detach()

    suspend fun apply(settings: ProcessingSettings): Boolean

    // Called when the foreground service is torn down - not part of everyday
    // operation, only process/service end (docs/STATE_MACHINE.md §3).
    fun markDetached()

    // Manually resets the retry budget and attempts the last known session
    // again, starting from Attaching. The UI's "Erneut versuchen" button after
    // Error uses this (roadmap-2026.md M1: "Fehler: konkrete nächste Handlung
    // anbieten"). No-op (returns false) if there's no session to retry.
    suspend fun retry(): Boolean
}
