package com.hardbasseq.eq.dsp

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ParametricCurveTest {
    private fun gainAt(
        curve: List<com.hardbasseq.eq.preset.TargetPoint>,
        hz: Float,
    ) = curve.minBy { abs(it.frequencyHz - hz) }.gainDb

    @Test
    fun noBandsMeansNoCurve() {
        assertTrue(ParametricCurve.bandsToCurve(emptyList()).isEmpty())
        assertTrue(ParametricCurve.bandsToCurve(listOf(ParametricBand(1000f, 6f, enabled = false))).isEmpty())
    }

    @Test
    fun peakBand_boostsAroundItsCentreAndLeavesTheFarEndsAlone() {
        val curve = ParametricCurve.bandsToCurve(listOf(ParametricBand(frequencyHz = 100f, gainDb = 6f, q = 2f)))

        assertEquals(6f, gainAt(curve, 100f), 0.6f)
        assertEquals(0f, gainAt(curve, 5000f), 0.3f)
        assertTrue("a higher Q is narrower", gainAt(curve, 200f) < 3f)
    }

    @Test
    fun higherQ_isNarrower() {
        val wide = ParametricCurve.bandsToCurve(listOf(ParametricBand(1000f, 6f, q = 0.5f)))
        val narrow = ParametricCurve.bandsToCurve(listOf(ParametricBand(1000f, 6f, q = 6f)))

        assertTrue(gainAt(wide, 2000f) > gainAt(narrow, 2000f))
    }

    @Test
    fun bandsAdd() {
        val one = ParametricBand(1000f, 3f, 1f)
        val curve = ParametricCurve.bandsToCurve(listOf(one, one))

        assertEquals(2 * gainAt(ParametricCurve.bandsToCurve(listOf(one)), 1000f), gainAt(curve, 1000f), 0.2f)
    }

    @Test
    fun outOfRangeValuesAreClampedNotTrusted() {
        val band = ParametricBand(frequencyHz = 5f, gainDb = 99f, q = 0f).clamped()

        assertEquals(ParametricFilterBounds.MIN_FREQUENCY_HZ, band.frequencyHz, 0f)
        assertEquals(ParametricFilterBounds.MAX_GAIN_DB, band.gainDb, 0f)
        assertEquals(ParametricFilterBounds.MIN_Q, band.q, 0f)
        val curve = ParametricCurve.bandsToCurve(listOf(ParametricBand(0f, 99f, 0f)))
        assertTrue(curve.all { it.gainDb in -24f..24f && !it.gainDb.isNaN() })
    }

    @Test
    fun onlyTheFirstMaxBandsCount() {
        val many = List(ParametricCurve.MAX_BANDS + 5) { ParametricBand(100f * (it + 1), 3f) }

        assertEquals(ParametricCurve.bandsToCurve(many.take(ParametricCurve.MAX_BANDS)), ParametricCurve.bandsToCurve(many))
    }

    @Test
    fun bandsSurviveJson() {
        val bands = listOf(ParametricBand(80f, 4f, 1.4f, ParametricFilterType.LOW_SHELF), ParametricBand(3000f, -2f, 3f, enabled = false))

        val json = Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ParametricBand.serializer()), bands)
        val back = Json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ParametricBand.serializer()), json)

        assertEquals(bands, back)
    }
}
