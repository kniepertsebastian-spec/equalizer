package com.auracurve.app.preset

import com.auracurve.app.preset.PresetUsageHint.KICK_FOCUS
import com.auracurve.app.preset.PresetUsageHint.NEUTRAL
import com.auracurve.app.preset.PresetUsageHint.QUIET_LISTENING
import com.auracurve.app.preset.PresetUsageHint.SMALL_DRIVERS
import com.auracurve.app.preset.PresetUsageHint.SOFTENED_HIGHS
import com.auracurve.app.preset.PresetUsageHint.SUB_FOCUS
import com.auracurve.app.preset.PresetUsageHint.VERY_STRONG_BOOST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetUsageAdvisorTest {
    private fun preset(
        vararg points: Pair<Number, Number>,
        bass: Float = 0f,
        punch: Float = 0f,
        haerte: Float = 0f,
        loudness: Float = 0f,
        virtualBass: Float = 0f,
    ) = Preset(
        id = "test",
        name = "Test",
        targetCurve = points.map { (hz, db) -> TargetPoint(hz.toFloat(), db.toFloat()) },
        macroBassDb = bass,
        macroPunchDb = punch,
        macroHaerteDb = haerte,
        loudnessMaxBoostDb = loudness,
        virtualBassMix = virtualBass,
    )

    @Test
    fun `a flat preset is neutral and nothing else`() {
        val usage = PresetUsageAdvisor.describe(BuiltInPresets.Flat)

        assertEquals(listOf(NEUTRAL), usage.hints)
        assertEquals(0f, usage.strongestBoostDb, 0f)
    }

    @Test
    fun `a flat curve with loudness is not neutral`() {
        val usage = PresetUsageAdvisor.describe(preset(100 to 0, 1000 to 0, loudness = 4f))

        assertEquals(listOf(QUIET_LISTENING), usage.hints)
    }

    @Test
    fun `kick snap max peaks at the kick with a very strong boost`() {
        val usage =
            PresetUsageAdvisor.describe(
                preset(
                    60 to 3,
                    110 to 3,
                    300 to -4.5,
                    1200 to -1,
                    3500 to 2,
                    7000 to 0.5,
                    12000 to -1,
                    bass = 2f,
                    punch = 5f,
                    haerte = 1f,
                ),
            )

        assertEquals(listOf(KICK_FOCUS, VERY_STRONG_BOOST), usage.hints)
        assertEquals(10f, usage.strongestBoostDb, 0.05f)
    }

    @Test
    fun `a deep low shelf without punch is sub focused`() {
        val usage = PresetUsageAdvisor.describe(preset(35 to 5, 50 to 5, 120 to 0, 1000 to 0))

        assertTrue(SUB_FOCUS in usage.hints)
        assertFalse(KICK_FOCUS in usage.hints)
        assertFalse(VERY_STRONG_BOOST in usage.hints)
        assertTrue(SUB_FOCUS in PresetUsageAdvisor.describe(BuiltInPresets.DeepRumble).hints)
    }

    @Test
    fun `virtual bass points to small drivers`() {
        val usage = PresetUsageAdvisor.describe(preset(90 to 3, 1000 to 0, virtualBass = 0.35f))

        assertTrue(SMALL_DRIVERS in usage.hints)
    }

    @Test
    fun `the terrorcore preset softens the upper mids`() {
        assertTrue(SOFTENED_HIGHS in PresetUsageAdvisor.describe(BuiltInPresets.MaximumDistortion).hints)
        assertFalse(SOFTENED_HIGHS in PresetUsageAdvisor.describe(BuiltInPresets.FinalSmash).hints)
    }

    @Test
    fun `every built-in preset gets at least one hint or a boost to report`() {
        BuiltInPresets.all.forEach { preset ->
            val usage = PresetUsageAdvisor.describe(preset)
            assertTrue("${preset.id} says nothing", usage.hints.isNotEmpty() || usage.strongestBoostDb > 0f)
        }
    }
}
