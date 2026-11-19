package com.auracurve.app.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatProtectionTest {
    @Test
    fun `each thermal status maps to its step`() {
        val levels = (0..6).map { HeatProtection.level(it, null) }
        assertEquals(
            listOf(
                ProtectionLevel.NONE,
                ProtectionLevel.NONE,
                ProtectionLevel.WARN,
                ProtectionLevel.SHED,
                ProtectionLevel.CRITICAL,
                ProtectionLevel.CRITICAL,
                ProtectionLevel.CRITICAL,
            ),
            levels,
        )
    }

    @Test
    fun `a hot battery counts even when the thermal status is still calm`() {
        assertEquals(ProtectionLevel.NONE, HeatProtection.level(0, 38f))
        assertEquals(ProtectionLevel.WARN, HeatProtection.level(0, 42f))
        assertEquals(ProtectionLevel.SHED, HeatProtection.level(1, 46f))
        assertEquals(ProtectionLevel.CRITICAL, HeatProtection.level(2, 51f))
        // The worse of the two wins.
        assertEquals(ProtectionLevel.CRITICAL, HeatProtection.level(4, 30f))
    }

    @Test
    fun `background pauses from SHED on, standby alone keeps the home sync`() {
        assertFalse(Protection(ProtectionLevel.WARN, standby = false).pauseBackground)
        assertTrue(Protection(ProtectionLevel.SHED, standby = false).pauseBackground)
        assertFalse(Protection(ProtectionLevel.NONE, standby = true).pauseBackground)
    }

    @Test
    fun `standby after two hours parked or with a low battery`() {
        val off = Ignition(IgnitionState.OFF, sinceMs = 0, source = IgnitionSource.NONE)
        val twoHours = HeatProtection.STANDBY_AFTER_MS
        assertFalse(HeatProtection.standby(off, 80, twoHours - 1))
        assertTrue(HeatProtection.standby(off, 80, twoHours))
        assertTrue(HeatProtection.standby(off, 29, 60_000))
        assertFalse(HeatProtection.standby(off, null, 60_000))
    }

    @Test
    fun `never standby while the car is on or not known yet`() {
        val on = Ignition(IgnitionState.ON, sinceMs = 0, source = IgnitionSource.POWER)
        assertFalse(HeatProtection.standby(on, 5, HeatProtection.STANDBY_AFTER_MS * 10))
        assertFalse(HeatProtection.standby(null, 5, HeatProtection.STANDBY_AFTER_MS * 10))
    }

    @Test
    fun `back to calm means no protection`() {
        val p = HeatProtection.protection(thermalStatus = 0, batteryTempC = 30f, batteryPercent = 90, ignition = null, nowMs = 0)
        assertEquals(Protection.NONE, p)
    }
}
