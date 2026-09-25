package com.hardbasseq.eq.audio

// roadmap-2026.md M1 / docs/STATE_MACHINE.md: bounded re-attach with backoff.
// Shared between AndroidAudioEngine (schedules retries against this) and the UI
// (renders "Versuch N/MAX_RETRY_ATTEMPTS").
const val MAX_RETRY_ATTEMPTS = 5

sealed interface AudioEngineState {
    // No active listening at all - only the brief cold-start window before the
    // foreground service starts listening, or after it's torn down. Not the
    // normal "no session yet" state - see Listening for that.
    data object Detached : AudioEngineState

    // Service is listening for sessions, but none is currently attached. What
    // the app spends most of its idle time in.
    data object Listening : AudioEngineState

    data class Attaching(
        val sessionId: Int,
    ) : AudioEngineState

    // Session attached AND the last apply() call succeeded - the only state the
    // UI may render as "Aktiv".
    data class Active(
        val sessionId: Int,
    ) : AudioEngineState

    // Short-lived transition state: apply() failed on a previously active
    // session. Immediately followed (same tick) by Retrying.
    data class LostControl(
        val sessionId: Int,
        val reason: String,
    ) : AudioEngineState

    // Bounded re-attach with backoff in progress for sessionId. attempt starts
    // at 1; nextRetryAtMillis is when the next attempt fires.
    data class Retrying(
        val sessionId: Int,
        val attempt: Int,
        val nextRetryAtMillis: Long,
    ) : AudioEngineState

    // Device/player doesn't provide a usable effect path for this session.
    // Never retried automatically - only a genuinely new session can leave
    // this state.
    data class Unsupported(
        val reason: String,
    ) : AudioEngineState

    // Retry budget exhausted, or a non-recoverable failure. Requires an
    // explicit next action (see EqualizerScreen's "Erneut versuchen").
    data class Error(
        val message: String,
    ) : AudioEngineState
}
