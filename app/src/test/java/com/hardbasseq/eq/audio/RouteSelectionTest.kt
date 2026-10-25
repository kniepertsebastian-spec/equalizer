package com.hardbasseq.eq.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteSelectionTest {
    private val speaker = RouteCandidate(RouteSelection.TYPE_BUILTIN_SPEAKER, null)
    private val wired = RouteCandidate(RouteSelection.TYPE_WIRED_HEADPHONES, null)
    private val usb = RouteCandidate(RouteSelection.TYPE_USB_HEADSET, "DAC")
    private val a2dp = RouteCandidate(RouteSelection.TYPE_BLUETOOTH_A2DP, "Buds")
    private val sco = RouteCandidate(RouteSelection.TYPE_BLUETOOTH_SCO, "Car")
    private val bleSpeaker = RouteCandidate(RouteSelection.TYPE_BLE_SPEAKER, "Box")
    private val bleHeadset = RouteCandidate(RouteSelection.TYPE_BLE_HEADSET, "Buds LE")

    @Test
    fun `media device from attributes wins over list order`() {
        val picked = RouteSelection.pick(listOf(a2dp, wired, usb), RouteSelection.TYPE_USB_HEADSET)
        assertEquals(usb, picked)
    }

    @Test
    fun `a2dp is preferred over wired when no media device is reported`() {
        assertEquals(a2dp, RouteSelection.pick(listOf(speaker, wired, a2dp), null))
    }

    @Test
    fun `sco only does not count as a route outside a call`() {
        assertNull(RouteSelection.pick(listOf(speaker, sco), null))
        assertEquals(sco, RouteSelection.pick(listOf(speaker, sco), null, inCommunication = true))
    }

    @Test
    fun `ble speaker is a speaker without a name match`() {
        val picked = RouteSelection.pick(listOf(speaker, bleSpeaker), null)
        assertEquals(bleSpeaker, picked)
        assertEquals(true, RouteSelection.isBluetooth(picked!!.type))
    }

    @Test
    fun `ble headset is treated as bluetooth so the name heuristic applies`() {
        val picked = RouteSelection.pick(listOf(speaker, bleHeadset), null)
        assertEquals(bleHeadset, picked)
        assertEquals(true, RouteSelection.isBluetooth(picked!!.type))
    }

    @Test
    fun `no external device falls back to the built-in speaker`() {
        assertNull(RouteSelection.pick(listOf(speaker), null))
        assertNull(RouteSelection.pick(emptyList(), null))
    }
}
