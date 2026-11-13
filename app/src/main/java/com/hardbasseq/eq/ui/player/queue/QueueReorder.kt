package com.hardbasseq.eq.ui.player.queue

import androidx.compose.foundation.lazy.LazyListItemInfo

// Drag-to-reorder for the queue sheet. While a row is dragged, its center is compared with the
// rows on screen; once it is over another row, the two swap places. The correction keeps the
// dragged row under the finger after the swap (it now starts where the other row started).
object QueueReorder {
    data class Move(
        val toIndex: Int,
        val offsetCorrection: Float,
    )

    /** [draggedKey]'s row moved by [dragOffset] px: where it belongs now, or null to stay. */
    fun step(
        visible: List<LazyListItemInfo>,
        draggedKey: Any,
        dragOffset: Float,
    ): Move? {
        val dragged = visible.firstOrNull { it.key == draggedKey } ?: return null
        val center = dragged.offset + dragOffset + dragged.size / 2f
        val target =
            visible.firstOrNull { it.key != draggedKey && center >= it.offset && center < it.offset + it.size }
                ?: return null
        return Move(toIndex = target.index, offsetCorrection = (dragged.offset - target.offset).toFloat())
    }
}
