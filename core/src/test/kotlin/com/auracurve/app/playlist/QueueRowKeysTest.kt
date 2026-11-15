package com.auracurve.app.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class QueueRowKeysTest {
    @Test
    fun everyEntryGetsItsOwnKey_alsoTheSameTrackTwice() {
        val keys = QueueRowKeys().assign(listOf(1, 2, 1, 3))

        assertEquals(4, keys.toSet().size)
    }

    @Test
    fun sameQueueAgain_keepsTheKeys() {
        val rows = QueueRowKeys()
        val first = rows.assign(listOf(1, 2, 3))

        assertEquals(first, rows.assign(listOf(1, 2, 3)))
    }

    @Test
    fun removedEntry_theOthersKeepTheirKeys() {
        val rows = QueueRowKeys()
        val (a, _, c, d) = rows.assign(listOf(1, 2, 3, 4))

        assertEquals(listOf(a, c, d), rows.assign(listOf(1, 3, 4)))
    }

    @Test
    fun movedEntry_takesItsKeyAlong() {
        val rows = QueueRowKeys()
        val (a, b, c) = rows.assign(listOf(1, 2, 3))

        assertEquals(listOf(c, a, b), rows.assign(listOf(3, 1, 2)))
    }

    @Test
    fun addedEntry_getsAKeyNobodyHad() {
        val rows = QueueRowKeys()
        val before = rows.assign(listOf(1, 2))

        val after = rows.assign(listOf(1, 9, 2))

        assertEquals(before[0], after[0])
        assertEquals(before[1], after[2])
        assertFalse(after[1] in before)
    }

    @Test
    fun removedDuplicate_itsKeyDoesNotGoToTheOtherCopy() {
        val rows = QueueRowKeys()
        val (first, between, second) = rows.assign(listOf(7, 2, 7))

        // The user swiped the first copy of track 7 away.
        rows.forget(first)

        assertEquals(listOf(between, second), rows.assign(listOf(2, 7)))
    }

    @Test
    fun removedEntryThatComesBack_isANewRow() {
        val rows = QueueRowKeys()
        val (_, removed) = rows.assign(listOf(1, 2))
        rows.forget(removed)
        rows.assign(listOf(1))

        // Undo puts track 2 back: it must not come back with the swiped-away row's state.
        assertNotEquals(removed, rows.assign(listOf(1, 2))[1])
    }

    @Test
    fun newQueue_startsWithNewKeys() {
        val rows = QueueRowKeys()
        val old = rows.assign(listOf(1, 2))

        val new = rows.assign(listOf(5, 6))

        assertFalse(new.any { it in old })
    }
}
