package com.hardbasseq.eq.audio

// Target state set from docs/STATE_MACHINE.md (M1 spec). Suspended was removed: it was
// unreachable dead code (never produced by AndroidAudioEngine) and isn't part of the state
// set roadmap-2026.md's M1 section calls for.
sealed interface AudioEngineState {
    data object Detached : AudioEngineState

    // Service is listening for sessions but none is currently attached. This is what earlier
    // code called Detached while the service was actually still listening - see
    // docs/STATE_MACHINE.md section 1 for the gap analysis.
    data object Listening : AudioEngineState

    data class Attaching(
        val sessionId: Int,
    ) : AudioEngineState

    data class Active(
        val sessionId: Int,
    ) : AudioEngineState

    // Short-lived: apply() failed on a previously active session. Immediately triggers
    // scheduling the first retry, i.e. a transition into Retrying.
    data class LostControl(
        val sessionId: Int,
        val reason: String,
    ) : AudioEngineState

    // Bounded, backed-off re-attach in progress. See AndroidAudioEngine's RETRY_DELAYS_MS.
    data class Retrying(
        val sessionId: Int,
        val attempt: Int,
        val nextRetryAtMillis: Long,
    ) : AudioEngineState

    data class Unsupported(
        val reason: String,
    ) : AudioEngineState

    data class Error(
        val message: String,
    ) : AudioEngineState
}
