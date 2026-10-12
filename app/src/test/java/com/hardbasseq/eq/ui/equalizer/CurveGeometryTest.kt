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
}
