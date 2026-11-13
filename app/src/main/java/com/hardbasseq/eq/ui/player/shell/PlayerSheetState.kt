package com.hardbasseq.eq.ui.player.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

// How far "Now playing" is pulled up over the mini player (0 = collapsed, 1 = full screen), and
// which sheet is open on top of it. Pure UI state: what plays comes from NowPlayingState only
// (docs/PLAYER_REDESIGN.md, section 4). Survives rotation; a half-open drag is saved as the
// side it would settle on.
@Stable
class PlayerSheetState(
    expanded: Boolean = false,
    queueOpen: Boolean = false,
    soundOpen: Boolean = false,
) {
    val fraction = Animatable(if (expanded) 1f else 0f)

    var queueOpen by mutableStateOf(queueOpen)
    var soundOpen by mutableStateOf(soundOpen)

    /** Something of "Now playing" is on screen. */
    val isVisible: Boolean get() = fraction.value > 0f

    suspend fun expand() = fraction.animateTo(1f, SETTLE_SPEC)

    suspend fun collapse() = fraction.animateTo(0f, SETTLE_SPEC)

    /** Follows a finger: [delta] as a share of the drag range, positive pulls up. */
    suspend fun dragBy(delta: Float) = fraction.snapTo((fraction.value + delta).coerceIn(0f, 1f))

    /** The finger let go at [velocity] (share of the range per second, positive = up). */
    suspend fun settle(velocity: Float) {
        if (SheetSettle.target(fraction.value, velocity) == 1f) expand() else collapse()
    }

    companion object {
        private val SETTLE_SPEC = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

        val Saver: Saver<PlayerSheetState, List<Boolean>> =
            Saver(
                save = { listOf(it.fraction.targetValue >= 0.5f, it.queueOpen, it.soundOpen) },
                restore = { PlayerSheetState(expanded = it[0], queueOpen = it[1], soundOpen = it[2]) },
            )
    }
}

@Composable
fun rememberPlayerSheetState(): PlayerSheetState = rememberSaveable(saver = PlayerSheetState.Saver) { PlayerSheetState() }

// Where a released drag goes: a clear flick decides by its direction, otherwise the nearer end.
object SheetSettle {
    /** Flicks faster than this (share of the range per second) win over the position. */
    const val FLICK_VELOCITY = 1.2f

    fun target(
        position: Float,
        velocity: Float,
    ): Float =
        when {
            velocity >= FLICK_VELOCITY -> 1f
            velocity <= -FLICK_VELOCITY -> 0f
            position >= 0.5f -> 1f
            else -> 0f
        }
}
