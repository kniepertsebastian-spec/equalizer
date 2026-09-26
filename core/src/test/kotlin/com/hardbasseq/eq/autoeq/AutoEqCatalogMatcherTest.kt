package com.hardbasseq.eq.autoeq

import com.hardbasseq.eq.correction.CorrectionProfile
import com.hardbasseq.eq.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoEqCatalogMatcherTest {
    private val testCatalog =
        listOf(
            entry("sony_wh1000xm4", "Sony WH-1000XM4", listOf("WH-1000XM4", "WH1000XM4")),
            entry("sennheiser_hd599", "Sennheiser HD 599", listOf("HD 599", "HD599")),
        )

    @Test
    fun exactDisplayNameMatches() {
        val match = AutoEqCatalogMatcher.findBestMatch("Sennheiser HD 599", testCatalog)
        assertEquals("sennheiser_hd599", match?.id)
    }

    @Test
    fun bluetoothAdvertisedAliasMatches() {
        // Many devices advertise just the model code over Bluetooth, not the
        // full "Brand + model" display name.
        val match = AutoEqCatalogMatcher.findBestMatch("WH-1000XM4", testCatalog)
        assertEquals("sony_wh1000xm4", match?.id)
    }

    @Test
    fun caseAndPunctuationInsensitive() {
        val match = AutoEqCatalogMatcher.findBestMatch("wh1000xm4", testCatalog)
        assertEquals("sony_wh1000xm4", match?.id)
    }

    @Test
    fun deviceNameContainingModelStillMatches() {
        // Some stacks report a longer string around the actual model name.
        val match = AutoEqCatalogMatcher.findBestMatch("Sony WH-1000XM4 Stereo", testCatalog)
        assertEquals("sony_wh1000xm4", match?.id)
    }

    @Test
    fun typoStillMatchesViaEditDistance() {
        // Normalization already strips spaces/hyphens, so a genuine edit-
        // distance test needs an actual character difference, not just
        // different spacing - "Senheiser" (missing one 'n') isn't a substring
        // of "Sennheiser" either way, so this only passes via Levenshtein.
        val match = AutoEqCatalogMatcher.findBestMatch("Senheiser HD 599", testCatalog)
        assertEquals("sennheiser_hd599", match?.id)
    }

    @Test
    fun unrelatedNameReturnsNoMatch() {
        val match = AutoEqCatalogMatcher.findBestMatch("Generic Bluetooth Speaker XR200", testCatalog)
        assertNull(match)
    }

    @Test
    fun blankNameReturnsNoMatch() {
        assertNull(AutoEqCatalogMatcher.findBestMatch("", testCatalog))
        assertNull(AutoEqCatalogMatcher.findBestMatch("   ", testCatalog))
    }

    @Test
    fun emptyCatalogReturnsNoMatch() {
        assertNull(AutoEqCatalogMatcher.findBestMatch("Sony WH-1000XM4", emptyList()))
    }

    @Test
    fun picksTheCloserOfTwoCandidatesWhenBothAreSimilar() {
        val ambiguousCatalog =
            listOf(
                entry("model_a", "Foo Headphone A", emptyList()),
                entry("model_b", "Foo Headphone B", emptyList()),
            )
        val match = AutoEqCatalogMatcher.findBestMatch("Foo Headphone A", ambiguousCatalog)
        assertEquals("model_a", match?.id)
    }

    @Test
    fun realCatalogFindsKnownModelByAlias() {
        val match = AutoEqCatalogMatcher.findBestMatch("Galaxy Buds2 Pro")
        assertEquals("samsung_galaxy_buds2_pro", match?.id)
    }

    private fun entry(
        id: String,
        displayName: String,
        aliases: List<String>,
    ): AutoEqCatalogEntry =
        AutoEqCatalogEntry(
            id = id,
            displayName = displayName,
            aliases = aliases,
            profile =
                CorrectionProfile(
                    id = id,
                    name = displayName,
                    curve = listOf(TargetPoint(1000f, 0f)),
                ),
        )
}
