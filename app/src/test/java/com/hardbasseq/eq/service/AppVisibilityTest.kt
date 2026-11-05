package com.hardbasseq.eq.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVisibilityTest {
    @Test
    fun `is not visible before any screen started`() {
        assertFalse(AppVisibility().visible.value)
    }

    @Test
    fun `is visible while at least one screen is started`() {
        val visibility = AppVisibility()

        visibility.activityStarted()
        visibility.activityStarted()
        visibility.activityStopped()

        assertTrue(visibility.visible.value)
    }

    @Test
    fun `is hidden again once every screen stopped`() {
        val visibility = AppVisibility()
        visibility.activityStarted()

        visibility.activityStopped()

        assertFalse(visibility.visible.value)
    }

    @Test
    fun `an extra stop does not make the next start look like a hidden app`() {
        val visibility = AppVisibility()

        visibility.activityStopped()
        visibility.activityStarted()

        assertTrue(visibility.visible.value)
    }
}
