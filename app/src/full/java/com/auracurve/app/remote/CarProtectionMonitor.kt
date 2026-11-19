package com.auracurve.app.remote

import com.auracurve.app.car.HeatProtection
import com.auracurve.app.car.Protection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

/**
 * Heat and battery protection (module 11, F1) from the system status and the ignition (F2).
 * The one place that feed, library sync, web updates and playback ask. Runs while the remote
 * server runs; [Protection.NONE] before that, so nothing is paused by mistake.
 */
@Singleton
class CarProtectionMonitor
    @Inject
    constructor(
        private val statusSource: SystemStatusSource,
        private val ignitionMonitor: IgnitionMonitor,
    ) {
        private val _state = MutableStateFlow(Protection.NONE)
        val state: StateFlow<Protection> = _state.asStateFlow()

        suspend fun run() {
            combine(statusSource.changes(), ignitionMonitor.state, minuteTicks()) { status, ignition, now ->
                HeatProtection.protection(
                    thermalStatus = status.thermalCode,
                    batteryTempC = status.batteryTempC,
                    batteryPercent = status.batteryPercent,
                    ignition = ignition,
                    nowMs = now,
                )
            }.distinctUntilChanged()
                .collect { _state.value = it }
        }

        /** The standby rule depends on time passing, not only on changes. */
        private fun minuteTicks(): Flow<Long> =
            flow {
                while (true) {
                    emit(System.currentTimeMillis())
                    delay(1.minutes)
                }
            }
    }
