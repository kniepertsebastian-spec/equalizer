package com.auracurve.app.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueuePositionsTest {
    private val queue = listOf<Long?>(10, 11, 12, 13, 14)

    @Test
    fun trackStillAtItsPosition_keepsThePosition() {
        assertEquals(3, QueuePositions.resolve(queue, index = 3, expectedTrackId = 13))
    }

    @Test
    fun entryBeforeItWasRemoved_findsTheTrackOnePositionUp() {
        // The screen saw [10, 11, 12, 13, 14] and removed 11 and then 13 (at 3) right after;
        // by the time the second command arrives, 13 is at 2.
        val afterFirstRemoval = listOf<Long?>(10, 12, 13, 14)

        assertEquals(2, QueuePositions.resolve(afterFirstRemoval, index = 3, expectedTrackId = 13))
    }

    @Test
    fun trackGone_isNotReplacedByItsNeighbour() {
        assertNull(QueuePositions.resolve(listOf<Long?>(10, 11, 12), index = 1, expectedTrackId = 99))
    }

    @Test
    fun positionOutsideTheQueue_stillFindsTheTrack() {
        assertEquals(1, QueuePositions.resolve(listOf<Long?>(10, 11), index = 5, expectedTrackId = 11))
    }

    @Test
    fun sameTrackTwice_takesTheNearestOne() {
        val twice = listOf<Long?>(7, 10, 11, 7, 12)

        assertEquals(3, QueuePositions.resolve(twice, index = 4, expectedTrackId = 7))
        assertEquals(0, QueuePositions.resolve(twice, index = 1, expectedTrackId = 7))
    }

    @Test
    fun sameTrackEquallyNearOnBothSides_takesTheOneBefore() {
        assertEquals(0, QueuePositions.resolve(listOf<Long?>(7, 10, 7), index = 1, expectedTrackId = 7))
    }

    @Test
    fun noTrackNamed_takesThePositionAsItIs() {
        assertEquals(2, QueuePositions.resolve(queue, index = 2, expectedTrackId = QueuePositions.NO_TRACK))
        assertNull(QueuePositions.resolve(queue, index = 5, expectedTrackId = QueuePositions.NO_TRACK))
        assertNull(QueuePositions.resolve(queue, index = -1, expectedTrackId = QueuePositions.NO_TRACK))
    }

    @Test
    fun foreignEntries_areNeverMatched() {
        assertNull(QueuePositions.resolve(listOf(null, null), index = 0, expectedTrackId = 5))
    }

    @Test
    fun emptyQueue_resolvesNothing() {
        assertNull(QueuePositions.resolve(emptyList(), index = 0, expectedTrackId = 5))
    }
}
