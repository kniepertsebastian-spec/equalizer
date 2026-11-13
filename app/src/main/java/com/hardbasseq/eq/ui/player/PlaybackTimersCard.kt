package com.hardbasseq.eq.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.playback.SleepTimerSetting
import com.hardbasseq.eq.playback.SleepTimerStatus
import com.hardbasseq.eq.ui.theme.SonicRule
import com.hardbasseq.eq.ui.theme.SonicSteel
import com.hardbasseq.eq.ui.theme.spacing

/** Seconds offered for the fade between tracks; 0 = off. */
val TrackFadeChoicesSeconds = listOf(0, 2, 4, 6, 8, 12)

/** m:ss, or h:mm:ss from an hour on - what the countdown shows. */
fun formatCountdown(remainingMs: Long): String {
    val totalSeconds = ((remainingMs + 999L) / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

// Sleep timer and fade between the tracks of the built-in player.
@Composable
fun PlaybackTimersCard(
    sleepTimer: SleepTimerStatus?,
    trackFadeMs: Long,
    onStartSleepTimer: (SleepTimerSetting) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onTrackFadeSeconds: (Int) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, SonicRule),
    ) {
        Column(modifier = Modifier.padding(spacing.medium), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            Text(
                text = stringResource(R.string.sleep_timer_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (sleepTimer != null) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text =
                            if (sleepTimer.endOfTrack) {
                                stringResource(R.string.sleep_timer_running_end_of_track)
                            } else {
                                stringResource(R.string.sleep_timer_running, formatCountdown(sleepTimer.remainingMs ?: 0L))
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancelSleepTimer) { Text(stringResource(R.string.sleep_timer_cancel)) }
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                SleepTimerSetting.PRESET_MINUTES.forEach { minutes ->
                    FilterChip(
                        selected = false,
                        onClick = { onStartSleepTimer(SleepTimerSetting.AfterMinutes(minutes)) },
                        label = { Text(stringResource(R.string.sleep_timer_minutes, minutes)) },
                    )
                }
                FilterChip(
                    selected = sleepTimer?.endOfTrack == true,
                    onClick = { onStartSleepTimer(SleepTimerSetting.EndOfTrack) },
                    label = { Text(stringResource(R.string.sleep_timer_end_of_track)) },
                )
            }
            Text(
                text = stringResource(R.string.sleep_timer_desc),
                style = MaterialTheme.typography.bodySmall,
                color = SonicSteel,
            )

            Text(
                text = stringResource(R.string.track_fade_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = spacing.small),
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                TrackFadeChoicesSeconds.forEach { seconds ->
                    FilterChip(
                        selected = trackFadeMs == seconds * 1000L,
                        onClick = { onTrackFadeSeconds(seconds) },
                        label = {
                            Text(
                                if (seconds ==
                                    0
                                ) {
                                    stringResource(R.string.track_fade_off)
                                } else {
                                    stringResource(R.string.track_fade_seconds, seconds)
                                },
                            )
                        },
                    )
                }
            }
            Text(
                text = stringResource(R.string.track_fade_desc),
                style = MaterialTheme.typography.bodySmall,
                color = SonicSteel,
            )
        }
    }
}
