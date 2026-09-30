package com.hardbasseq.eq.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundContextClassifierTest {
    @Test
    fun `carmaker and infotainment names are classified as car`() {
        listOf(
            "BMW 12345",
            "Audi MMI",
            "MY VW",
            "Mercedes-Benz MBUX",
            "Uconnect 4C",
            "Android Auto",
            "CarPlay",
            "Car Kit",
            "Freisprecheinrichtung",
            "Opel Radio",
        ).forEach { name ->
            assertEquals(name, SoundContext.CAR, SoundContextClassifier.classifyBluetoothName(name))
        }
    }

    @Test
    fun `speaker names are classified as bluetooth speaker`() {
        listOf("JBL Flip 6", "JBL Charge 5", "Bluetooth Speaker XR200", "Anker Soundcore Boom 2", "UE MEGABOOM 3", "Partybox 310")
            .forEach { name ->
                assertEquals(name, SoundContext.BLUETOOTH_SPEAKER, SoundContextClassifier.classifyBluetoothName(name))
            }
    }

    @Test
    fun `headphones and unknown names have no context`() {
        listOf("Sony WH-1000XM4", "AirPods Pro", "Galaxy Buds2", "Bluetooth Device", "").forEach { name ->
            assertNull(name, SoundContextClassifier.classifyBluetoothName(name))
        }
    }

    @Test
    fun `short brand names do not match inside other words`() {
        // "vw" inside "Vwxyz", "seat" inside "Seattle", "kia" inside "Kiara" must not count.
        assertNull(SoundContextClassifier.classifyBluetoothName("Vwxyz Buds"))
        assertNull(SoundContextClassifier.classifyBluetoothName("Seattle Headset"))
        assertNull(SoundContextClassifier.classifyBluetoothName("Kiara Earbuds"))
    }

    @Test
    fun `car wins when a name contains both car and speaker words`() {
        assertEquals(SoundContext.CAR, SoundContextClassifier.classifyBluetoothName("Audi Speaker"))
    }
}
