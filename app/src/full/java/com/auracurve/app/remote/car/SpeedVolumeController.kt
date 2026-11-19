package com.auracurve.app.remote.car

import com.auracurve.app.car.IgnitionState
import com.auracurve.app.car.SpeedVolumeRegulator
import com.auracurve.app.eq.EqController
import com.auracurve.app.remote.IgnitionMonitor
import com.auracurve.app.remote.RemoteServerSettings
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Module 11, F6: drives [SpeedVolumeRegulator] once a second with the GPS speed and hands its gain
 * to the EQ chain. GPS only runs while the function is on and the car is on (F2). Runs while the
 * remote server runs, on the main thread.
 */
@Singleton
class SpeedVolumeController
    @Inject
    constructor(
        private val settings: RemoteServerSettings,
        private val location: CarLocation,
        private val ignition: IgnitionMonitor,
        private val eq: EqController,
    ) {
        data class Live(
            val gainDb: Float = 0f,
            val speedKmh: Float? = null,
        )

        private val _live = MutableStateFlow(Live())
        val live: StateFlow<Live> = _live.asStateFlow()

        suspend fun run(nothingPlaying: suspend () -> Boolean) =
            coroutineScope {
                val regulator = SpeedVolumeRegulator()
                // The newest fix and when it arrived (the fix's own time is GPS time).
                var latest: Pair<Long, CarFix>? = null
                val config = settings.speedVolume.stateIn(this, SharingStarted.Eagerly, null)
                val carOn = ignition.state.stateIn(this, SharingStarted.Eagerly, null)
                launch {
                    combine(settings.speedVolume, ignition.state) { s, ign -> s.enabled && ign?.state == IgnitionState.ON }
                        .distinctUntilChanged()
                        .collectLatest { active ->
                            latest = null
                            if (active) location.fixes().collect { latest = System.currentTimeMillis() to it }
                        }
                }
                try {
                    while (true) {
                        val now = System.currentTimeMillis()
                        val s = config.value
                        if (s != null) {
                            val active = s.enabled && carOn.value?.state == IgnitionState.ON
                            val speed = latest?.takeIf { now - it.first < STALE_MS }?.second?.speedKmh
                            val gain =
                                regulator.update(
                                    settings = s.copy(enabled = active),
                                    speedKmh = speed,
                                    nowMs = now,
                                    frozen = active && nothingPlaying(),
                                )
                            eq.setSpeedGainDb(gain)
                            _live.value = Live(gain, speed)
                        }
                        delay(TICK_MS)
                    }
                } finally {
                    // Server stops: leave the chain as it was.
                    eq.setSpeedGainDb(0f)
                    _live.value = Live()
                }
            }

        companion object {
            const val TICK_MS = 1_000L

            /** A fix older than this counts as "no fix" (tunnel): the regulator holds the level. */
            const val STALE_MS = 5_000L
        }
    }
