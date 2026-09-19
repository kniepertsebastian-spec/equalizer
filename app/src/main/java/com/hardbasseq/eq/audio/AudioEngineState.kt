package com.hardbasseq.eq.audio

sealed interface AudioEngineState {
    data object Detached : AudioEngineState
    data class Attaching(val sessionId: Int) : AudioEngineState
    data class Active(val sessionId: Int) : AudioEngineState
    data class Suspended(val reason: String) : AudioEngineState
    data class LostControl(val reason: String) : AudioEngineState
    data class Unsupported(val reason: String) : AudioEngineState
    data class Error(val message: String) : AudioEngineState
}
