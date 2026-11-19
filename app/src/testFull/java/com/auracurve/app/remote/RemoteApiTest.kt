package com.auracurve.app.remote

import io.ktor.client.HttpClient
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
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RemoteApiTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val token = "test-token-0123456789"
    private val status = SystemStatus(81, true, "NONE", "BLUETOOTH", "Autoradio")
    private val events = MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 8)
    private var now = 0L

    private fun config(
        webRoot: File? = null,
        systemStatus: suspend () -> SystemStatus = { status },
        visualizer: ((Boolean) -> JsonObject)? = null,
    ) = RemoteApiConfig(
        visualizer = visualizer,
        appVersion = "2.0.0-test",
        token = { token },
        systemStatus = systemStatus,
        events = events,
        webRoot = { webRoot },
        // The test engine reports no real addresses; the address check is tested on its own
        // and against a real CIO server in RemoteServerSocketTest.
        addressAllowed = { _, _ -> true },
        rateLimiter = AuthRateLimiter(nowMs = { now }),
        tickets = WebSocketTickets(nowMs = { now }),
    )

    private fun api(
        config: RemoteApiConfig = config(),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application { remoteApi(config) }
        block()
    }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun HttpResponse.assertError(
        status: HttpStatusCode,
        code: String,
    ) {
        assertEquals(status, this.status)
        assertTrue(headers[HttpHeaders.ContentType].orEmpty().startsWith("application/json"))
        val body = json()
        assertEquals("false", body["ok"]!!.jsonPrimitive.content)
        assertEquals(code, body["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertTrue(
            body["error"]!!
                .jsonObject["message"]!!
                .jsonPrimitive.content
                .isNotEmpty(),
        )
    }

    private suspend fun HttpClient.authed(path: String) = get(path) { header(HttpHeaders.Authorization, "Bearer $token") }

    @Test
    fun `the visualizer lease is asked for by POST and needs a token`() {
        val asked = mutableListOf<Boolean>()
        api(
            config(visualizer = { on ->
                asked += on
                buildJsonObject { put("active", on) }
            }),
        ) {
            client.post("/api/v0/visualizer") { setBody("""{"enabled":true}""") }.assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
            val response =
                client.post("/api/v0/visualizer") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"enabled":true}""")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("true", response.json()["active"]!!.jsonPrimitive.content)
            assertEquals(listOf(true), asked)
        }
    }

    @Test
    fun `version needs no token`() =
        api {
            val response = client.get("/api/version")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.json()
            assertEquals("v0", body["api"]!!.jsonPrimitive.content)
            assertEquals("2.0.0-test", body["app"]!!.jsonPrimitive.content)
            assertEquals("0.2", body["minPwa"]!!.jsonPrimitive.content)
            // Module 10: no signed bundle in this test, the files come from the folder.
            assertEquals("false", body["web"]!!.jsonObject["verified"]!!.jsonPrimitive.content)
        }

    @Test
    fun `api calls without or with a wrong token get 401`() =
        api {
            client.get("/api/v0/system/status").assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
            client
                .get("/api/v0/system/status") { header(HttpHeaders.Authorization, "Bearer wrong") }
                .assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
            client.post("/api/v0/ws-ticket").assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        }

    @Test
    fun `system status in the contract's shape`() =
        api {
            val response = client.authed("/api/v0/system/status")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            val body = response.json()
            assertEquals(81, body["battery"]!!.jsonObject["percent"]!!.jsonPrimitive.int)
            assertEquals("true", body["battery"]!!.jsonObject["charging"]!!.jsonPrimitive.content)
            assertEquals("NONE", body["thermal"]!!.jsonObject["status"]!!.jsonPrimitive.content)
            assertEquals("BLUETOOTH", body["audioRoute"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals("Autoradio", body["audioRoute"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            assertEquals("MAIN", body["activeUser"]!!.jsonPrimitive.content)
            assertEquals("PLAYER", body["activeSource"]!!.jsonPrimitive.content)
            assertEquals("CAR", body["mode"]!!.jsonPrimitive.content)
        }

    @Test
    fun `ten wrong tokens block the ip with 429 even for the right token`() =
        api {
            repeat(10) { client.get("/api/v0/system/status") { header(HttpHeaders.Authorization, "Bearer guess$it") } }
            client.authed("/api/v0/system/status").assertError(HttpStatusCode.TooManyRequests, "RATE_LIMITED")
            assertEquals("version stays open", HttpStatusCode.OK, client.get("/api/version").status)
            now += 60_000L
            assertEquals(HttpStatusCode.OK, client.authed("/api/v0/system/status").status)
        }

    @Test
    fun `a foreign origin gets 403 and the own origin passes`() =
        api {
            client
                .get("/api/v0/system/status") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.Origin, "http://evil.example")
                }.assertError(HttpStatusCode.Forbidden, "ORIGIN_NOT_ALLOWED")
            client.get("/api/version") { header(HttpHeaders.Origin, "null") }.assertError(HttpStatusCode.Forbidden, "ORIGIN_NOT_ALLOWED")
            val own =
                client.get("/api/v0/system/status") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.Host, "192.168.43.1:8080")
                    header(HttpHeaders.Origin, "http://192.168.43.1:8080")
                }
            assertEquals(HttpStatusCode.OK, own.status)
        }

    @Test
    fun `unknown paths and failures use the error format without internals`() =
        api(config(systemStatus = { error("battery service exploded at line 42") })) {
            client.authed("/api/v0/nothing").assertError(HttpStatusCode.NotFound, "NOT_FOUND")
            client.get("/api/v1/version").assertError(HttpStatusCode.NotFound, "NOT_FOUND")
            val failed = client.authed("/api/v0/system/status")
            failed.assertError(HttpStatusCode.InternalServerError, "INTERNAL")
            assertFalse(failed.bodyAsText().contains("exploded"))
        }

    @Test
    fun `the websocket needs a ticket that works once`() =
        api {
            val ws = createClient { install(WebSockets) }
            val ticket =
                client
                    .post("/api/v0/ws-ticket") { header(HttpHeaders.Authorization, "Bearer $token") }
                    .json()
                    .also { assertEquals(30, it["expiresInS"]!!.jsonPrimitive.int) }["ticket"]!!
                    .jsonPrimitive
                    .content

            ws.webSocket("/api/v0/ws?ticket=$ticket") {
                val hello = Json.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject
                assertEquals("hello", hello["type"]!!.jsonPrimitive.content)
                assertEquals(1L, hello["seq"]!!.jsonPrimitive.long)
                assertEquals("v0", hello["data"]!!.jsonObject["api"]!!.jsonPrimitive.content)

                events.emit(RemoteEvent("system.changed", buildJsonObject { put("x", 1) }))
                val event = Json.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject
                assertEquals("system.changed", event["type"]!!.jsonPrimitive.content)
                assertEquals(2L, event["seq"]!!.jsonPrimitive.long)
            }

            client.get("/api/v0/ws?ticket=$ticket").assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
            client.get("/api/v0/ws").assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        }

    @Test
    fun `an expired ticket is refused`() =
        api {
            val ticket =
                client
                    .post("/api/v0/ws-ticket") { header(HttpHeaders.Authorization, "Bearer $token") }
                    .json()["ticket"]!!
                    .jsonPrimitive
                    .content
            now += 30_000L
            client.get("/api/v0/ws?ticket=$ticket").assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        }

    @Test
    fun `the web remote is served from its directory without leaving it`() {
        val root = tmp.newFolder("web")
        File(root, "index.html").writeText("<html>remote</html>")
        File(root, "sw.js").writeText("self")
        tmp.newFile("secret.txt").writeText("token")
        api(config(webRoot = root)) {
            val index = client.get("/")
            assertEquals(HttpStatusCode.OK, index.status)
            assertEquals("<html>remote</html>", index.bodyAsText())
            assertEquals("no-cache", index.headers[HttpHeaders.CacheControl])
            assertTrue(index.headers[HttpHeaders.ContentType].orEmpty().startsWith("text/html"))

            val sw = client.get("/sw.js")
            assertEquals("no-cache", sw.headers[HttpHeaders.CacheControl])
            assertTrue(sw.headers[HttpHeaders.ContentType].orEmpty().startsWith("text/javascript"))

            client.get("/%2e%2e/secret.txt").assertError(HttpStatusCode.NotFound, "NOT_FOUND")
            client.get("/..%2fsecret.txt").assertError(HttpStatusCode.NotFound, "NOT_FOUND")
            client.get("/missing.js").assertError(HttpStatusCode.NotFound, "NOT_FOUND")
            client.post("/index.html").assertError(HttpStatusCode.MethodNotAllowed, "METHOD_NOT_ALLOWED")
        }
    }

    @Test
    fun `without the web remote's files the page says so`() =
        api(config(webRoot = File(tmp.root, "absent"))) {
            val response = client.get("/")
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertTrue(response.bodyAsText().contains("Oberfläche nicht installiert"))
        }
}
