package com.hardbasseq.eq.service

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hardbasseq.eq.HardBassEqApplication
import com.hardbasseq.eq.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Roadmap 06, P6-3 (CRIT-01 in the old review): starting the foreground service while the app is
 * in the background must never take the process down. On Android 12+ the platform refuses such a
 * start with ForegroundServiceStartNotAllowedException; HardBassEqApplication.startSessionService
 * has to swallow that, and the process has to stay alive afterwards.
 */
@RunWith(AndroidJUnit4::class)
class SessionServiceBackgroundStartTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun startSessionService_fromTheBackground_doesNotCrash() {
        val app = instrumentation.targetContext.applicationContext as HardBassEqApplication
        ActivityScenario.launch(MainActivity::class.java).use {
            // Home: the app has no visible window any more, which is what makes the start "background".
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            Thread.sleep(SETTLE_MS)

            app.startSessionService()
            app.startSessionService()
        }
        // Reaching this line means no exception left startSessionService and the test process lived on.
        assertNotNull(app.getSystemService(Context.ACTIVITY_SERVICE))
    }

    private companion object {
        const val SETTLE_MS = 1_500L
    }
}
