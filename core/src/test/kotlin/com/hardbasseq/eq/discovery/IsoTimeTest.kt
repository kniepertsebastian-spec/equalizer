package com.hardbasseq.eq.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsoTimeTest {
    @Test
    fun `parses soundcloud timestamps`() {
        assertEquals(0L, IsoTime.parseMillis("1970-01-01T00:00:00Z"))
        assertEquals(20_724L * 86_400_000L, IsoTime.parseMillis("2026-09-28T00:00:00Z"))
        assertEquals(20_724L * 86_400_000L + 12 * 3_600_000L + 34 * 60_000L + 56_000L, IsoTime.parseMillis("2026-09-28T12:34:56Z"))
    }

    @Test
    fun `parses the older slash format and ignores offsets and fractions`() {
        assertEquals(IsoTime.parseMillis("2026-09-28T12:34:56Z"), IsoTime.parseMillis("2026/09/28 12:34:56 +0000"))
        assertEquals(IsoTime.parseMillis("2026-09-28T12:34:56Z"), IsoTime.parseMillis("2026-09-28T12:34:56.789Z"))
    }

    @Test
    fun `handles leap days and year boundaries`() {
        // 2024-02-29 is day 19_782; 2024-03-01 the next.
        assertEquals(19_782L * 86_400_000L, IsoTime.parseMillis("2024-02-29T00:00:00Z"))
        assertEquals(19_783L * 86_400_000L, IsoTime.parseMillis("2024-03-01T00:00:00Z"))
    }

    @Test
    fun `garbage is null`() {
        assertNull(IsoTime.parseMillis(null))
        assertNull(IsoTime.parseMillis(""))
        assertNull(IsoTime.parseMillis("yesterday"))
        assertNull(IsoTime.parseMillis("2026-13-40T00:00:00Z"))
    }
}
