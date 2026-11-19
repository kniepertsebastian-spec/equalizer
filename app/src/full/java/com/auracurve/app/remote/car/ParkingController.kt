package com.auracurve.app.remote.car

import com.auracurve.app.car.GeoFix
import com.auracurve.app.car.IgnitionState
import com.auracurve.app.car.ParkedSpot
import com.auracurve.app.car.ParkingTracker
import com.auracurve.app.remote.IgnitionMonitor
import com.auracurve.app.remote.RemoteEvent
import com.auracurve.app.remote.RemoteServerSettings
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Module 11, F3: remembers where the car was switched off and tells the remote at once with the
 * `parked` event, because the driver's phone is still connected in that moment. GPS runs while
 * the car is on and for up to 30 s after it is switched off. Only the last spot is kept.
 */
@Singleton
class ParkingController
    @Inject
    constructor(
        private val settings: RemoteServerSettings,
        private val location: CarLocation,
        private val ignition: IgnitionMonitor,
    ) {
        suspend fun run(emit: (RemoteEvent) -> Unit): Unit =
            coroutineScope {
                val tracker = ParkingTracker()
                val gpsWanted = MutableStateFlow(false)
                var carOn = false

                suspend fun settled(spot: ParkedSpot?) {
                    if (spot == null) return
                    val json = toJson(spot)
                    settings.setParking(json.toString())
                    emit(RemoteEvent("parked", json))
                }

                launch {
                    gpsWanted.collectLatest { wanted ->
                        if (wanted) {
                            location.fixes().collect { f ->
                                settled(tracker.onFix(GeoFix(f.lat, f.lon, f.accuracyM, System.currentTimeMillis())))
                            }
                        }
                    }
                }
                launch {
                    while (true) {
                        settled(tracker.tick(System.currentTimeMillis()))
                        gpsWanted.value = carOn || tracker.waiting
                        delay(TICK_MS)
                    }
                }
                var previous: IgnitionState? = null
                ignition.state.filterNotNull().distinctUntilChanged().collect { ign ->
                    when {
                        ign.state == IgnitionState.ON -> tracker.onIgnitionOn()
                        // Only a real switch-off, not the first state after the server started.
                        previous == IgnitionState.ON -> settled(tracker.onIgnitionOff(ign.sinceMs))
                    }
                    previous = ign.state
                    carOn = ign.state == IgnitionState.ON
                    gpsWanted.value = carOn || tracker.waiting
                }
            }

        /** `GET /api/v0/car/parking`: `{spot}` with the last spot or null. */
        suspend fun current(): JsonObject =
            buildJsonObject {
                val stored = settings.parking.first()?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
                put("spot", stored ?: JsonNull)
            }

        /** `DELETE /api/v0/car/parking`. */
        suspend fun delete(): JsonObject {
            settings.setParking(null)
            return buildJsonObject { put("spot", JsonNull) }
        }

        companion object {
            private const val TICK_MS = 1_000L
            private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

            fun toJson(
                spot: ParkedSpot,
                zone: ZoneId = ZoneId.systemDefault(),
            ): JsonObject =
                buildJsonObject {
                    put("lat", spot.fix?.lat)
                    put("lon", spot.fix?.lon)
                    put("accuracyM", spot.fix?.accuracyM?.let { Math.round(it) })
                    put("time", ISO.format(Instant.ofEpochMilli(spot.parkedAtMs).atZone(zone)))
                    put("quality", spot.quality.name)
                }
        }
    }
