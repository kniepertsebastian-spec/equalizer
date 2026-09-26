package com.hardbasseq.eq.autoeq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInAutoEqCatalogTest {
    @Test
    fun everyEntryHasANonEmptyCurve() {
        for (entry in BuiltInAutoEqCatalog.entries) {
            assertTrue("${entry.id} has an empty curve", entry.profile.curve.isNotEmpty())
        }
    }

    @Test
    fun everyEntryIdIsUnique() {
        val ids = BuiltInAutoEqCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyEntryCurveIsWithinAutoEqParserBounds() {
        for (entry in BuiltInAutoEqCatalog.entries) {
            for (point in entry.profile.curve) {
                assertTrue("${entry.id} has an out-of-range gain at ${point.frequencyHz} Hz", point.gainDb in -24f..24f)
                assertTrue("${entry.id} has a non-positive frequency", point.frequencyHz > 0f)
            }
        }
    }

    @Test
    fun catalogIsNotEmpty() {
        assertTrue(BuiltInAutoEqCatalog.entries.isNotEmpty())
    }
}
