package com.auracurve.app.ui.player.queue

import androidx.compose.foundation.lazy.LazyListItemInfo

// Drag-to-reorder for the queue sheet. While a row is dragged, its center is compared with the
// rows on screen; once it is over another row, the two swap places. The correction keeps the
// dragged row under the finger after the swap (it now starts where the other row started).
object QueueReorder {
    data class Move(
        val toIndex: Int,
        val offsetCorrection: Float,
    )

    /**
     * [draggedKey]'s row moved by [dragOffset] px: where it belongs now, or null to stay.
     *
     * [draggedIndex] is where the row is in the list now. The rows on screen are only measured
     * once per frame, the finger reports more often: right after a swap [visible] still shows the
     * row at its old place. Comparing against that would swap again (or back) with a row the
     * finger is not over, so the step waits until the layout has caught up.
     */
    fun step(
        visible: List<LazyListItemInfo>,
        draggedKey: Any,
        draggedIndex: Int,
        dragOffset: Float,
    ): Move? {
        val dragged = visible.firstOrNull { it.key == draggedKey } ?: return null
        if (dragged.index != draggedIndex) return null
        val center = dragged.offset + dragOffset + dragged.size / 2f
        val target =
            visible.firstOrNull { it.key != draggedKey && center >= it.offset && center < it.offset + it.size }
                ?: return null
        return Move(toIndex = target.index, offsetCorrection = (dragged.offset - target.offset).toFloat())
    }
}
