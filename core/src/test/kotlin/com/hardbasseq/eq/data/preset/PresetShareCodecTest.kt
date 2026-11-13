package com.hardbasseq.eq.data.preset

import com.hardbasseq.eq.preset.BuiltInPresets
import com.hardbasseq.eq.preset.Preset
import com.hardbasseq.eq.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetShareCodecTest {
    private val custom =
        Preset(
            id = "mine",
            name = "Mein Kick",
            targetCurve = (0 until 34).map { TargetPoint(20f * (it + 1), (it % 7) - 3f) },
            macroBassDb = 2.5f,
            virtualBassMix = 0.4f,
        )

    @Test
    fun roundTrip_keepsEverythingButIdentity() {
        val decoded = PresetShareCodec.decode(PresetShareCodec.encode(custom)).getOrThrow()

        assertEquals(custom.name, decoded.name)
        assertEquals(custom.targetCurve, decoded.targetCurve)
        assertEquals(2.5f, decoded.macroBassDb, 0f)
        assertEquals(0.4f, decoded.virtualBassMix, 0f)
        assertNotEquals("the receiver gets a fresh id", custom.id, decoded.id)
        assertTrue(decoded.id.isNotBlank())
    }

    @Test
    fun builtInPreset_isSharedAsAnOrdinaryUserPreset() {
        val decoded = PresetShareCodec.decode(PresetShareCodec.encode(BuiltInPresets.CleanPunch)).getOrThrow()

        assertFalse(decoded.metadata.builtIn)
        assertEquals(BuiltInPresets.CleanPunch.name, decoded.name)
    }

    @Test
    fun link_isCompactEnoughForAQrCode() {
        val link = PresetShareCodec.encode(custom)

        assertTrue("link was ${link.length} chars", link.length <= PresetShareCodec.QR_FRIENDLY_MAX_CHARS)
        assertTrue(PresetShareCodec.isShareLink(link))
    }

    @Test
    fun decode_toleratesSurroundingTextWhitespace() {
        val link = PresetShareCodec.encode(custom)

        assertTrue(PresetShareCodec.decode("  $link\n").isSuccess)
    }

    @Test
    fun decode_rejectsForeignAndBrokenInput() {
        val link = PresetShareCodec.encode(custom)

        assertTrue(PresetShareCodec.decode("https://example.com/x").isFailure)
        assertTrue(PresetShareCodec.decode("hardbasseq://preset").isFailure)
        assertTrue(PresetShareCodec.decode("hardbasseq://preset?v=1").isFailure)
        assertTrue(PresetShareCodec.decode("hardbasseq://preset?v=9&d=abc").isFailure)
        assertTrue(PresetShareCodec.decode("hardbasseq://preset?v=1&d=!!!not-base64!!!").isFailure)
        assertTrue(PresetShareCodec.decode(link.dropLast(20)).isFailure)
    }

    @Test
    fun decode_rejectsOutOfRangeGainFromAHandCraftedLink() {
        val tooLoud = custom.copy(targetCurve = listOf(TargetPoint(100f, 90f)))
        // Encoding does not validate (the sender is trusted code); the receiving side must.
        val link = PresetShareCodec.encode(tooLoud)

        assertTrue(PresetShareCodec.decode(link).isFailure)
    }

    @Test
    fun decode_refusesAnInflationBomb() {
        val bomb =
            java.util.zip.Deflater(9, true).run {
                setInput(ByteArray(10 * 1024 * 1024))
                finish()
                val out = ByteArray(64 * 1024)
                val n = deflate(out)
                end()
                out.copyOf(n)
            }
        val payload =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(bomb)

        val result = PresetShareCodec.decode("hardbasseq://preset?v=1&d=$payload")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("too large") == true)
    }
}
