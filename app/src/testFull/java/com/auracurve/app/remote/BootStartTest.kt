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

    @Test
    fun `a boot and an update of the app start it, nothing else`() {
        assertTrue(BootStart.handles("android.intent.action.BOOT_COMPLETED"))
        assertTrue(BootStart.handles("android.intent.action.MY_PACKAGE_REPLACED"))
        assertFalse(BootStart.handles("android.intent.action.PACKAGE_REPLACED"))
        assertFalse(BootStart.handles(null))
    }
}
