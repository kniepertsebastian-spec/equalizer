package com.auracurve.app.remote

import com.auracurve.app.audio.AudioDeviceType
import com.auracurve.app.car.Ignition
import com.auracurve.app.car.IgnitionDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** The two hints the car's state is read from (module 11, F2). */
data class IgnitionHints(
    /** The phone charges: the car's USB power is switched with the ignition. */
    val power: Boolean,
    /** The radio is connected as the Bluetooth audio output. */
    val bluetooth: Boolean,
)

/**
 * Car on/off for `system/status` and the `ignition` event (module 11, F2), from charging and the
 * Bluetooth audio output. Runs while the remote server runs; null until the first hints arrived.
 */
@Singleton
class IgnitionMonitor
    @Inject
    constructor(
        private val statusSource: SystemStatusSource,
    ) {
        private val _state = MutableStateFlow<Ignition?>(null)
        val state: StateFlow<Ignition?> = _state.asStateFlow()

        suspend fun run() {
            statusSource
                .changes()
                .map { IgnitionHints(power = it.charging, bluetooth = isCarBluetooth(it.audioRouteType)) }
                .distinctUntilChanged()
                .toIgnition(System::currentTimeMillis)
                .collect { _state.value = it }
        }

        companion object {
            /**
             * A Bluetooth speaker or soundbar is a hint for "at home", not for "in the car", so only
             * plain Bluetooth and routes that look like a car radio count.
             */
            fun isCarBluetooth(routeType: String): Boolean =
                routeType == AudioDeviceType.BLUETOOTH.name || routeType == AudioDeviceType.CAR.name
        }
    }

/**
 * Feeds [IgnitionDetector] and wakes it up when a pending "off" is due, so the off arrives
 * without a further hint change. Equal states are not repeated.
 */
fun Flow<IgnitionHints>.toIgnition(
    clock: () -> Long,
    offDelayMs: Long = IgnitionDetector.DEFAULT_OFF_DELAY_MS,
): Flow<Ignition> =
    channelFlow {
        val detector = IgnitionDetector(clock(), offDelayMs)
        val lock = Mutex()
        var offTimer: Job? = null
        collect { hints ->
            offTimer?.cancel()
            val (now, offAt) = lock.withLock { detector.update(hints.power, hints.bluetooth, clock()) to detector.pendingOffAtMs }
            send(now)
            if (offAt == null) return@collect
            offTimer =
                launch {
                    delay((offAt - clock()).coerceAtLeast(0))
                    send(lock.withLock { detector.tick(clock()) })
                }
        }
    }.distinctUntilChanged()
