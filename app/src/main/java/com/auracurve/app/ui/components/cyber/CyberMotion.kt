package com.auracurve.app.ui.components.cyber

import android.provider.Settings
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

// When the Cyber visuals may move (Design/DESIGN_SPEC.md §6): only while their screen is
// composed, the app is in the foreground and the system has not turned animations off. Off
// screen, in the background or with the display off nothing runs.

/** True while the screen's lifecycle is at least RESUMED (in front and not paused). */
@Composable
internal fun rememberIsResumed(): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

/** True when the user switched animations off (developer options or accessibility). */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Seconds of motion for a visual, ticking at most [fps] times a second while [running], the
 * screen is resumed and motion is not reduced; it stops where it is otherwise and resumes from
 * there. Read the value only in the draw phase, so a tick redraws and never recomposes.
 *
 * The loop runs on infinite-animation frames: a Compose test stops it instead of waiting for it,
 * so screenshots show the still frame at 0 s.
 */
@Composable
internal fun rememberMotionClock(
    running: Boolean,
    fps: Int = 30,
): FloatState {
    val clock = remember { mutableFloatStateOf(0f) }
    val active = running && rememberIsResumed() && !rememberReducedMotion()
    LaunchedEffect(active, fps) {
        if (!active) return@LaunchedEffect
        val frameNanos = 1_000_000_000L / fps.coerceIn(1, 120)
        val origin = clock.floatValue
        var start = -1L
        var last = -frameNanos
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (start < 0L) {
                    start = now
                    last = now - frameNanos
                }
                if (now - last >= frameNanos) {
                    last = now
                    clock.floatValue = origin + (now - start) / 1_000_000_000f
                }
            }
        }
    }
    return clock
}
