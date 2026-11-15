package com.auracurve.app.ui.share

import com.auracurve.app.data.preset.PresetShareCodec
import com.auracurve.app.preset.BuiltInPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeTest {
    @Test
    fun builtInPresetLink_fitsIntoASquareQrCode() {
        val modules = qrModules(PresetShareCodec.encode(BuiltInPresets.CleanPunch))

        assertNotNull(modules)
        assertTrue(modules!!.size >= 21)
        assertTrue("square", modules.all { it.size == modules.size })
        assertTrue("has dark modules", modules.any { row -> row.any { it } })
    }

    @Test
    fun hugeText_yieldsNullInsteadOfThrowing() {
        assertEquals(null, qrModules("x".repeat(10_000)))
    }
}
