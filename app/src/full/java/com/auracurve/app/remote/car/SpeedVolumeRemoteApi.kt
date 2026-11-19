package com.auracurve.app.remote.car

import com.auracurve.app.car.SpeedVolumeSettings
import com.auracurve.app.remote.EqRemoteApi
import com.auracurve.app.remote.RemoteServerSettings
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * `GET/PUT /api/v0/settings/autoVolume` (module 11, F6). PUT takes any of `enabled`, `maxBoostDb`,
 * `startSpeedKmh`, `fullSpeedKmh`; values outside the limits answer 400 OUT_OF_RANGE.
 */
class SpeedVolumeRemoteApi(
    private val settings: RemoteServerSettings,
    private val live: () -> SpeedVolumeController.Live,
    private val hasLocation: () -> Boolean,
) {
    suspend fun get(): JsonObject = toJson(settings.speedVolume.first())

    suspend fun put(body: JsonObject): JsonObject {
        val current = settings.speedVolume.first()
        val next = merge(current, body)
        return toJson(settings.setSpeedVolume(next))
    }

    private fun toJson(s: SpeedVolumeSettings): JsonObject {
        val now = live()
        return buildJsonObject {
            put("enabled", s.enabled)
            put("maxBoostDb", s.maxBoostDb)
            put("startSpeedKmh", s.startSpeedKmh)
            put("fullSpeedKmh", s.fullSpeedKmh)
            put("gainDb", now.gainDb)
            put("speedKmh", now.speedKmh)
            put("locationPermission", hasLocation())
        }
    }

    companion object {
        /** The body over [current]; throws 400 for a wrong type or a value outside the limits. */
        fun merge(
            current: SpeedVolumeSettings,
            body: JsonObject,
        ): SpeedVolumeSettings {
            fun num(
                name: String,
                min: Float,
                max: Float,
            ): Float? {
                val raw = body[name] ?: return null
                val v =
                    (raw as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.toFloat()
                        ?: throw EqRemoteApi.badRequest("$name ist keine Zahl")
                if (v < min || v > max) throw EqRemoteApi.outOfRange("$name außerhalb der Grenzen ($min bis $max)")
                return v
            }
            val enabled =
                body["enabled"]?.let {
                    (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.booleanOrNull
                        ?: throw EqRemoteApi.badRequest("enabled fehlt")
                }
            val next =
                SpeedVolumeSettings(
                    enabled = enabled ?: current.enabled,
                    maxBoostDb = num("maxBoostDb", 0f, SpeedVolumeSettings.MAX_BOOST_DB) ?: current.maxBoostDb,
                    startSpeedKmh = num("startSpeedKmh", 0f, SpeedVolumeSettings.MAX_START_KMH) ?: current.startSpeedKmh,
                    fullSpeedKmh = num("fullSpeedKmh", 0f, SpeedVolumeSettings.MAX_FULL_KMH) ?: current.fullSpeedKmh,
                )
            if (next.fullSpeedKmh < next.startSpeedKmh + SpeedVolumeSettings.MIN_SPAN_KMH) {
                throw EqRemoteApi.outOfRange(
                    "fullSpeedKmh muss mindestens ${SpeedVolumeSettings.MIN_SPAN_KMH.toInt()} km/h über startSpeedKmh liegen",
                )
            }
            return next
        }
    }
}
