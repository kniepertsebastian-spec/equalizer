package com.hardbasseq.eq.ui.main

import com.hardbasseq.eq.audio.AudioDeviceType
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.context.SoundContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteContextResolverTest {
    private val speaker = AudioRoute(AudioDeviceType.SPEAKER, "Speaker", "speaker")
    private val carRoute = AudioRoute(AudioDeviceType.CAR, "Car", "car")
    private val headset = AudioRoute(AudioDeviceType.BLUETOOTH, "Headset", "bt1")

    @Test
    fun resolve_savedChoiceWinsOverDetection() {
        val decision =
            RouteContextResolver.resolve(
                carRoute,
                carConnected = true,
                storedMode = "BLUETOOTH_SPEAKER",
                legacyContext = null,
                restoredContext = null,
            )

        assertEquals(SoundContext.BLUETOOTH_SPEAKER, decision.mode)
        assertFalse(decision.automatic)
    }

    @Test
    fun resolve_explicitOffStaysOffEvenInTheCar() {
        val decision =
            RouteContextResolver.resolve(
                carRoute,
                carConnected = true,
                storedMode = CONTEXT_MODE_NONE,
                legacyContext = null,
                restoredContext = null,
            )

        assertNull(decision.mode)
        assertFalse(decision.automatic)
    }

    @Test
    fun resolve_withoutAChoiceDetectionDecidesAndIsMarkedAutomatic() {
        val decision =
            RouteContextResolver.resolve(
                carRoute,
                carConnected = false,
                storedMode = null,
                legacyContext = null,
                restoredContext = null,
            )

        assertEquals(SoundContext.CAR, decision.mode)
        assertTrue(decision.automatic)
    }

    @Test
    fun resolve_androidAutoMeansCarOnAnyRoute() {
        val decision =
            RouteContextResolver.resolve(
                speaker,
                carConnected = true,
                storedMode = null,
                legacyContext = null,
                restoredContext = null,
            )

        assertEquals(SoundContext.CAR, decision.mode)
    }

    @Test
    fun resolve_legacyAndRestoredBeatDetectionInThatOrder() {
        val legacy = RouteContextResolver.resolve(carRoute, false, null, SoundContext.BLUETOOTH_SPEAKER, SoundContext.CAR)
        val restored = RouteContextResolver.resolve(speaker, false, null, null, SoundContext.CAR)

        assertEquals(SoundContext.BLUETOOTH_SPEAKER, legacy.mode)
        assertFalse(legacy.automatic)
        assertEquals(SoundContext.CAR, restored.mode)
        assertFalse(restored.automatic)
    }

    @Test
    fun resolve_unknownSavedNameFallsBackToDetection() {
        val decision = RouteContextResolver.resolve(carRoute, false, "RENAMED_LONG_AGO", null, null)

        assertEquals(SoundContext.CAR, decision.mode)
    }

    @Test
    fun resolve_plainSpeakerRouteHasNoMode() {
        val decision = RouteContextResolver.resolve(speaker, false, null, null, null)

        assertNull(decision.mode)
        assertFalse(decision.automatic)
    }

    @Test
    fun shouldAskDeviceKind_onlyForUnclassifiedBluetooth() {
        assertTrue(RouteContextResolver.shouldAskDeviceKind(headset, null, null, emptySet(), carConnected = false))
        assertFalse(RouteContextResolver.shouldAskDeviceKind(speaker, null, null, emptySet(), false))
        assertFalse(RouteContextResolver.shouldAskDeviceKind(headset, "CAR", null, emptySet(), false))
        assertFalse(RouteContextResolver.shouldAskDeviceKind(headset, null, SoundContext.CAR, emptySet(), false))
        assertFalse(RouteContextResolver.shouldAskDeviceKind(headset, null, null, setOf("bt1"), false))
        assertFalse(RouteContextResolver.shouldAskDeviceKind(headset, null, null, emptySet(), carConnected = true))
    }
}
