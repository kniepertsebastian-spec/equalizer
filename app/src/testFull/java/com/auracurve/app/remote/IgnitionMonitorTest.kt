package com.auracurve.app.remote

import com.auracurve.app.car.Ignition
import com.auracurve.app.car.IgnitionSource
import com.auracurve.app.car.IgnitionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class IgnitionMonitorTest {
    @Test
    fun `the off arrives on its own after the delay, dated to when the hints went away`() =
        runTest {
            val hints = MutableSharedFlow<IgnitionHints>()
            val seen = mutableListOf<Ignition>()
            val job = launch { hints.toIgnition(clock = { testScheduler.currentTime }, offDelayMs = 15_000).collect { seen += it } }
            runCurrent()

            hints.emit(IgnitionHints(power = true, bluetooth = false))
            runCurrent()
            advanceTimeBy(60_000)
            hints.emit(IgnitionHints(power = false, bluetooth = false))
            runCurrent()
            assertEquals(IgnitionState.ON, seen.last().state)

            advanceTimeBy(15_001)
            runCurrent()
            assertEquals(IgnitionState.OFF, seen.last().state)
            assertEquals(60_000L, seen.last().sinceMs)
            job.cancel()
        }

    @Test
    fun `a hint back within the delay cancels the pending off`() =
        runTest {
            val hints = MutableSharedFlow<IgnitionHints>()
            val seen = mutableListOf<Ignition>()
            val job = launch { hints.toIgnition(clock = { testScheduler.currentTime }, offDelayMs = 15_000).collect { seen += it } }
            runCurrent()

            hints.emit(IgnitionHints(power = true, bluetooth = true))
            runCurrent()
            hints.emit(IgnitionHints(power = false, bluetooth = false)) // cranking
            runCurrent()
            advanceTimeBy(3_000)
            hints.emit(IgnitionHints(power = false, bluetooth = true))
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()

            assertTrue(seen.none { it.state == IgnitionState.OFF })
            assertEquals(IgnitionSource.BLUETOOTH, seen.last().source)
            job.cancel()
        }

    @Test
    fun `speakers and soundbars at home are not a car hint`() {
        assertTrue(IgnitionMonitor.isCarBluetooth("BLUETOOTH"))
        assertTrue(IgnitionMonitor.isCarBluetooth("CAR"))
        assertFalse(IgnitionMonitor.isCarBluetooth("BLUETOOTH_SPEAKER"))
        assertFalse(IgnitionMonitor.isCarBluetooth("SOUNDBAR"))
        assertFalse(IgnitionMonitor.isCarBluetooth("SPEAKER"))
    }

    @Test
    fun `the ignition json names state, local time and source`() {
        val json = SystemStatus.ignitionJson(Ignition(IgnitionState.ON, 0L, IgnitionSource.POWER), ZoneOffset.UTC)
        assertEquals("{\"state\":\"ON\",\"since\":\"1970-01-01T00:00:00\",\"source\":\"POWER\"}", json.toString())
    }
}
