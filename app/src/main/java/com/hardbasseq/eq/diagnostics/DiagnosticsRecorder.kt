package com.hardbasseq.eq.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class DiagnosticsEvent(
    val timestampMillis: Long,
    val message: String,
)

/**
 * Bounded, timestamped history of session/route/state-transition events, for
 * the diagnostic report (roadmap-2026.md M0: "Diagnosereport um ...
 * Session-Ereignisse ... erweitern"). Deliberately just a rolling window,
 * not a persisted log - this is for "what just happened" during a live
 * troubleshooting session, not long-term telemetry (see
 * docs/ARCHITECTURE.md's logging rules: no persisted, identifying data).
 */
interface DiagnosticsRecorder {
    val events: StateFlow<List<DiagnosticsEvent>>

    fun record(message: String)
}

@Singleton
class InMemoryDiagnosticsRecorder
    @Inject
    constructor() : DiagnosticsRecorder {
        private val _events = MutableStateFlow<List<DiagnosticsEvent>>(emptyList())
        override val events: StateFlow<List<DiagnosticsEvent>> = _events.asStateFlow()

        override fun record(message: String) {
            _events.value = (_events.value + DiagnosticsEvent(System.currentTimeMillis(), message)).takeLast(MAX_EVENTS)
        }

        private companion object {
            const val MAX_EVENTS = 50
        }
    }
