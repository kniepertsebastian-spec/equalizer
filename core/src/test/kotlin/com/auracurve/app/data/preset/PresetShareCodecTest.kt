package com.auracurve.app.data.preset

import com.auracurve.app.preset.BuiltInGenreVariants
import com.auracurve.app.preset.BuiltInPlayerPresets
import com.auracurve.app.preset.BuiltInPresets
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.PresetSourceScope
import com.auracurve.app.preset.TargetPoint
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

    @Test
    fun link_usesTheAuraCurveSchemeAndStillReadsTheOldOne() {
        val link = PresetShareCodec.encode(custom)
        assertTrue(link, link.startsWith("auracurve://preset?"))

        val legacy = link.replaceFirst("auracurve://", "hardbasseq://")
        assertTrue(PresetShareCodec.isShareLink(legacy))
        assertEquals(custom.name, PresetShareCodec.decode(legacy).getOrThrow().name)
    }

    // Roadmap 15: PresetSourceScope.

    private fun linkFor(json: String): String {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val deflated =
            java.util.zip.Deflater(9, true).run {
                setInput(bytes)
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
                .encodeToString(deflated)
        return "auracurve://preset?v=1&d=$payload"
    }

    @Test
    fun linkFromBeforeSourceScope_decodesAsAll() {
        val old =
            """
            {"id":"","name":"Alter Link","targetCurve":[{"frequencyHz":55.0,"gainDb":3.0}],
             "metadata":{"genre":"uptempo-hardcore"}}
            """.trimIndent()

        val decoded = PresetShareCodec.decode(linkFor(old)).getOrThrow()

        assertEquals("Alter Link", decoded.name)
        assertEquals(PresetSourceScope.ALL, decoded.metadata.sourceScope)
    }

    @Test
    fun roundTrip_keepsPlayerAndExternalScopes() {
        val player = BuiltInPlayerPresets.Schranz.base
        val playerUseCase = BuiltInPlayerPresets.useCases(BuiltInPlayerPresets.Techno).last().preset
        val external =
            BuiltInGenreVariants.styles.values
                .first()
                .first()
                .preset

        listOf(player, playerUseCase, external).forEach { preset ->
            val decoded = PresetShareCodec.decode(PresetShareCodec.encode(preset)).getOrThrow()
            assertEquals(preset.id, preset.metadata.sourceScope, decoded.metadata.sourceScope)
            assertEquals(preset.id, preset.copy(id = decoded.id, metadata = decoded.metadata), decoded)
            assertFalse(decoded.metadata.builtIn)
        }
        assertEquals(PresetSourceScope.OWN_PLAYER, player.metadata.sourceScope)
        assertEquals(PresetSourceScope.EXTERNAL_APPS, external.metadata.sourceScope)
    }

    @Test
    fun scopeAllLink_staysAsShortAsBefore() {
        val link = PresetShareCodec.encode(custom)
        val payload = link.substringAfter("d=")
        val json =
            java.util.zip.Inflater(true).run {
                setInput(
                    java.util.Base64
                        .getUrlDecoder()
                        .decode(payload),
                )
                val out = ByteArray(64 * 1024)
                val n = inflate(out)
                end()
                String(out, 0, n, Charsets.UTF_8)
            }

        assertFalse(json, json.contains("sourceScope"))
    }
}
