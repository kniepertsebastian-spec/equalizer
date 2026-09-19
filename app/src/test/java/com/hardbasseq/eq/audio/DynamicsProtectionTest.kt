package com.hardbasseq.eq.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicsProtectionTest {
    @Test
    fun limiterThreshold_clampedToZeroDbfsMax() {
        val unsafeSettings =
            ProcessingSettings(
                limiterEnabled = true,
                limiterThresholdDb = 6.0f, // Unsafe positive threshold!
            )

        val safeThreshold = unsafeSettings.limiterThresholdDb.coerceAtMost(0f)
        assertEquals(0f, safeThreshold, 0.001f)
    }

    @Test
    fun defaultSettings_haveSafeLimiterThreshold() {
        val settings = ProcessingSettings()

        assertTrue(settings.limiterThresholdDb <= 0f)
    }
}
