package com.hardbasseq.eq.ui.player.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

// Share of the row's width a swipe has to cover to count as "away".
private const val SWIPE_AWAY_FRACTION = 0.4f

// A row that can be swiped sideways out of the list. Follows the finger; past 40 % of
// its width it counts as dismissed, otherwise it snaps back. The close button on the row
// does the same without a gesture.
@Composable
fun SwipeAwayRow(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .onSizeChanged { widthPx = it.width.toFloat() },
    ) {
        Box(
            modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(offsetX) > widthPx * SWIPE_AWAY_FRACTION) currentOnDismiss() else offsetX = 0f
                            },
                            onDragCancel = { offsetX = 0f },
                        ) { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount
                        }
                    },
        ) {
            content()
        }
    }
}
