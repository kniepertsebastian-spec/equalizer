package com.hardbasseq.eq.playlist

// The order the player steps through the queue while shuffling. Pure functions, free of
// Android/ExoPlayer, so the edge cases are unit-testable.
object QueueOrder {
    /**
     * "Play next" while shuffling. ExoPlayer files newly added entries at random places of its
     * shuffled order, so they would come some time later instead of next.
     *
     * @param playOrder the shuffled order (queue positions) after the new entries were added
     * @param currentIndex queue position of the track that plays now
     * @param insertedFirstIndex queue position of the first new entry
     * @param insertedCount how many entries were added, one after the other
     * @return [playOrder] with the new entries moved right behind [currentIndex], in the order
     *   they were added; at the front when [currentIndex] is not in the order
     */
    fun playNext(
        playOrder: List<Int>,
        currentIndex: Int,
        insertedFirstIndex: Int,
        insertedCount: Int,
    ): List<Int> {
        if (insertedCount <= 0) return playOrder
        val inserted = (insertedFirstIndex until insertedFirstIndex + insertedCount).toList()
        val insertedSet = inserted.toSet()
        val rest = playOrder.filter { it !in insertedSet }
        val currentAt = rest.indexOf(currentIndex)
        return rest.take(currentAt + 1) + inserted + rest.drop(currentAt + 1)
    }
}
