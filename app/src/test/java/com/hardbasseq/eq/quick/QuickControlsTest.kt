package com.hardbasseq.eq.quick

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickControlsTest {
    @Test
    fun send_isRefusedWithoutAConsumerSoTheSurfaceCanOpenTheApp() {
        val controls = QuickControls()

        assertFalse(controls.send(QuickCommand.TOGGLE_MASTER))
    }

    @Test
    fun send_reachesAnAttachedConsumer() =
        runTest {
            val controls = QuickControls()
            controls.consumerAttached()

            assertTrue(controls.send(QuickCommand.NEXT_PRESET))
            assertEquals(QuickCommand.NEXT_PRESET, controls.commands.first())
        }

    @Test
    fun detachingTheLastConsumerForgetsTheState() {
        val controls = QuickControls()
        controls.consumerAttached()
        controls.publish(QuickState(true, "Deep Rumble"))

        controls.consumerDetached()

        assertFalse(controls.hasConsumer)
        assertNull(controls.state.value)
    }

    @Test
    fun publish_notifiesSurfacesOnlyOnRealChanges() {
        val controls = QuickControls()
        var refreshes = 0
        controls.onSurfacesChanged = { refreshes++ }

        controls.publish(QuickState(true, "A"))
        controls.publish(QuickState(true, "A"))
        controls.publish(QuickState(false, "A"))

        assertEquals(2, refreshes)
    }
}
