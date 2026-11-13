package com.hardbasseq.eq.ui.player.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class SheetSettleTest {
    @Test
    fun slowRelease_goesToTheNearerEnd() {
        assertEquals(1f, SheetSettle.target(position = 0.6f, velocity = 0f), 0f)
        assertEquals(0f, SheetSettle.target(position = 0.4f, velocity = 0.5f), 0f)
        assertEquals(1f, SheetSettle.target(position = 0.5f, velocity = -0.5f), 0f)
    }

    @Test
    fun flick_winsOverThePosition() {
        assertEquals(1f, SheetSettle.target(position = 0.1f, velocity = SheetSettle.FLICK_VELOCITY), 0f)
        assertEquals(0f, SheetSettle.target(position = 0.9f, velocity = -SheetSettle.FLICK_VELOCITY), 0f)
    }
}
