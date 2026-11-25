package com.auracurve.app.ui.components.cyber

import com.auracurve.app.preset.TargetPoint
import com.auracurve.app.ui.equalizer.CurveGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class CurveSamplerTest {
    private val points =
        listOf(
            TargetPoint(60f, 6f),
            TargetPoint(230f, -2f),
            TargetPoint(910f, 0f),
            TargetPoint(3600f, 3f),
            TargetPoint(14_000f, 1f),
        )

    @Test
    fun `draws the same curve as CurveGeometry`() {
        val sampler = CurveSampler(capacity = 2)
        sampler.resize(points.size)
        points.forEachIndexed { i, point -> sampler.put(i, point.frequencyHz, point.gainDb) }
        assertEquals(points.size, sampler.size)
        for (i in 0..160) {
            val fraction = i / 160f
            val expected = CurveGeometry.gainAt(points, CurveGeometry.frequencyAt(fraction))
            assertEquals("at $fraction", expected, sampler.gainAt(fraction), 2e-3f)
        }
    }

    @Test
    fun `an empty curve is flat and a single point holds`() {
        val sampler = CurveSampler(capacity = 4)
        assertEquals(0f, sampler.gainAt(0.5f), 0f)
        sampler.resize(1)
        sampler.put(0, 1000f, 4f)
        assertEquals(4f, sampler.gainAt(0f), 0f)
        assertEquals(4f, sampler.gainAt(1f), 0f)
    }
}
