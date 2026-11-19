package com.auracurve.app.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingTrackerTest {
    private fun fix(
        t: Long,
        acc: Float?,
        lat: Double = 48.0,
    ) = GeoFix(lat, 11.0, acc, t)

    @Test
    fun `a recent good fix from the drive is taken at once`() {
        val p = ParkingTracker()
        p.onFix(fix(100_000, 8f, lat = 48.1))
        val spot = p.onIgnitionOff(130_000)!!
        assertEquals(ParkingQuality.GOOD, spot.quality)
        assertEquals(48.1, spot.fix!!.lat, 0.0)
        assertEquals(130_000L, spot.parkedAtMs)
        assertFalse(p.waiting)
    }

    @Test
    fun `without a recent good fix it waits for a fresh one`() {
        val p = ParkingTracker()
        p.onFix(fix(0, 8f)) // too old by switch-off
        assertNull(p.onIgnitionOff(200_000))
        assertTrue(p.waiting)
        assertNull(p.onFix(fix(205_000, 80f)))
        val spot = p.onFix(fix(210_000, 12f, lat = 48.2))!!
        assertEquals(ParkingQuality.GOOD, spot.quality)
        assertEquals(48.2, spot.fix!!.lat, 0.0)
    }

    @Test
    fun `underground car park settles for the best rough fix after 30 s`() {
        val p = ParkingTracker()
        p.onIgnitionOff(0)
        p.onFix(fix(5_000, 120f, lat = 48.3))
        p.onFix(fix(9_000, 60f, lat = 48.4))
        assertNull(p.tick(29_999))
        val spot = p.tick(30_000)!!
        assertEquals(ParkingQuality.INACCURATE, spot.quality)
        assertEquals(48.4, spot.fix!!.lat, 0.0)
    }

    @Test
    fun `no fix at all says so instead of reusing an old position`() {
        val p = ParkingTracker()
        p.onFix(fix(0, 5f))
        p.onIgnitionOff(10 * 60_000)
        val spot = p.tick(10 * 60_000 + 30_000)!!
        assertEquals(ParkingQuality.NONE, spot.quality)
        assertNull(spot.fix)
    }

    @Test
    fun `starting again before a decision cancels it`() {
        val p = ParkingTracker()
        p.onIgnitionOff(0)
        p.onIgnitionOn()
        assertFalse(p.waiting)
        assertNull(p.tick(60_000))
    }
}
