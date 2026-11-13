package com.hardbasseq.eq.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueOrderTest {
    @Test
    fun newEntries_followTheCurrentTrack_inTheOrderTheyWereAdded() {
        // Queue 0..5, track 2 plays, 3 and 4 were just added after it; ExoPlayer scattered them.
        val scattered = listOf(5, 4, 2, 0, 3, 1)

        assertEquals(listOf(5, 2, 3, 4, 0, 1), QueueOrder.playNext(scattered, currentIndex = 2, insertedFirstIndex = 3, insertedCount = 2))
    }

    @Test
    fun currentTrackLastInTheOrder_newEntriesComeAtTheEnd() {
        assertEquals(listOf(2, 0, 1), QueueOrder.playNext(listOf(1, 2, 0), currentIndex = 0, insertedFirstIndex = 1, insertedCount = 1))
    }

    @Test
    fun unknownCurrentTrack_newEntriesComeFirst() {
        assertEquals(
            listOf(2, 3, 1, 0),
            QueueOrder.playNext(listOf(3, 1, 0, 2), currentIndex = 9, insertedFirstIndex = 2, insertedCount = 2),
        )
    }

    @Test
    fun nothingAdded_keepsTheOrder() {
        val order = listOf(2, 0, 1)

        assertEquals(order, QueueOrder.playNext(order, currentIndex = 0, insertedFirstIndex = 3, insertedCount = 0))
    }

    @Test
    fun resultIsStillAPermutationOfTheQueue() {
        val order = listOf(7, 3, 0, 5, 1, 6, 2, 4)

        val result = QueueOrder.playNext(order, currentIndex = 5, insertedFirstIndex = 6, insertedCount = 2)

        assertEquals((0..7).toList(), result.sorted())
        assertEquals(listOf(5, 6, 7), result.subList(result.indexOf(5), result.indexOf(5) + 3))
    }
}
