package com.hardbasseq.eq.preset

import com.hardbasseq.eq.correction.BuiltInCorrectionProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PortableSoundProfileTest {
    private val profile =
        PortableSoundProfile(
            preset = BuiltInPresets.CleanPunch,
            correction = BuiltInCorrectionProfiles.None,
            appliedEqCurve = listOf(TargetPoint(90f, 4f)),
            macroBassDb = 1.5f,
            macroPunchDb = 2f,
            macroHaerteDb = 0.5f,
            inputGainDb = -1.5f,
            mbcEnabled = true,
            mbcThresholdDb = -7f,
            mbcRatio = 2.5f,
            limiter = LimiterConfig(true, -1f),
            processingEnabled = true,
        )

    @Test
    fun `phone profile round trips with live band settings`() {
        val restored = PortableSoundProfileJson.import(PortableSoundProfileJson.export(profile))
        assertEquals(profile, restored)
    }

    @Test
    fun `unsupported profile version is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PortableSoundProfileJson.import(PortableSoundProfileJson.export(profile).replaceFirst("{", "{\"version\":2,"))
        }
    }
}
