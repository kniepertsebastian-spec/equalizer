package com.hardbasseq.eq.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueNavigationTest {
    @Test
    fun `next advances and stops at the end`() {
        assertEquals(1, QueueNavigation.next(0, 3))
        assertEquals(2, QueueNavigation.next(1, 3))
        assertNull(QueueNavigation.next(2, 3))
        assertNull(QueueNavigation.next(0, 0))
    }

    @Test
    fun `previous goes back only early in a track`() {
        assertEquals(0, QueueNavigation.previous(1, positionMs = 1_000L))
        assertNull(QueueNavigation.previous(1, positionMs = 10_000L))
        assertNull(QueueNavigation.previous(0, positionMs = 0L))
    }

    @Test
    fun `validates indexes`() {
        assertTrue(QueueNavigation.isValid(0, 1))
        assertFalse(QueueNavigation.isValid(1, 1))
        assertFalse(QueueNavigation.isValid(-1, 3))
        assertFalse(QueueNavigation.isValid(0, 0))
    }
}
