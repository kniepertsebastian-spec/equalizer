package com.hardbasseq.eq.discovery

// Identifies the calendar week (Monday to Sunday) a moment falls in, so "a new week
// has started" is a plain inequality. Plain arithmetic instead of java.time: :core is
// also used on Android versions where java.time is not available without desugaring.
object WeekKey {
    private const val MS_PER_DAY = 86_400_000L

    // 1 January 1970 was a Thursday, so shifting by 3 days makes week boundaries fall
    // on Mondays.
    private const val THURSDAY_TO_MONDAY_SHIFT = 3L

    // utcOffsetMs is the device's current offset from UTC (TimeZone.getOffset), so the
    // week turns over at local midnight, not UTC midnight.
    fun of(
        nowMs: Long,
        utcOffsetMs: Long,
    ): Long {
        val epochDay = Math.floorDiv(nowMs + utcOffsetMs, MS_PER_DAY)
        return Math.floorDiv(epochDay + THURSDAY_TO_MONDAY_SHIFT, 7L)
    }
}
