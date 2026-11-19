package com.auracurve.app.remote.car

import com.auracurve.app.car.GeoFix
import com.auracurve.app.car.ParkedSpot
import com.auracurve.app.car.ParkingQuality
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class ParkingJsonTest {
    @Test
    fun `a spot in the contract's shape`() {
        val spot = ParkedSpot(GeoFix(48.137, 11.575, 7.6f, 0), ParkingQuality.GOOD, 0)
        assertEquals(
            """{"lat":48.137,"lon":11.575,"accuracyM":8,"time":"1970-01-01T00:00:00","quality":"GOOD"}""",
            ParkingController.toJson(spot, ZoneOffset.UTC).toString(),
        )
    }

    @Test
    fun `no fix keeps the time but no position`() {
        val json = ParkingController.toJson(ParkedSpot(null, ParkingQuality.NONE, 0), ZoneOffset.UTC).toString()
        assertEquals("""{"lat":null,"lon":null,"accuracyM":null,"time":"1970-01-01T00:00:00","quality":"NONE"}""", json)
    }
}
