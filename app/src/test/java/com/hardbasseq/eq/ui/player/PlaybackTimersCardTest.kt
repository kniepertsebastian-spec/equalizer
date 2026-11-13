package com.hardbasseq.eq.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimersCardTest {
    @Test
    fun formatCountdown_showsMinutesAndSecondsAndHours() {
        assertEquals("0:00", formatCountdown(0))
        assertEquals("0:01", formatCountdown(1))
        assertEquals("1:00", formatCountdown(60_000))
        assertEquals("14:59", formatCountdown(899_000))
        assertEquals("1:30:00", formatCountdown(90 * 60_000L))
    }

    @Test
    fun formatCountdown_neverGoesNegative() {
        assertEquals("0:00", formatCountdown(-5_000))
    }
}
