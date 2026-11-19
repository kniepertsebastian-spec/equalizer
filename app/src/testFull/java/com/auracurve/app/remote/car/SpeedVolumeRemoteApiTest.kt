package com.auracurve.app.remote.car

import com.auracurve.app.car.SpeedVolumeSettings
import com.auracurve.app.remote.ApiError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedVolumeRemoteApiTest {
    private val current = SpeedVolumeSettings()

    private fun merge(json: String) = SpeedVolumeRemoteApi.merge(current, Json.parseToJsonElement(json).jsonObject)

    private fun code(json: String): String =
        try {
            merge(json)
            "none"
        } catch (e: ApiError) {
            e.code
        }

    @Test
    fun `only the given fields change`() {
        val next = merge("""{"enabled":true,"maxBoostDb":5}""")
        assertTrue(next.enabled)
        assertEquals(5f, next.maxBoostDb, 0f)
        assertEquals(current.startSpeedKmh, next.startSpeedKmh, 0f)
    }

    @Test
    fun `wrong types and values outside the limits are refused`() {
        assertEquals("BAD_REQUEST", code("""{"enabled":"yes"}"""))
        assertEquals("BAD_REQUEST", code("""{"maxBoostDb":"5"}"""))
        assertEquals("OUT_OF_RANGE", code("""{"maxBoostDb":9}"""))
        assertEquals("OUT_OF_RANGE", code("""{"startSpeedKmh":100,"fullSpeedKmh":110}"""))
        assertEquals("none", code("""{"startSpeedKmh":50,"fullSpeedKmh":120}"""))
    }
}
