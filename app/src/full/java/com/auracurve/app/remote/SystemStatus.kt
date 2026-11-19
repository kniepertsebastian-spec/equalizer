package com.auracurve.app.remote

import com.auracurve.app.car.Ignition
import com.auracurve.app.car.Protection
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * `GET /api/v0/system/status` (API-VERTRAG v0, 3.1). In module 04a the user is always the main
 * one, the mode always CAR and the source always the built-in player (source switching is 04c).
 */
data class SystemStatus(
    val batteryPercent: Int?,
    val charging: Boolean,
    val thermalStatus: String,
    val audioRouteType: String,
    val audioRouteName: String,
    val activeUser: String = "MAIN",
    val activeSource: String = "PLAYER",
    val mode: String = "CAR",
    /** Module 11, F2: car on/off; null while not known yet. */
    val ignition: Ignition? = null,
    /** Battery temperature in °C (module 11, F1); null if unknown. */
    val batteryTempC: Float? = null,
    /** PowerManager.THERMAL_STATUS_* as a number, for the F1 rules; [thermalStatus] is its name. */
    val thermalCode: Int = 0,
    /** Module 11, F1: heat and battery protection; null while not known yet. */
    val protection: Protection? = null,
) {
    fun toJson(): JsonObject =
        buildJsonObject {
            putJsonObject("battery") {
                put("percent", batteryPercent)
                put("charging", charging)
                put("temperatureC", batteryTempC)
            }
            putJsonObject("thermal") { put("status", thermalStatus) }
            putJsonObject("audioRoute") {
                put("type", audioRouteType)
                put("name", audioRouteName)
            }
            put("activeUser", activeUser)
            put("activeSource", activeSource)
            put("mode", mode)
            if (ignition == null) put("ignition", null as String?) else put("ignition", ignitionJson(ignition))
            if (protection == null) put("protection", null as String?) else put("protection", protectionJson(protection))
        }

    companion object {
        /** `ignition` in `system/status` and the payload of the `ignition` event. */
        fun ignitionJson(
            ignition: Ignition,
            zone: ZoneId = ZoneId.systemDefault(),
        ): JsonObject =
            buildJsonObject {
                put("state", ignition.state.name)
                put("since", ISO.format(Instant.ofEpochMilli(ignition.sinceMs).atZone(zone)))
                put("source", ignition.source.name)
            }

        /** `protection` in `system/status` and the payload of the `protection` event. */
        fun protectionJson(protection: Protection): JsonObject =
            buildJsonObject {
                put("level", protection.level.name)
                put("standby", protection.standby)
                put("pauseBackground", protection.pauseBackground)
            }

        private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        /** PowerManager.THERMAL_STATUS_* (0 = NONE … 6 = SHUTDOWN) as the contract's names. */
        fun thermalName(status: Int): String = THERMAL_NAMES.getOrElse(status) { "NONE" }

        private val THERMAL_NAMES = listOf("NONE", "LIGHT", "MODERATE", "SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN")
    }
}

/** One message on the WebSocket, before the per-connection `seq` is added. */
data class RemoteEvent(
    val type: String,
    val data: JsonObject,
)
