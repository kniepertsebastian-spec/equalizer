package com.auracurve.app.playlist

// Keys for the rows of the queue list. A row's key has to stay with its entry while other
// entries are removed or moved: a list keyed by position hands the state of a removed row
// (how far it was swiped) to the entry that takes its place. The track id alone is not enough
// either, since the same track can be queued more than once.
//
// Each time the player publishes its queue, [assign] gives every entry the key it had before
// (matched by track, in order) and a new key to entries that were not there. Free of
// Android/Compose so the matching is unit-testable; not thread-safe, used from the UI thread.
class QueueRowKeys {
    private class Row(
        val trackId: Long,
        val key: Long,
    )

    private var nextKey = 0L
    private var rows: List<Row> = emptyList()

    /** One key per entry of [trackIds], in the same order. Unique within the result. */
    fun assign(trackIds: List<Long>): List<Long> {
        val free = HashMap<Long, ArrayDeque<Long>>()
        rows.forEach { row -> free.getOrPut(row.trackId) { ArrayDeque() }.addLast(row.key) }
        val assigned =
            trackIds.map { trackId ->
                Row(trackId, free[trackId]?.removeFirstOrNull() ?: nextKey++)
            }
        rows = assigned
        return assigned.map { it.key }
    }

    /**
     * The row with [key] was removed by the user. Without this, the next [assign] would hand
     * the key to another entry of the same track (a duplicate), and with it the removed
     * row's state.
     */
    fun forget(key: Long) {
        rows = rows.filterNot { it.key == key }
    }
}
