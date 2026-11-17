package com.auracurve.app.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.auracurve.app.audio.AudioRouteRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transform
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/** Battery, temperature and audio output for `system/status` and `system.changed`. */
@Singleton
class SystemStatusSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val routes: AudioRouteRepository,
    ) {
        private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        /** Now, read directly (the battery from its sticky broadcast, nothing is registered). */
        fun current(): SystemStatus {
            val battery = ContextCompat.registerReceiver(context, null, batteryFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
            return status(battery, thermalNow())
        }

        /** Changes as they happen; equal states are not repeated. */
        fun changes(): Flow<SystemStatus> =
            combine(batteryIntents(), thermalStatuses(), routes.activeRoute) { battery, thermal, _ -> status(battery, thermal) }
                .distinctUntilChanged()

        private fun status(
            battery: Intent?,
            thermal: Int,
        ): SystemStatus {
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val route = routes.activeRoute.value
            return SystemStatus(
                batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null,
                charging = plugged != 0,
                thermalStatus = SystemStatus.thermalName(thermal),
                audioRouteType = route.type.name,
                audioRouteName = route.name,
            )
        }

        private fun thermalNow(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) powerManager.currentThermalStatus else 0

        private fun batteryIntents(): Flow<Intent?> =
            callbackFlow {
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            context: Context,
                            intent: Intent,
                        ) {
                            trySend(intent)
                        }
                    }
                // Sticky: the current state arrives right away.
                ContextCompat.registerReceiver(context, receiver, batteryFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
                awaitClose { context.unregisterReceiver(receiver) }
            }

        private fun thermalStatuses(): Flow<Int> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return flowOf(0)
            return callbackFlow {
                val listener = PowerManager.OnThermalStatusChangedListener { trySend(it) }
                // Calls back with the current status right away.
                powerManager.addThermalStatusListener(context.mainExecutor, listener)
                awaitClose { powerManager.removeThermalStatusListener(listener) }
            }
        }

        private val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    }

/**
 * At most one value per [period], and always the latest one: the first passes at once, later ones
 * wait until the period is over (module 04a: `system.changed` at most every 5 s).
 */
fun <T> Flow<T>.throttleLatest(period: Duration): Flow<T> =
    conflate().transform {
        emit(it)
        delay(period)
    }
