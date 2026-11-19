package com.auracurve.app.quick

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class QuickWidgetCommandReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the widget's own intent carries its command to the private receiver`() {
        val intent = QuickWidgetCommandReceiver.intent(context, QuickCommand.NEXT_PRESET)
        assertEquals(QuickWidgetCommandReceiver::class.java.name, intent.component?.className)
        assertEquals(QuickCommand.NEXT_PRESET, QuickWidgetCommandReceiver.commandOf(intent))
    }

    @Test
    fun `other actions and unknown commands are ignored`() {
        assertNull(QuickWidgetCommandReceiver.commandOf(Intent("x").putExtra("command", "TOGGLE_MASTER")))
        assertNull(
            QuickWidgetCommandReceiver.commandOf(
                QuickWidgetCommandReceiver.intent(context, QuickCommand.TOGGLE_MASTER).putExtra("command", "WIPE"),
            ),
        )
        assertNull(QuickWidgetCommandReceiver.commandOf(null))
    }
}
