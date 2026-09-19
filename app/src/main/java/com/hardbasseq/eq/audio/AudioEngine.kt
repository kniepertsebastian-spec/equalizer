package com.hardbasseq.eq.audio

import kotlinx.coroutines.flow.StateFlow

interface AudioEngine {
    val capabilities: StateFlow<AudioCapabilities>
    val state: StateFlow<AudioEngineState>
    val currentSettings: StateFlow<ProcessingSettings>

    suspend fun attach(session: AudioSession): Boolean
    suspend fun detach()
    suspend fun apply(settings: ProcessingSettings): Boolean
}
