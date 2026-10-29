package com.hardbasseq.eq.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.ui.theme.SonicAmber
import com.hardbasseq.eq.ui.theme.SonicGraphiteRaised
import com.hardbasseq.eq.ui.theme.SonicSteel

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

// The track playing now is marked, still: a raised background and an amber outline. No
// pulse - a list that keeps moving competes with everything else on the screen.
@Composable
fun Modifier.markWhenCurrent(state: TrackPlayState): Modifier {
    if (state != TrackPlayState.CURRENT) return this
    val shape = MaterialTheme.shapes.small
    return this
        .background(SonicGraphiteRaised, shape)
        .border(1.5.dp, SonicAmber, shape)
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
                tint = SonicAmber,
                modifier = Modifier.size(20.dp),
            )

        TrackPlayState.PLAYED ->
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.track_state_played),
                tint = SonicSteel,
                modifier = Modifier.size(18.dp).alpha(0.8f),
            )

        TrackPlayState.NEW -> Unit
    }
}

// Played tracks are dimmed so unplayed ones stand out.
fun TrackPlayState.textAlpha(): Float = if (this == TrackPlayState.PLAYED) 0.6f else 1f
