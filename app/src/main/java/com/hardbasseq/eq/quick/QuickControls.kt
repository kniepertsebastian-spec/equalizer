package com.hardbasseq.eq.quick

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** What the quick-settings tiles and the widget can ask for (roadmap 06, P5-1). */
enum class QuickCommand {
    TOGGLE_MASTER,
    NEXT_PRESET,
}

/** What the tiles and the widget show. */
data class QuickState(
    val masterEnabled: Boolean,
    val presetName: String,
)

/**
 * The bridge between the system surfaces (tiles, widget) and the equalizer state, which lives in
 * `MainViewModel`: the view model publishes [state] and consumes [commands]. While no view model is
 * alive ([hasConsumer] is false) a surface has to open the app with the command instead
 * ([QuickCommandIntents]) - nothing else can compose the curve and drive the engine.
 */
@Singleton
class QuickControls
    @Inject
    constructor() {
        private val _state = MutableStateFlow<QuickState?>(null)

        /** Null until the first publish in this process. */
        val state: StateFlow<QuickState?> = _state.asStateFlow()

        private val channel = Channel<QuickCommand>(Channel.UNLIMITED)
        val commands: Flow<QuickCommand> = channel.receiveAsFlow()

        private val consumers = AtomicInteger(0)
        val hasConsumer: Boolean get() = consumers.get() > 0

        fun consumerAttached() {
            consumers.incrementAndGet()
            refreshSurfaces()
        }

        fun consumerDetached() {
            consumers.updateAndGet { (it - 1).coerceAtLeast(0) }
            if (!hasConsumer) _state.value = null
            refreshSurfaces()
        }

        /** Returns whether a view model is there to act on it; if not nothing is queued. */
        fun send(command: QuickCommand): Boolean {
            if (!hasConsumer) return false
            return channel.trySend(command).isSuccess
        }

        /** Called by the view model whenever what the surfaces show may have changed. */
        fun publish(state: QuickState) {
            if (_state.value == state) return
            _state.value = state
            refreshSurfaces()
        }

        /** Set once by the application: redraws the tiles and the widget. Absent in unit tests. */
        @Volatile
        var onSurfacesChanged: (() -> Unit)? = null

        private fun refreshSurfaces() {
            onSurfacesChanged?.invoke()
        }
    }
