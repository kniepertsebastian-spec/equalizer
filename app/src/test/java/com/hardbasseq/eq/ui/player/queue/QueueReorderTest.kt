package com.hardbasseq.eq.ui.player.queue

import androidx.compose.foundation.lazy.LazyListItemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueReorderTest {
    private class Item(
        override val index: Int,
        override val key: Any,
        override val offset: Int,
        override val size: Int,
    ) : LazyListItemInfo

    // Three rows of 100 px, keys 10/11/12 at positions 0/1/2.
    private val rows = List(3) { Item(index = it, key = 10 + it, offset = it * 100, size = 100) }

    @Test
    fun smallDrag_staysInPlace() {
        assertNull(QueueReorder.step(rows, draggedKey = 10, dragOffset = 40f))
    }

    @Test
    fun dragPastTheNextRowsTop_swapsAndKeepsTheRowUnderTheFinger() {
        val move = QueueReorder.step(rows, draggedKey = 10, dragOffset = 60f)

        assertEquals(1, move?.toIndex)
        // The row now starts at 100 px; 60 px of drag from 0 px means -40 px from there.
        assertEquals(-100f, move!!.offsetCorrection, 0f)
    }

    @Test
    fun dragUp_swapsWithTheRowAbove() {
        val move = QueueReorder.step(rows, draggedKey = 12, dragOffset = -60f)

        assertEquals(1, move?.toIndex)
        assertEquals(100f, move!!.offsetCorrection, 0f)
    }

    @Test
    fun unknownRow_isIgnored() {
        assertNull(QueueReorder.step(rows, draggedKey = 99, dragOffset = 500f))
    }
}
