package com.auracurve.app.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10

class SoftClipperTest {
    private val clipper = SoftClipper()

    private fun db(ratio: Double) = 20 * log10(ratio)

    @Test
    fun `below the knee the signal passes linear`() {
        var x = 0.001
        while (x <= SoftClipper.DEFAULT_KNEE) {
            for (sample in doubleArrayOf(x, -x)) {
                val errorDb = abs(db(clipper.process(sample) / sample))
                assertTrue("at $sample the error was $errorDb dB", errorDb < 0.01)
            }
            x += 0.001
        }
    }

    @Test
    fun `above the knee the curve keeps rising`() {
        var previous = clipper.process(SoftClipper.DEFAULT_KNEE)
        var x = SoftClipper.DEFAULT_KNEE + 0.001
        while (x < 4.0) {
            val y = clipper.process(x)
            assertTrue("not rising at $x: $previous -> $y", y > previous || y == clipper.ceiling)
            previous = y
            x += 0.001
        }
    }

    @Test
    fun `the output stays under full scale however loud the input`() {
        for (x in doubleArrayOf(0.9, 1.0, 1.5, 3.0, 10.0, 1e6, Double.MAX_VALUE, Double.POSITIVE_INFINITY)) {
            for (sample in doubleArrayOf(x, -x)) {
                val y = clipper.process(sample)
                assertTrue("$sample gave $y", y.isFinite())
                assertTrue("$sample gave $y", abs(y).toFloat() < 1.0f)
                assertTrue("$sample gave $y", abs(y) <= SoftClipper.DEFAULT_CEILING)
            }
        }
    }

    @Test
    fun `NaN comes out as silence, not as NaN`() {
        assertEquals(0.0, clipper.process(Double.NaN), 0.0)
        val block = doubleArrayOf(0.1, Double.NaN, 2.0)
        clipper.processBlock(block, block.size)
        assertEquals(0.0, block[1], 0.0)
        assertTrue(block.all { it.isFinite() })
    }

    @Test
    fun `the bend starts without a kink at the knee`() {
        val knee = SoftClipper.DEFAULT_KNEE
        val step = 1e-6
        val slopeAbove = (clipper.process(knee + step) - clipper.process(knee)) / step

        assertEquals(1.0, slopeAbove, 1e-3)
    }

    @Test
    fun `negative samples mirror positive ones`() {
        for (x in doubleArrayOf(0.2, 0.8, 1.2, 5.0)) assertEquals(-clipper.process(x), clipper.process(-x), 0.0)
    }

    @Test
    fun `the block form equals the sample form`() {
        val input = DoubleArray(2_000) { (it - 1_000) / 400.0 }
        val block = input.copyOf()

        clipper.processBlock(block, block.size)

        for (n in input.indices) assertEquals(clipper.process(input[n]), block[n], 0.0)
    }

    @Test
    fun `only the requested frames are touched`() {
        val block = doubleArrayOf(2.0, 2.0, 2.0)

        clipper.processBlock(block, 2)

        assertEquals(2.0, block[2], 0.0)
        assertTrue(block[0] < 1.0)
    }

    @Test
    fun `out-of-range knee and ceiling are pulled into a usable shape`() {
        val odd = SoftClipper(knee = 2.0, ceiling = 0.5)

        assertTrue(odd.knee < odd.ceiling)
        assertTrue(odd.ceiling <= 1.0)
        assertTrue(abs(odd.process(100.0)) <= 1.0)
    }
}
