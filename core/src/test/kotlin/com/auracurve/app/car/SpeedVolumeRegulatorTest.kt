package com.auracurve.app.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedVolumeRegulatorTest {
    private val on = SpeedVolumeSettings(enabled = true, maxBoostDb = 4f, startSpeedKmh = 30f, fullSpeedKmh = 130f)

    /** Feeds one value per second for [seconds] and returns the last output. */
    private fun SpeedVolumeRegulator.drive(
        fromS: Int,
        seconds: Int,
        speed: Float?,
        settings: SpeedVolumeSettings = on,
        frozen: Boolean = false,
    ): Float {
        var out = 0f
        for (s in fromS until fromS + seconds) out = update(settings, speed, s * 1000L, frozen)
        return out
    }

    @Test
    fun `the target is a stepless mix between town and motorway`() {
        assertEquals(0f, SpeedVolumeRegulator.targetDb(on, 20f), 0.001f)
        assertEquals(0f, SpeedVolumeRegulator.targetDb(on, 30f), 0.001f)
        assertEquals(2f, SpeedVolumeRegulator.targetDb(on, 80f), 0.001f)
        assertEquals(4f, SpeedVolumeRegulator.targetDb(on, 130f), 0.001f)
        assertEquals(4f, SpeedVolumeRegulator.targetDb(on, 180f), 0.001f)
    }

    @Test
    fun `motorway on-ramp rises slowly, at most half a dB per second`() {
        val r = SpeedVolumeRegulator()
        r.drive(0, 10, 50f)
        val before = r.outputDb
        val after2s = r.drive(10, 2, 130f)
        assertTrue("rose ${after2s - before} dB in 2 s", after2s - before <= 1.0f + 0.05f)
        assertEquals(4f, r.drive(12, 30, 130f), 0.05f)
    }

    @Test
    fun `a tunnel keeps the level instead of dropping it`() {
        val r = SpeedVolumeRegulator()
        r.drive(0, 30, 130f)
        assertEquals(4f, r.drive(30, 60, null), 0.05f)
    }

    @Test
    fun `a traffic light stop lowers slowly, not at once`() {
        val r = SpeedVolumeRegulator()
        r.drive(0, 30, 130f)
        val after4s = r.drive(30, 4, 0f)
        assertTrue("dropped to $after4s", after4s >= 2.9f)
        assertEquals(0f, r.drive(34, 30, 0f), 0.05f)
    }

    @Test
    fun `GPS noise is averaged away`() {
        val r = SpeedVolumeRegulator()
        var out = 0f
        for (s in 0 until 60) out = r.update(on, if (s % 2 == 0) 70f else 90f, s * 1000L)
        assertEquals(2f, out, 0.15f)
    }

    @Test
    fun `while paused the level does not move`() {
        val r = SpeedVolumeRegulator()
        r.drive(0, 30, 130f)
        assertEquals(4f, r.drive(30, 20, 0f, frozen = true), 0.05f)
    }

    @Test
    fun `switched off it fades out to exactly zero`() {
        val r = SpeedVolumeRegulator()
        r.drive(0, 30, 130f)
        assertEquals(0f, r.drive(30, 30, 130f, settings = on.copy(enabled = false)), 0f)
    }

    @Test
    fun `a manual volume change needs nothing from the regulator`() {
        // The boost is relative to the system volume, so the same speed gives the same boost
        // whatever the user set; nothing to reset.
        val r = SpeedVolumeRegulator()
        assertEquals(r.drive(0, 30, 100f), r.drive(30, 10, 100f), 0.05f)
    }

    @Test
    fun `settings are kept within safe limits`() {
        val s = SpeedVolumeSettings(enabled = true, maxBoostDb = 20f, startSpeedKmh = 100f, fullSpeedKmh = 90f).sanitized()
        assertEquals(6f, s.maxBoostDb, 0f)
        assertEquals(120f, s.fullSpeedKmh, 0f)
    }

    @Test
    fun `the output comes in tenth-of-a-dB steps`() {
        val r = SpeedVolumeRegulator()
        val out = r.drive(0, 3, 130f)
        assertEquals(out, Math.round(out * 10) / 10f, 0.0001f)
    }
}
