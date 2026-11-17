package com.auracurve.app.remote

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.isHandled
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.util.cio.ChannelIOException
import io.ktor.websocket.Frame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Everything the API needs from outside, so tests can run it with Ktor's test host and fakes. */
class RemoteApiConfig(
    val appVersion: String,
    /** The current token; null means none is set yet and nothing authenticates. */
    val token: () -> String?,
    val systemStatus: suspend () -> SystemStatus,
    /** Broadcast to every open WebSocket (each connection numbers them with its own seq). */
    val events: Flow<RemoteEvent>,
    /** The web remote's directory, or null when the app has none (no external storage). */
    val webRoot: () -> File?,
    /** Local and remote IP literal of a connection: false closes it without an answer. */
    val addressAllowed: (local: String, remote: String) -> Boolean = PrivateNetwork::allows,
    val rateLimiter: AuthRateLimiter = AuthRateLimiter(),
    val tickets: WebSocketTickets = WebSocketTickets(),
    val pingPeriod: Duration = 20.seconds,
    val pingTimeout: Duration = 60.seconds,
)

/** API version info (also the WebSocket's `hello`). */
const val API_VERSION = "v0"
const val MIN_PWA_VERSION = "0.2"

/**
 * Thrown to drop a connection without any answer (a public address on either end). The engine
 * pipeline treats a ChannelIOException as a broken connection: it logs it at debug level and
 * sends nothing, and CIO then closes the socket.
 */
class ConnectionRefused : ChannelIOException("Not a private network", IllegalStateException("refused"))

private val JSON_UTF8 = ContentType.Application.Json.withCharset(Charsets.UTF_8)

/**
 * The remote-control API of Pixel-Car module 04a: version, system status, WebSocket ticket and
 * WebSocket, plus the web remote's files on the same origin (E-005). Order of the checks for every
 * request: private network, Origin, rate limit, token (or ticket for the WebSocket).
 */
fun Application.remoteApi(config: RemoteApiConfig) {
    install(WebSockets) {
        pingPeriod = config.pingPeriod
        timeout = config.pingTimeout
        maxFrameSize = MAX_FRAME_BYTES
    }

    // Errors in the contract's format for everything, 404 and 500 included; never a stack trace.
    intercept(ApplicationCallPipeline.Monitoring) {
        try {
            proceed()
            if (!call.isHandled) respondError(call, ApiError.notFound())
        } catch (e: ConnectionRefused) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            respondError(call, e)
        } catch (e: Exception) {
            if (!call.isHandled) respondError(call, ApiError.internal())
        }
    }

    intercept(ApplicationCallPipeline.Plugins) {
        gate(call, config)?.let { error ->
            respondError(call, error)
            finish()
        }
    }

    routing {
        get("/api/version") { respondJson(call, versionJson(config)) }
        get("/api/v0/system/status") { respondJson(call, config.systemStatus().toJson()) }
        post("/api/v0/ws-ticket") {
            respondJson(
                call,
                buildJsonObject {
                    put("ticket", config.tickets.issue())
                    put("expiresInS", WebSocketTickets.TTL_MS / 1000)
                },
            )
        }
        webSocket("/api/v0/ws") {
            var seq = 0L
            send(Frame.Text(envelope("hello", ++seq, versionJson(config))))
            val forward =
                launch {
                    config.events.collect { event -> send(Frame.Text(envelope(event.type, ++seq, event.data))) }
                }
            try {
                // Server to client only (contract section 4): incoming frames are read and dropped,
                // which also notices a closed connection.
                for (ignored in incoming) Unit
            } finally {
                forward.cancel()
            }
        }
        // Everything outside /api is the web remote (same origin, E-005). The tailcard has the
        // lowest priority, so it only gets what no route above takes; Routing would otherwise
        // answer those itself with a bare 404 or 405.
        route("{...}") {
            handle {
                if (call.request.path().let { it == "/api" || it.startsWith("/api/") }) {
                    respondError(call, ApiError.notFound())
                } else {
                    serveStatic(call, config)
                }
            }
        }
    }
}

/** The checks every request goes through before routing. Null means it may pass. */
private fun gate(
    call: ApplicationCall,
    config: RemoteApiConfig,
): ApiError? {
    val point = call.request.local
    if (!config.addressAllowed(point.localAddress, point.remoteAddress)) throw ConnectionRefused()

    // Same origin only (E-005): a browser that sends an Origin must send this server's own.
    val origin = call.request.headers[HttpHeaders.Origin]
    val host = call.request.headers[HttpHeaders.Host]
    if (origin != null && (host == null || origin != "http://$host")) return ApiError.originNotAllowed()

    val path = call.request.path()
    if (!path.startsWith("/api/v0/")) return null
    val ip = point.remoteAddress
    if (config.rateLimiter.isBlocked(ip)) return ApiError.rateLimited()
    if (path == "/api/v0/ws") {
        return if (config.tickets.redeem(call.request.queryParameters["ticket"])) null else ApiError.badTicket()
    }
    val sent = RemoteToken.fromAuthorizationHeader(call.request.headers[HttpHeaders.Authorization])
    if (!RemoteToken.matches(config.token(), sent)) {
        config.rateLimiter.recordFailure(ip)
        return ApiError.unauthorized()
    }
    return null
}

private suspend fun serveStatic(
    call: ApplicationCall,
    config: RemoteApiConfig,
) {
    val method = call.request.httpMethod
    if (method != HttpMethod.Get && method != HttpMethod.Head) {
        respondError(call, ApiError.methodNotAllowed())
        return
    }
    val root = config.webRoot()
    if (root == null || !root.isDirectory) {
        call.respondText(UI_MISSING_TEXT, ContentType.Text.Plain.withCharset(Charsets.UTF_8), HttpStatusCode.ServiceUnavailable)
        return
    }
    val file = StaticFiles.resolve(root, call.request.path())
    if (file == null) {
        respondError(call, ApiError(HttpStatusCode.NotFound, "NOT_FOUND", "Datei nicht gefunden"))
        return
    }
    val type = ContentType.parse(StaticFiles.contentType(file))
    call.response.header(HttpHeaders.CacheControl, StaticFiles.CACHE_CONTROL)
    if (method == HttpMethod.Head) {
        call.respond(
            object : OutgoingContent.NoContent() {
                override val contentType = type
                override val contentLength = file.length()
            },
        )
    } else {
        call.respond(LocalFileContent(file, type))
    }
}

private fun versionJson(config: RemoteApiConfig): JsonObject =
    buildJsonObject {
        put("api", API_VERSION)
        put("app", config.appVersion)
        put("minPwa", MIN_PWA_VERSION)
    }

private fun envelope(
    type: String,
    seq: Long,
    data: JsonElement,
): String =
    buildJsonObject {
        put("type", type)
        put("seq", seq)
        put("data", data)
    }.toString()

private suspend fun respondJson(
    call: ApplicationCall,
    body: JsonElement,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    call.response.header(HttpHeaders.CacheControl, "no-store")
    call.respondText(body.toString(), JSON_UTF8, status)
}

private suspend fun respondError(
    call: ApplicationCall,
    error: ApiError,
) {
    if (call.isHandled) return
    call.response.header(HttpHeaders.CacheControl, "no-store")
    call.respondText(error.toJson(), JSON_UTF8, error.status)
}

private const val UI_MISSING_TEXT =
    "Oberfläche nicht installiert. Die Dateien der Fernbedienung gehören nach " +
        "Android/data/com.auracurve.app.full/files/web/ (adb push)."
private const val MAX_FRAME_BYTES = 64L * 1024L
