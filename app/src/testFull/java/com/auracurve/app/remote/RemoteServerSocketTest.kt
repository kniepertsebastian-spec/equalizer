package com.auracurve.app.remote

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket

/** Against the real CIO engine: what a client outside the private network sees is a closed connection. */
class RemoteServerSocketTest {
    private fun requestOnce(allowed: Boolean): String {
        val port = ServerSocket(0).use { it.localPort }
        val config =
            RemoteApiConfig(
                appVersion = "test",
                token = { "t" },
                systemStatus = { SystemStatus(50, false, "NONE", "SPEAKER", "Speaker") },
                events = emptyFlow(),
                webRoot = { null },
                addressAllowed = { _, _ -> allowed },
            )
        val server = embeddedServer(CIO, port = port, host = "127.0.0.1") { remoteApi(config) }.start(wait = false)
        try {
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 5_000
                socket.getOutputStream().write(
                    "GET /api/version HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nConnection: close\r\n\r\n".toByteArray(),
                )
                socket.getOutputStream().flush()
                return socket.getInputStream().readBytes().decodeToString()
            }
        } finally {
            server.stop(0, 1_000)
        }
    }

    @Test
    fun `a refused address gets no answer at all`() {
        assertEquals("", requestOnce(allowed = false))
    }

    @Test
    fun `an allowed address gets the answer`() {
        val answer = requestOnce(allowed = true)
        assertTrue(answer, answer.startsWith("HTTP/1.1 200"))
        assertTrue(answer, answer.contains("\"api\":\"v0\""))
    }
}
