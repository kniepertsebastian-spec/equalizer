package com.hardbasseq.eq.ui.player.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hardbasseq.eq.R
import com.hardbasseq.eq.playlist.PlayerFormat

// Dragging shows where the thumb is without seeking on every frame; the seek
// happens once, when the finger lifts.
@Composable
internal fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: PlayerFormat.progress(positionMs, durationMs)
    Column(modifier = Modifier.fillMaxWidth()) {
        val seekDescription = stringResource(R.string.seek_description)
        Slider(
            modifier = Modifier.semantics { contentDescription = seekDescription },
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * durationMs).toLong()) }
                dragFraction = null
            },
            enabled = enabled && durationMs > 0,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = PlayerFormat.duration(dragFraction?.let { (it * durationMs).toLong() } ?: positionMs),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(text = PlayerFormat.duration(durationMs), style = MaterialTheme.typography.labelSmall)
        }
    }
}
