package com.hardbasseq.eq.preset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresetCycleTest {
    private val ids = listOf("a", "b", "c")

    @Test
    fun nextId_movesOnAndWrapsAround() {
        assertEquals("b", PresetCycle.nextId(ids, "a"))
        assertEquals("c", PresetCycle.nextId(ids, "b"))
        assertEquals("a", PresetCycle.nextId(ids, "c"))
    }

    @Test
    fun nextId_startsAtTheFirstWhenCurrentIsUnknown() {
        assertEquals("a", PresetCycle.nextId(ids, "deleted"))
    }

    @Test
    fun nextId_isNullWithoutPresets() {
        assertNull(PresetCycle.nextId(emptyList(), "a"))
    }

    @Test
    fun nextId_singlePresetStaysPut() {
        assertEquals("a", PresetCycle.nextId(listOf("a"), "a"))
    }
}
