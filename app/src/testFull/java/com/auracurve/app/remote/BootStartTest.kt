package com.auracurve.app.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootStartTest {
    @Test
    fun `a boot starts the server only when asked for and only for the main user`() {
        assertTrue(BootStart.shouldStart(startAtBoot = true, mainUser = true))
        assertFalse(BootStart.shouldStart(startAtBoot = false, mainUser = true))
        assertFalse(BootStart.shouldStart(startAtBoot = true, mainUser = false))
    }
}
