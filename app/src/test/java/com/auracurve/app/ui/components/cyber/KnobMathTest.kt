package com.auracurve.app.ui.components.cyber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnobMathTest {
    private val bass = -6f..6f
    private val harshness = -2f..2f

    @Test
    fun `dial runs 270 degrees with 0 dB at the top`() {
        assertEquals(-135f, KnobMath.angleFor(-6f, bass), 1e-4f)
        assertEquals(0f, KnobMath.angleFor(0f, bass), 1e-4f)
        assertEquals(135f, KnobMath.angleFor(6f, bass), 1e-4f)
        assertEquals(135f, KnobMath.angleFor(40f, bass), 1e-4f)
    }

    @Test
    fun `angle and value round trip`() {
        listOf(-6f, -2.5f, 0f, 1.5f, 6f).forEach { value ->
            assertEquals(value, KnobMath.valueAt(KnobMath.angleFor(value, bass), bass), 1e-4f)
        }
        listOf(-2f, -0.5f, 0f, 1f, 2f).forEach { value ->
            assertEquals(value, KnobMath.valueAt(KnobMath.angleFor(value, harshness), harshness), 1e-4f)
        }
    }

    @Test
    fun `snap lands on half dB steps inside the range`() {
        assertEquals(1.5f, KnobMath.snap(1.37f, 0.5f, bass), 1e-6f)
        assertEquals(-0.5f, KnobMath.snap(-0.3f, 0.5f, bass), 1e-6f)
        assertEquals(0f, KnobMath.snap(0.2f, 0.5f, bass), 1e-6f)
        assertEquals(6f, KnobMath.snap(9f, 0.5f, bass), 1e-6f)
        assertEquals(-2f, KnobMath.snap(-2.4f, 0.5f, harshness), 1e-6f)
    }

    @Test
    fun `segments light from 0 dB toward the value`() {
        assertTrue(KnobMath.litSegments(0f, bass).isEmpty())
        assertEquals(12..12, KnobMath.litSegments(0.5f, bass))
        assertEquals(11..11, KnobMath.litSegments(-0.5f, bass))
        assertEquals(12..23, KnobMath.litSegments(6f, bass))
        assertEquals(0..11, KnobMath.litSegments(-6f, bass))
        // Even a small value off 0 lights the segment it falls in.
        assertEquals(12..12, KnobMath.litSegments(0.1f, bass))
    }

    @Test
    fun `segments of a range without 0 light from its start`() {
        assertEquals(0..11, KnobMath.litSegments(5f, 0f..10f))
        assertEquals(0..23, KnobMath.litSegments(3f, 1f..3f))
        assertTrue(KnobMath.litSegments(1f, 1f..3f).isEmpty())
    }

    @Test
    fun `dragging up over the travel covers the whole range`() {
        assertEquals(12f, KnobMath.dragDelta(-220f, 220f, bass), 1e-4f)
        assertEquals(-2f, KnobMath.dragDelta(110f, 220f, harshness), 1e-4f)
    }

    @Test
    fun `finger angles run clockwise from 12 o'clock`() {
        assertEquals(0f, KnobMath.pointerAngle(0f, -50f), 1e-4f)
        assertEquals(90f, KnobMath.pointerAngle(50f, 0f), 1e-4f)
        assertEquals(-90f, KnobMath.pointerAngle(-50f, 0f), 1e-4f)
    }

    @Test
    fun `a radial turn takes the short way across the bottom`() {
        assertEquals(20f, KnobMath.angleDelta(170f, -170f), 1e-4f)
        assertEquals(-20f, KnobMath.angleDelta(-170f, 170f), 1e-4f)
        assertEquals(-30f, KnobMath.angleDelta(30f, 0f), 1e-4f)
        // A quarter of the sweep is a quarter of the range.
        assertEquals(3f, KnobMath.turnDelta(67.5f, bass), 1e-4f)
    }
}
