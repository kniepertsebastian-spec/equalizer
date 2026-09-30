package com.hardbasseq.eq.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WeekKeyTest {
    private val day = 86_400_000L

    // 2026-09-28 is a Monday (epoch day 20_724).
    private val monday = 20_724L * day

    @Test
    fun `the week turns over at midnight between sunday and monday`() {
        val sundayNight = monday - 1
        assertNotEquals(WeekKey.of(sundayNight, 0), WeekKey.of(monday, 0))
    }

    @Test
    fun `all days from monday to sunday share a week`() {
        val key = WeekKey.of(monday, 0)
        for (d in 0..6) assertEquals("day $d", key, WeekKey.of(monday + d * day + day / 2, 0))
        assertEquals(key + 1, WeekKey.of(monday + 7 * day, 0))
    }

    @Test
    fun `the local offset moves the boundary`() {
        // 23:30 UTC on Sunday is already Monday 01:30 in UTC+2.
        val sunday2330Utc = monday - 30 * 60_000L
        val twoHours = 2 * 3_600_000L
        assertEquals(WeekKey.of(monday, 0), WeekKey.of(sunday2330Utc, twoHours))
        assertNotEquals(WeekKey.of(monday, 0), WeekKey.of(sunday2330Utc, 0))
    }

    @Test
    fun `dates before 1970 still work`() {
        assertEquals(WeekKey.of(-day, 0), WeekKey.of(-2 * day, 0))
    }
}
