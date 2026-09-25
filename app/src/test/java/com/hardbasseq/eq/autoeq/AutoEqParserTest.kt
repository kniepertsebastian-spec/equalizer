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
}
