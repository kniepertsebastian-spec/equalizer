package com.hardbasseq.eq.ui.eq

import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.ProcessingSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class EqStatusTest {
    private val on = ProcessingSettings()

    @Test
    fun `only a confirmed attach with processing on is active`() {
        assertEquals(EqStatusKind.ACTIVE, EqStatus.kind(AudioEngineState.Active(1), on))
        assertEquals(EqStatusKind.CONNECTING, EqStatus.kind(AudioEngineState.Attaching(1), on))
    }

    @Test
    fun `master off or bypass is never shown as active`() {
        assertEquals(EqStatusKind.OFF, EqStatus.kind(AudioEngineState.Active(1), on.copy(masterEnabled = false)))
        assertEquals(EqStatusKind.OFF, EqStatus.kind(AudioEngineState.Active(1), on.copy(bypass = true)))
    }

    @Test
    fun `waiting lost unsupported and error states map to plain categories`() {
        assertEquals(EqStatusKind.WAITING, EqStatus.kind(AudioEngineState.Listening, on))
        assertEquals(EqStatusKind.WAITING, EqStatus.kind(AudioEngineState.Detached, on))
        assertEquals(EqStatusKind.LOST, EqStatus.kind(AudioEngineState.LostControl(1, "x"), on))
        assertEquals(EqStatusKind.LOST, EqStatus.kind(AudioEngineState.Retrying(1, 1, 0L), on))
        assertEquals(EqStatusKind.UNSUPPORTED, EqStatus.kind(AudioEngineState.Unsupported("x"), on))
        assertEquals(EqStatusKind.ERROR, EqStatus.kind(AudioEngineState.Error("x"), on))
    }
}
