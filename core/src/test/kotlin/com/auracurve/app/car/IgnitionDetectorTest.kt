package com.auracurve.app.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IgnitionDetectorTest {
    private val delay = IgnitionDetector.DEFAULT_OFF_DELAY_MS

    @Test
    fun `starts off and turns on at once with power`() {
        val d = IgnitionDetector(startMs = 0)
        assertEquals(Ignition(IgnitionState.OFF, 0, IgnitionSource.NONE), d.current)
        assertEquals(Ignition(IgnitionState.ON, 1_000, IgnitionSource.POWER), d.update(power = true, bluetooth = false, nowMs = 1_000))
    }

    @Test
    fun `cranking cuts the power for a moment without turning off`() {
        val d = IgnitionDetector(startMs = 0)
        d.update(power = true, bluetooth = false, nowMs = 0)
        d.update(power = false, bluetooth = false, nowMs = 10_000)
        assertEquals(10_000 + delay, d.pendingOffAtMs)
        assertEquals(IgnitionState.ON, d.tick(12_000).state)
        val back = d.update(power = true, bluetooth = false, nowMs = 12_500)
        assertEquals(Ignition(IgnitionState.ON, 0, IgnitionSource.POWER), back)
        assertNull(d.pendingOffAtMs)
        assertEquals(IgnitionState.ON, d.tick(10_000 + delay + 1).state)
    }

    @Test
    fun `start-stop with bluetooth still connected stays on`() {
        val d = IgnitionDetector(startMs = 0)
        d.update(power = true, bluetooth = true, nowMs = 0)
        val stop = d.update(power = false, bluetooth = true, nowMs = 60_000)
        assertEquals(Ignition(IgnitionState.ON, 0, IgnitionSource.BLUETOOTH), stop)
        assertNull(d.pendingOffAtMs)
    }

    @Test
    fun `getting out turns off after the delay, dated when the hints went away`() {
        val d = IgnitionDetector(startMs = 0)
        d.update(power = true, bluetooth = true, nowMs = 0)
        d.update(power = false, bluetooth = true, nowMs = 100_000)
        d.update(power = false, bluetooth = false, nowMs = 103_000)
        assertEquals(IgnitionState.ON, d.tick(103_000 + delay - 1).state)
        assertEquals(Ignition(IgnitionState.OFF, 103_000, IgnitionSource.NONE), d.tick(103_000 + delay))
        assertNull(d.pendingOffAtMs)
    }

    @Test
    fun `only bluetooth or only power each count as on`() {
        val bt = IgnitionDetector(startMs = 0)
        assertEquals(IgnitionSource.BLUETOOTH, bt.update(power = false, bluetooth = true, nowMs = 5).source)
        val power = IgnitionDetector(startMs = 0)
        assertEquals(IgnitionSource.POWER, power.update(power = true, bluetooth = false, nowMs = 5).source)
    }

    @Test
    fun `the off delay is also checked by a late update`() {
        val d = IgnitionDetector(startMs = 0, offDelayMs = 1_000)
        d.update(power = true, bluetooth = false, nowMs = 0)
        d.update(power = false, bluetooth = false, nowMs = 10)
        // No tick ran (the phone slept); the next update still sees the delay is over.
        assertEquals(Ignition(IgnitionState.OFF, 10, IgnitionSource.NONE), d.update(power = false, bluetooth = false, nowMs = 5_000))
    }

    @Test
    fun `off stays off without hints`() {
        val d = IgnitionDetector(startMs = 0)
        assertEquals(Ignition(IgnitionState.OFF, 0, IgnitionSource.NONE), d.update(power = false, bluetooth = false, nowMs = 50))
        assertNull(d.pendingOffAtMs)
    }
}
