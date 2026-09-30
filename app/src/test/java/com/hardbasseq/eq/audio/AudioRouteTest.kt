package com.hardbasseq.eq.audio

import com.hardbasseq.eq.context.SoundContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRouteTest {
    @Test
    fun `only car and bluetooth speaker routes imply a sound context`() {
        assertEquals(SoundContext.CAR, AudioDeviceType.CAR.soundContext())
        assertEquals(SoundContext.BLUETOOTH_SPEAKER, AudioDeviceType.BLUETOOTH_SPEAKER.soundContext())
        listOf(
            AudioDeviceType.SPEAKER,
            AudioDeviceType.WIRED_HEADPHONES,
            AudioDeviceType.BLUETOOTH,
            AudioDeviceType.USB,
            AudioDeviceType.UNKNOWN,
        ).forEach { assertNull(it.name, it.soundContext()) }
    }

    @Test
    fun `car and speaker routes do not default to headphone acoustics`() {
        assertFalse(AudioDeviceType.CAR.defaultHeadphoneAcoustics())
        assertFalse(AudioDeviceType.BLUETOOTH_SPEAKER.defaultHeadphoneAcoustics())
        assertTrue(AudioDeviceType.BLUETOOTH.defaultHeadphoneAcoustics())
    }
}
