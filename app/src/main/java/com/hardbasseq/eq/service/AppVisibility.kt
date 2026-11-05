package com.hardbasseq.eq.service

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether any screen of this app (the equalizer or the built-in player) is currently visible.
 * [AudioSessionForegroundService] uses it so it never closes itself while someone is looking
 * at the app. Register [callbacks] once, from the Application.
 */
@Singleton
class AppVisibility
    @Inject
    constructor() {
        private var startedActivities = 0
        private val _visible = MutableStateFlow(false)
        val visible: StateFlow<Boolean> = _visible.asStateFlow()

        fun activityStarted() {
            startedActivities++
            _visible.value = true
        }

        fun activityStopped() {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
            _visible.value = startedActivities > 0
        }

        // All seven callbacks are spelled out: on API 28 (minSdk) the interface has no default methods.
        val callbacks: Application.ActivityLifecycleCallbacks =
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) = activityStarted()

                override fun onActivityStopped(activity: Activity) = activityStopped()

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
    }
