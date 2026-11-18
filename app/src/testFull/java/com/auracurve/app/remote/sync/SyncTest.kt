package com.auracurve.app.remote.sync

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneOffset

// Module 08: when the car phone was last online and what it last brought up to date.
class SyncTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_800_000_000_000L
    private val day = 24 * 60 * 60_000L

    private fun times(file: File = File(tmp.root, "sync.json")) = SyncTimes(file, clock = { now })

    @Test
    fun `online times are kept across a restart, written at most once a minute`() {
        val file = File(tmp.root, "keep.json")
        val times = times(file)
        assertNull(times.current().lastOnlineMs)
        times.markOnline()
        val first = now
        now += 30_000
        times.markOnline()
        assertEquals(first, times.current().lastOnlineMs)
        now += 60_000
        times.markOnline()
        times.markWebChecked()
        val again = times(file).current()
        assertEquals(now, again.lastOnlineMs)
        assertEquals(now, again.webCheckedMs)
    }

    @Test
    fun `the status says how long the phone has been offline and warns after three weeks`() =
        runTest {
            val times = times()
            var online = true
            val api =
                SyncRemoteApi(
                    times = times,
                    online = { online },
                    librarySyncedAtMs = { now - day },
                    feedUpdatedAtMs = { null },
                    webVersion = { "0.6.0" },
                    clock = { now },
                    zone = ZoneOffset.UTC,
                )
            val fresh = api.status()
            assertEquals("true", fresh["online"]!!.jsonPrimitive.content)
            assertEquals(JsonNull, fresh["offlineDays"])
            assertEquals(JsonNull, fresh["feedUpdatedAt"])
            assertEquals("0.6.0", fresh["webVersion"]!!.jsonPrimitive.content)
            assertEquals("2027-01-14T08:00:00", fresh["librarySyncedAt"]!!.jsonPrimitive.content)

            online = false
            now += 3 * day
            val car = api.status()
            assertEquals(3, car["offlineDays"]!!.jsonPrimitive.int)
            assertEquals(JsonNull, car["warning"])

            now += 18 * day
            assertEquals("OFFLINE_LONG", api.status()["warning"]!!.jsonPrimitive.content)
        }
}
