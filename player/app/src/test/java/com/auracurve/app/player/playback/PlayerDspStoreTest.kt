package com.auracurve.app.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.auracurve.app.dsp.PlayerDspSettings
import com.auracurve.app.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Roadmap 11 P11-4: the player's DSP inputs behind PlayerDspStore, so tests need no global state.
class PlayerDspStoreTest {
    @Test
    fun inMemoryStores_keepTheirStateToThemselves_andLeaveTheProcessWideObjectsAlone() {
        val globalCurve = PlayerCurveState.current
        val globalMix = VirtualBassState.mix
        val globalSettings = PlayerDspState.settings.value
        val first = InMemoryPlayerDspStore()
        val second = InMemoryPlayerDspStore()

        first.updateSettings { it.copy(limiterEnabled = false) }
        first.setVirtualBassMix(0.4f)
        first.setCurve(PlayerCurve(listOf(TargetPoint(100f, 3f)), 25f))

        assertFalse(first.currentSettings.limiterEnabled)
        assertEquals(PlayerDspSettings(), second.currentSettings)
        assertEquals(0f, second.virtualBassMix, 0f)
        assertTrue(second.curve.curve.isEmpty())
        assertSame(globalCurve, PlayerCurveState.current)
        assertEquals(globalMix, VirtualBassState.mix, 0f)
        assertEquals(globalSettings, PlayerDspState.settings.value)
    }

    @Test
    fun beforeAnythingIsLoaded_theSafeDefaultsHold_monoBassAndLimiterOn() {
        val store = InMemoryPlayerDspStore()
        assertTrue(store.currentSettings.monoBassEnabled)
        assertTrue(store.currentSettings.limiterEnabled)
        assertTrue(PlayerDspSettings().monoBassEnabled && PlayerDspSettings().limiterEnabled)
    }

    @Test
    fun virtualBassMix_isClamped() {
        val store = InMemoryPlayerDspStore()
        store.setVirtualBassMix(3f)
        assertEquals(1f, store.virtualBassMix, 0f)
        store.setVirtualBassMix(-1f)
        assertEquals(0f, store.virtualBassMix, 0f)
    }

    @Test
    fun theAudioProcessor_followsTheStoreItWasGiven() {
        val reads = CountingSource(InMemoryPlayerDspStore())
        val processor = PlayerDspAudioProcessor(reads)
        processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        processor.queueInput(silence(frames = 256))
        processor.queueInput(silence(frames = 256))

        // Once on configure, then once per buffer.
        assertEquals(3, reads.settingsReads)
        assertEquals(2, reads.curveReads)
        assertEquals(2, reads.mixReads)
        assertTrue(processor.output.hasRemaining())
    }

    private fun silence(frames: Int): ByteBuffer =
        ByteBuffer.allocateDirect(frames * 2 * 2).order(ByteOrder.nativeOrder()).also { it.position(it.limit()).flip() }

    private class CountingSource(
        private val inner: PlayerDspSource,
    ) : PlayerDspSource {
        var settingsReads = 0
        var curveReads = 0
        var mixReads = 0

        override val currentSettings: PlayerDspSettings get() = inner.currentSettings.also { settingsReads++ }
        override val curve: PlayerCurve get() = inner.curve.also { curveReads++ }
        override val virtualBassMix: Float get() = inner.virtualBassMix.also { mixReads++ }
    }
}
