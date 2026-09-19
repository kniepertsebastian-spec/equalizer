package com.hardbasseq.eq.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AudioCapabilitiesMapperTest {
    private val knownTypes =
        KnownEffectTypeIds(
            equalizer = UUID.fromString("0bed4300-ddd6-11db-8f34-0002a5d5c51b"),
            dynamicsProcessing = UUID.fromString("7261676f-6d75-7369-6364-8000fed35d69"),
            bassBoost = UUID.fromString("0634f220-ddd4-11db-a0fc-0002a5d5c51b"),
            loudnessEnhancer = UUID.fromString("fe3199be-aed0-413f-87bb-11260eb63cf1"),
        )

    @Test
    fun `empty descriptor list yields no capabilities`() {
        val result = AudioCapabilitiesMapper.map(emptyList(), knownTypes)

        assertEquals(AudioCapabilities(false, false, false, false, 0), result)
    }

    @Test
    fun `recognizes equalizer and dynamics processing, ignores unknown effects`() {
        val descriptors =
            listOf(
                descriptor(knownTypes.equalizer, name = "Equalizer"),
                descriptor(knownTypes.dynamicsProcessing, name = "Dynamics Processing"),
                descriptor(UUID.randomUUID(), name = "Vendor Reverb"),
            )

        val result = AudioCapabilitiesMapper.map(descriptors, knownTypes)

        assertTrue(result.hasEqualizer)
        assertTrue(result.hasDynamicsProcessing)
        assertFalse(result.hasBassBoost)
        assertFalse(result.hasLoudnessEnhancer)
        assertEquals(3, result.totalEffectCount)
    }

    @Test
    fun `duplicate descriptors of the same type still count individually`() {
        val descriptors =
            listOf(
                descriptor(knownTypes.equalizer, name = "Equalizer A"),
                descriptor(knownTypes.equalizer, name = "Equalizer B"),
            )

        val result = AudioCapabilitiesMapper.map(descriptors, knownTypes)

        assertTrue(result.hasEqualizer)
        assertEquals(2, result.totalEffectCount)
    }

    private fun descriptor(
        typeUuid: UUID,
        name: String,
    ) = AudioEffectDescriptor(
        typeUuid = typeUuid,
        effectUuid = UUID.randomUUID(),
        name = name,
        implementor = "Test",
        connectMode = EffectConnectMode.INSERT,
    )
}
