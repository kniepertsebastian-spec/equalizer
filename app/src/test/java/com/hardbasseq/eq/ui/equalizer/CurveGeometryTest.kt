package com.hardbasseq.eq.ui.equalizer

import com.hardbasseq.eq.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurveGeometryTest {
    private val bands =
        listOf(
            TargetPoint(60f, 6f),
            TargetPoint(230f, -2f),
            TargetPoint(910f, 0f),
            TargetPoint(3600f, 3f),
            TargetPoint(14_000f, 1f),
        )

    @Test
    fun `axis is logarithmic from 20 Hz to 20 kHz`() {
        assertEquals(0f, CurveGeometry.xFraction(20f), 1e-4f)
        assertEquals(1f, CurveGeometry.xFraction(20_000f), 1e-4f)
        // 632 Hz is the geometric middle of 20 Hz and 20 kHz.
        assertEquals(0.5f, CurveGeometry.xFraction(632.46f), 1e-3f)
        assertEquals(0f, CurveGeometry.xFraction(5f), 1e-4f)
        assertEquals(1f, CurveGeometry.xFraction(40_000f), 1e-4f)
    }

    @Test
    fun `frequencyAt inverts xFraction`() {
        listOf(20f, 60f, 1000f, 14_000f).forEach { hz ->
            assertEquals(hz, CurveGeometry.frequencyAt(CurveGeometry.xFraction(hz)), hz * 1e-3f)
        }
    }

    @Test
    fun `curve passes exactly through every point and holds the ends`() {
        bands.forEach { point ->
            assertEquals(point.gainDb, CurveGeometry.gainAt(bands, point.frequencyHz), 1e-4f)
        }
        assertEquals(6f, CurveGeometry.gainAt(bands, 20f), 1e-4f)
        assertEquals(1f, CurveGeometry.gainAt(bands, 20_000f), 1e-4f)
    }

    @Test
    fun `between two points the curve stays inside their gains`() {
        val gain = CurveGeometry.gainAt(bands, 120f)
        assertTrue("gain $gain should lie between -2 and 6", gain > -2f && gain < 6f)
    }

    @Test
    fun `point order does not matter`() {
        assertEquals(
            CurveGeometry.gainAt(bands, 500f),
            CurveGeometry.gainAt(bands.reversed(), 500f),
            1e-5f,
        )
    }

    @Test
    fun `empty curve is flat`() {
        assertEquals(0f, CurveGeometry.gainAt(emptyList(), 1000f), 0f)
        assertNull(CurveGeometry.strongestChange(emptyList()))
        assertNull(CurveGeometry.strongestChange(listOf(TargetPoint(100f, 0f))))
    }

    @Test
    fun `range is at least six dB and rounded up`() {
        assertEquals(6f, CurveGeometry.rangeDb(bands), 0f)
        assertEquals(9f, CurveGeometry.rangeDb(listOf(TargetPoint(50f, -8.2f))), 0f)
    }

    @Test
    fun `strongest change picks the largest boost or cut`() {
        assertEquals(TargetPoint(60f, 6f), CurveGeometry.strongestChange(bands))
        assertEquals(
            TargetPoint(3000f, -7f),
            CurveGeometry.strongestChange(listOf(TargetPoint(60f, 4f), TargetPoint(3000f, -7f))),
        )
    }

    @Test
    fun `frequency labels are short and locale independent`() {
        assertEquals("55 Hz", CurveGeometry.formatFrequency(55f))
        assertEquals("1 kHz", CurveGeometry.formatFrequency(1000f))
        assertEquals("3.6 kHz", CurveGeometry.formatFrequency(3600f))
        assertEquals("14 kHz", CurveGeometry.formatFrequency(14_000f))
    }

    @Test
    fun `scale follows the device range and falls back to 12 dB`() {
        assertEquals(15f, CurveGeometry.scaleDb(-15f, 15f), 0f)
        assertEquals(12f, CurveGeometry.scaleDb(-12f, 10.5f), 0f)
        assertEquals(6f, CurveGeometry.scaleDb(-3f, 3f), 0f)
        assertEquals(12f, CurveGeometry.scaleDb(null, null), 0f)
    }

    @Test
    fun `gain ticks are symmetric and include 0 dB`() {
        assertEquals(listOf(15f, 10f, 5f, 0f, -5f, -10f, -15f), CurveGeometry.gainTicks(15f))
        assertEquals(listOf(12f, 6f, 0f, -6f, -12f), CurveGeometry.gainTicks(12f))
        assertEquals(listOf(6f, 3f, 0f, -3f, -6f), CurveGeometry.gainTicks(6f))
    }

    @Test
    fun `minor grid lines skip the labelled ones and stay inside the axis`() {
        assertTrue(CurveGeometry.minorFrequencies.none { it in CurveGeometry.labelledFrequencies })
        assertTrue(CurveGeometry.minorFrequencies.all { it in 20f..20_000f })
        assertTrue(30f in CurveGeometry.minorFrequencies)
        assertTrue(9000f in CurveGeometry.minorFrequencies)
    }

    @Test
    fun `labels use a real minus sign and one decimal for values`() {
        assertEquals("5k", CurveGeometry.axisLabel(5000f))
        assertEquals("200", CurveGeometry.axisLabel(200f))
        assertEquals("+6", CurveGeometry.gainTickLabel(6f))
        assertEquals("\u22126", CurveGeometry.gainTickLabel(-6f))
        assertEquals("0", CurveGeometry.gainTickLabel(0f))
        assertTrue(CurveGeometry.formatGain(4.46f).startsWith("+4"))
        assertTrue(CurveGeometry.formatGain(-2f).startsWith("\u22122"))
        assertTrue(CurveGeometry.formatGain(-0.01f).startsWith("0"))
        assertTrue(CurveGeometry.formatGain(1f).endsWith(" dB"))
    }

    @Test
    fun `nearest band is found on the log axis`() {
        val centers = bands.map { it.frequencyHz }
        assertEquals(0, CurveGeometry.nearestIndex(centers, 0f))
        assertEquals(4, CurveGeometry.nearestIndex(centers, 1f))
        assertEquals(2, CurveGeometry.nearestIndex(centers, CurveGeometry.xFraction(1000f)))
        assertNull(CurveGeometry.nearestIndex(emptyList(), 0.5f))
    }
}
