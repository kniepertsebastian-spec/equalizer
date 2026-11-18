package com.auracurve.app.remote

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetAddress

// Module 05: pairing by one-time code, one token per device, removal, and the Wi-Fi switch.
class PairingTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 0L
    private var nextCode = "ABCDEFGH"
    private var tokens = 0

    private fun devices(file: File = File(tmp.root, "devices.json")) =
        RemoteDevices(file, clock = { now }, newToken = { "token-${++tokens}-0123456789" }, newCode = { nextCode })

    private fun api(
        devices: RemoteDevices,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            remoteApi(
                RemoteApiConfig(
                    appVersion = "2.0.0-test",
                    token = { "old-single-token" },
                    systemStatus = { SystemStatus(81, true, "NONE", "BLUETOOTH", "Autoradio") },
                    events = MutableSharedFlow(),
                    webRoot = { null },
                    addressAllowed = { _, _ -> true },
                    rateLimiter = AuthRateLimiter(nowMs = { now }),
                    tickets = WebSocketTickets(nowMs = { now }),
                    devices = devices,
                ),
            )
        }
        block()
    }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun HttpResponse.errorCode(): String = json()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    private suspend fun ApplicationTestBuilder.pair(
        code: String,
        name: String = "Pixel 10",
    ): HttpResponse =
        client.post("/api/v0/pair") {
            contentType(ContentType.Application.Json)
            setBody("""{"code":"$code","name":"$name"}""")
        }

    private suspend fun ApplicationTestBuilder.status(token: String) =
        client.get("/api/v0/system/status") { header(HttpHeaders.Authorization, "Bearer $token") }.status

    @Test
    fun `the right code gives the device its own token, once`() {
        val devices = devices()
        api(devices) {
            devices.startPairing()
            val response = pair("abcd-efgh")
            assertEquals(HttpStatusCode.Created, response.status)
            val body = response.json()
            val token = body["token"]!!.jsonPrimitive.content
            assertEquals("Pixel 10", body["device"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            assertEquals(HttpStatusCode.OK, status(token))
            assertNull("the code is used up", devices.pairing.value)

            val again = pair("ABCDEFGH")
            assertEquals(HttpStatusCode.Unauthorized, again.status)
            assertEquals("PAIRING_FAILED", again.errorCode())
            // The token of module 04a counts no more once devices are in charge.
            assertEquals(HttpStatusCode.Unauthorized, status("old-single-token"))
        }
    }

    @Test
    fun `an expired, a wrong or no code pairs nothing`() {
        val devices = devices()
        api(devices) {
            assertEquals("no code shown", HttpStatusCode.Unauthorized, pair("ABCDEFGH").status)
            devices.startPairing()
            assertEquals(HttpStatusCode.Unauthorized, pair("ABCDEFGJ").status)
            assertEquals(HttpStatusCode.Unauthorized, pair("not a code").status)
            now += RemoteDevices.CODE_TTL_MS
            assertEquals("expired", HttpStatusCode.Unauthorized, pair("ABCDEFGH").status)
            assertTrue(devices.devices.value.isEmpty())
        }
    }

    @Test
    fun `wrong codes count against the address like wrong tokens`() {
        val devices = devices()
        api(devices) {
            devices.startPairing()
            repeat(10) { pair("ZZZZZZZZ") }
            val blocked = pair("ABCDEFGH")
            assertEquals(HttpStatusCode.TooManyRequests, blocked.status)
            assertEquals("RATE_LIMITED", blocked.errorCode())
        }
    }

    @Test
    fun `a removed device is refused, the others keep working`() {
        val devices = devices()
        api(devices) {
            devices.startPairing()
            val first = pair("ABCDEFGH").json()["token"]!!.jsonPrimitive.content
            nextCode = "HGFEDCBA"
            devices.startPairing()
            val second = pair("HGFEDCBA", "iPhone").json()
            val secondToken = second["token"]!!.jsonPrimitive.content
            devices.revoke(second["device"]!!.jsonObject["id"]!!.jsonPrimitive.content)
            assertEquals(HttpStatusCode.Unauthorized, status(secondToken))
            assertEquals(HttpStatusCode.OK, status(first))
            devices.revokeAll()
            assertEquals(HttpStatusCode.Unauthorized, status(first))
        }
    }

    @Test
    fun `removing a device closes its websocket`() {
        val devices = devices()
        api(devices) {
            devices.startPairing()
            val paired = pair("ABCDEFGH").json()
            val token = paired["token"]!!.jsonPrimitive.content
            val id = paired["device"]!!.jsonObject["id"]!!.jsonPrimitive.content
            val ticket =
                client
                    .post("/api/v0/ws-ticket") { header(HttpHeaders.Authorization, "Bearer $token") }
                    .json()["ticket"]!!
                    .jsonPrimitive.content
            createClient { install(WebSockets) }.webSocket("/api/v0/ws?ticket=$ticket") {
                assertTrue(incoming.receive() is Frame.Text) // hello
                devices.revoke(id)
                val reason = withTimeout(5_000) { closeReason.await() }
                assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason?.code)
            }
        }
    }

    @Test
    fun `at most ten devices`() {
        val devices = devices()
        api(devices) {
            repeat(RemoteDevices.MAX_DEVICES) {
                devices.startPairing()
                assertEquals(HttpStatusCode.Created, pair("ABCDEFGH").status)
            }
            devices.startPairing()
            val full = pair("ABCDEFGH")
            assertEquals(HttpStatusCode.Conflict, full.status)
            assertEquals("TOO_MANY_DEVICES", full.errorCode())
        }
    }

    @Test
    fun `devices survive a restart, the file holds no token`() {
        val file = File(tmp.root, "keep.json")
        val first = devices(file)
        first.startPairing()
        val paired = first.pair("ABCDEFGH", "Pixel 10")!!
        val again = devices(file)
        assertEquals("Pixel 10", again.authenticate(paired.token)?.name)
        assertFalse(file.readText().contains(paired.token))
        assertNull(again.authenticate("something else"))
        assertNull(again.authenticate(null))
    }

    @Test
    fun `the old single token is taken over once as a device`() {
        val devices = devices()
        devices.importLegacyToken("legacy-token-0123456789", "Earlier pairing")
        devices.importLegacyToken("legacy-token-0123456789", "Earlier pairing")
        assertEquals(1, devices.devices.value.size)
        assertNotNull(devices.authenticate("legacy-token-0123456789"))
    }

    @Test
    fun `codes and names are cleaned`() {
        assertEquals("ABCDEFGH", RemoteDevices.normalizeCode("abcd-efgh"))
        assertEquals("ABCDEFGH", RemoteDevices.normalizeCode(" ABCD EFGH "))
        assertNull(RemoteDevices.normalizeCode("ABCDEFG"))
        assertNull("no I, O, 0, 1", RemoteDevices.normalizeCode("ABCDEFG0"))
        assertEquals(8, RemoteDevices.randomCode().length)
        assertEquals("ABCD-EFGH", PairingCode("ABCDEFGH", 0).display)
        assertEquals(RemoteDevices.DEFAULT_NAME, RemoteDevices.cleanName("  \u0000 "))
        assertEquals(RemoteDevices.MAX_NAME_LENGTH, RemoteDevices.cleanName("x".repeat(100)).length)
    }

    @Test
    fun `the home wi-fi is only reachable with the switch`() {
        val wifi = setOf(InetAddress.getByName("192.168.178.37"))
        assertFalse(NetworkZone.allows("192.168.178.37", wifi, allowClientNetworks = false))
        assertFalse("v4 in v6", NetworkZone.allows("::ffff:192.168.178.37", wifi, allowClientNetworks = false))
        assertTrue(NetworkZone.allows("192.168.178.37", wifi, allowClientNetworks = true))
        assertTrue("hotspot", NetworkZone.allows("10.40.5.90", wifi, allowClientNetworks = false))
    }
}
