package com.hardbasseq.eq.ui.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.ui.theme.PlayerTextMutedColor
import com.hardbasseq.eq.ui.theme.PlayerTitleColor

// Where a track in a list stands: the one playing now, one that was already played in
// this session, or not played yet.
enum class TrackPlayState { CURRENT, PLAYED, NEW }

fun trackPlayState(
    trackId: Long,
    currentTrackId: Long?,
    playedIds: Set<Long>,
): TrackPlayState =
    when {
        trackId == currentTrackId -> TrackPlayState.CURRENT
        trackId in playedIds -> TrackPlayState.PLAYED
        else -> TrackPlayState.NEW
    }

// The track playing now glows: a softly pulsing lilac frame and tint around the row.
@Composable
fun Modifier.glowWhenCurrent(state: TrackPlayState): Modifier {
    if (state != TrackPlayState.CURRENT) return this
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.10f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(1_100), RepeatMode.Reverse),
        label = "glowAlpha",
    )
    val shape = RoundedCornerShape(8.dp)
    return this
        .background(PlayerTitleColor.copy(alpha = pulse), shape)
        .border(1.5.dp, PlayerTitleColor, shape)
}

// Small marker in front of a row: equalizer bars for the current track, a check for a
// played one, nothing for a new one.
@Composable
fun TrackStateIcon(state: TrackPlayState) {
    when (state) {
        TrackPlayState.CURRENT ->
            Icon(
                Icons.Default.GraphicEq,
                contentDescription = stringResource(R.string.track_state_current),
                tint = PlayerTitleColor,
                modifier = Modifier.size(20.dp),
            )

        TrackPlayState.PLAYED ->
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.track_state_played),
                tint = PlayerTextMutedColor,
                modifier = Modifier.size(18.dp).alpha(0.8f),
            )

        TrackPlayState.NEW -> Unit
    }
}

// Played tracks are dimmed so unplayed ones stand out.
fun TrackPlayState.textAlpha(): Float = if (this == TrackPlayState.PLAYED) 0.6f else 1f
