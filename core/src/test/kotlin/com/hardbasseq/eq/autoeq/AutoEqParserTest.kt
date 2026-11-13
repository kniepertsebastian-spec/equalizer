package com.hardbasseq.eq.autoeq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqParserTest {
    @Test
    fun parseAutoEqText_graphicEqLineFormat_parsedSuccessfully() {
        val sampleGraphicEqText = "GraphicEQ: 20 0; 50 3.5; 100 2.0; 1000 -1.5"

        val result = AutoEqParser.parseAutoEqText("Sony WH-1000XM4 AutoEQ", sampleGraphicEqText)

        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals("Sony WH-1000XM4 AutoEQ", profile.name)
        assertEquals(4, profile.curve.size)
        assertEquals(50f, profile.curve[1].frequencyHz, 0.001f)
        assertEquals(3.5f, profile.curve[1].gainDb, 0.001f)
    }

    @Test
    fun parseAutoEqText_tsvFormat_parsedSuccessfully() {
        val sampleTsvText =
            """
            # Frequency Gain
            20.0	0.0
            60.0	4.0
            250.0	-2.0
            4000.0	1.0
            """.trimIndent()

        val result = AutoEqParser.parseAutoEqText("Sennheiser HD 600", sampleTsvText)

        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals(4, profile.curve.size)
        assertEquals(60f, profile.curve[1].frequencyHz, 0.001f)
        assertEquals(4.0f, profile.curve[1].gainDb, 0.001f)
    }

    @Test
    fun parseAutoEqText_waveletStyleGraphicEq_parsedSuccessfully() {
        // Wavelet exports/imports the same "GraphicEQ:" line (AutoEq's GraphicEQ.txt), usually on one
        // long line with several dozen points and a trailing newline.
        val waveletText = "GraphicEQ: 20 -2.4; 21 -2.5; 22 -2.5; 1000 0.0; 20000 -8.1\n"

        val profile = AutoEqParser.parseAutoEqText("Wavelet export", waveletText).getOrThrow()

        assertEquals(5, profile.curve.size)
        assertEquals(-8.1f, profile.curve.last().gainDb, 0.001f)
        assertEquals(20000f, profile.curve.last().frequencyHz, 0.001f)
    }

    @Test
    fun parseAutoEqText_parametricEqFile_evaluatesFiltersIntoCurve() {
        val text =
            """
            Preamp: -6.2 dB
            Filter 1: ON PK Fc 100 Hz Gain 6.0 dB Q 1.00
            Filter 2: OFF PK Fc 3000 Hz Gain 9.0 dB Q 1.00
            Filter 3: ON HSC Fc 10000 Hz Gain -4.0 dB Q 0.70
            """.trimIndent()

        val profile = AutoEqParser.parseAutoEqText("AutoEq parametric", text).getOrThrow()

        fun gainNear(hz: Float) = profile.curve.minBy { kotlin.math.abs(it.frequencyHz - hz) }.gainDb

        assertTrue("peak centre is boosted", gainNear(100f) in 5.0f..6.5f)
        assertEquals("disabled filter has no effect", 0f, gainNear(3000f), 0.5f)
        assertTrue("high shelf cuts the top", gainNear(20000f) < -3f)
        assertEquals("far from every filter stays flat", 0f, gainNear(1000f), 0.8f)
    }

    @Test
    fun parseAutoEqText_parametricWithoutUsableFilters_fails() {
        val result = AutoEqParser.parseAutoEqText("Empty", "Preamp: -3 dB\nFilter 1: OFF PK Fc 100 Hz Gain 3 dB Q 1")

        assertTrue(result.isFailure)
    }
}
