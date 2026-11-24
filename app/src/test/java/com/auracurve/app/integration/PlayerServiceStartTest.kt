package com.auracurve.app.integration

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.auracurve.app.player.playback.QueueRequest
import com.auracurve.app.player.playback.QueueRequests
import com.auracurve.app.player.service.PlayerCommandSender
import com.auracurve.app.player.service.PlayerSessionCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class PlayerServiceStartTest {
    @Test
    fun `a start Android allows goes through`() {
        var started = 0
        assertTrue(PlayerServiceStart.start(start = { started++ }, onRefused = { throw AssertionError("not refused") }))
        assertEquals(1, started)
    }

    @Test
    fun `a start refused in the background is dropped instead of crashing`() {
        // What Android 8+ throws for startService from the background
        // (BackgroundServiceStartNotAllowedException extends IllegalStateException).
        val refusal = IllegalStateException("Not allowed to start service Intent: app is in background")
        var reported: IllegalStateException? = null
        assertFalse(PlayerServiceStart.start(start = { throw refusal }, onRefused = { reported = it }))
        assertSame(refusal, reported)
    }

    // --- The master bar's stop and play/pause (AndroidPlayerBridge), roadmap 11 P11-1 ----------
    // Called from the background too (quick tile, widget, remote server, API 31+): what Android
    // 12+ throws there for startService must never be reached.

    @Test
    fun `stopping from the background goes over the session, not startService`() {
        val context = BackgroundContext()
        val commands = RecordingCommands(hasLoadedTrack = true)
        val bridge = AndroidPlayerBridge(context).apply { playerCommands = commands }

        bridge.stopPlayer()

        assertEquals(listOf(PlayerSessionCommands.STOP), commands.sent)
        assertEquals(0, context.serviceStarts)
    }

    @Test
    fun `play pause from the background goes over the session, not startService`() {
        val context = BackgroundContext()
        val commands = RecordingCommands(hasLoadedTrack = true)
        val bridge = AndroidPlayerBridge(context).apply { playerCommands = commands }

        bridge.togglePlayback()
        bridge.togglePlayback()

        assertEquals(listOf(PlayerSessionCommands.TOGGLE_PLAYBACK, PlayerSessionCommands.TOGGLE_PLAYBACK), commands.sent)
        assertEquals(0, context.serviceStarts)
    }

    @Test
    fun `no player running - stop does not start the service only to stop it`() {
        val context = BackgroundContext()
        val commands = RecordingCommands(hasLoadedTrack = false)
        val bridge = AndroidPlayerBridge(context).apply { playerCommands = commands }

        bridge.stopPlayer()
        bridge.togglePlayback()

        assertTrue(commands.sent.isEmpty())
        assertEquals(0, context.serviceStarts)
    }

    @Test
    fun `the session grants exactly the commands the bridge and the controller send`() {
        assertEquals(
            setOf(
                PlayerSessionCommands.STOP,
                PlayerSessionCommands.TOGGLE_PLAYBACK,
                PlayerSessionCommands.NEXT,
                PlayerSessionCommands.PREVIOUS,
            ),
            PlayerSessionCommands.COMMANDS,
        )
    }

    // --- The remote server's play/pause and skipping (AndroidPlayerController), ADR 0004 step 3 --

    @Test
    fun `play pause and skipping from the background go over the session while a track is loaded`() {
        val context = BackgroundContext()
        val commands = RecordingCommands(hasLoadedTrack = true)
        val controller = AndroidPlayerController(context, unusedOnlineSources()).apply { playerCommands = commands }

        controller.togglePlayback()
        controller.next()
        controller.previous()

        assertEquals(
            listOf(PlayerSessionCommands.TOGGLE_PLAYBACK, PlayerSessionCommands.NEXT, PlayerSessionCommands.PREVIOUS),
            commands.sent,
        )
        assertEquals(0, context.serviceStarts)
    }

    @Test
    fun `with nothing loaded the controller sends the intent as before`() {
        val context = BackgroundContext()
        val commands = RecordingCommands(hasLoadedTrack = false)
        val controller = AndroidPlayerController(context, unusedOnlineSources()).apply { playerCommands = commands }

        // From a screen in the foreground this starts the service; in the background Android
        // refuses it and the command is dropped instead of crashing.
        controller.togglePlayback()

        assertTrue(commands.sent.isEmpty())
        assertEquals(1, context.serviceStarts)
    }

    private fun unusedOnlineSources(): OnlineSources =
        Proxy.newProxyInstance(OnlineSources::class.java.classLoader, arrayOf(OnlineSources::class.java)) { _, method, _ ->
            throw AssertionError("not used here: ${method.name}")
        } as OnlineSources

    @Test
    fun `a withdrawn queue request is not applied later`() {
        val dropped = QueueRequest.Append(emptyList())
        val later = QueueRequest.PlayNext(emptyList())
        QueueRequests.submit(dropped)
        QueueRequests.withdraw(dropped)
        QueueRequests.submit(later)
        assertSame(later, QueueRequests.take())
        assertNull(QueueRequests.take())
    }

    // An app in the background on Android 12+: every service start is refused the way the
    // system does it (BackgroundServiceStartNotAllowedException extends IllegalStateException).
    private class BackgroundContext : ContextWrapper(null) {
        var serviceStarts = 0

        override fun getApplicationContext(): Context = this

        override fun startService(service: Intent?): ComponentName {
            serviceStarts++
            throw IllegalStateException("Not allowed to start service $service: app is in background")
        }

        override fun startForegroundService(service: Intent?): ComponentName {
            serviceStarts++
            throw IllegalStateException("Not allowed to start foreground service $service: app is in background")
        }
    }

    private class RecordingCommands(
        override val hasLoadedTrack: Boolean,
    ) : PlayerCommandSender {
        val sent = mutableListOf<String>()

        override fun send(command: String) {
            sent += command
        }
    }
}
