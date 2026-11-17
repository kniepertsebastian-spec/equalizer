package com.auracurve.app.remote

import com.auracurve.app.remote.feed.FeedRemoteApi
import com.auracurve.app.remote.library.SpotifyLibraryRemoteApi
import com.auracurve.app.remote.playback.PlaybackRemoteApi
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
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.util.cio.ChannelIOException
import io.ktor.utils.io.readRemaining
import io.ktor.websocket.Frame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
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
    /** The EQ endpoints (04b); null leaves them out (tests of the scaffold). */
    val eq: EqRemoteApi? = null,
    /** Playback, volume and source (04c); null leaves them out. */
    val playback: PlaybackRemoteApi? = null,
    /** The feed (04d); null leaves it out. */
    val feed: FeedRemoteApi? = null,
    /** The offline copy of the Spotify library; null leaves it out. */
    val library: SpotifyLibraryRemoteApi? = null,
    val pingPeriod: Duration = 20.seconds,
    val pingTimeout: Duration = 60.seconds,
    /** Called with the number of open WebSockets whenever it changes (04f: compare safety net). */
    val onWebSocketClients: (Int) -> Unit = {},
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

    val clients = AtomicInteger(0)
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
        config.eq?.let { eq -> eqRoutes(eq) }
        config.playback?.let { playback -> playbackRoutes(playback) }
        config.feed?.let { feed -> feedRoutes(feed, config.playback) }
        config.library?.let { library -> libraryRoutes(library, config.playback) }
        webSocket("/api/v0/ws") {
            config.onWebSocketClients(clients.incrementAndGet())
            var seq = 0L
            // hello goes out only once the event subscription stands, so an event emitted right
            // after a client has seen hello is never lost (SharedFlow drops it without subscribers).
            val forward =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    val hello: suspend () -> Unit = { send(Frame.Text(envelope("hello", ++seq, versionJson(config)))) }
                    val events = config.events
                    val stream = if (events is SharedFlow) events.onSubscription { hello() } else events.onStart { hello() }
                    stream.collect { event -> send(Frame.Text(envelope(event.type, ++seq, event.data))) }
                }
            try {
                // Server to client only (contract section 4): incoming frames are read and dropped,
                // which also notices a closed connection.
                for (ignored in incoming) Unit
            } finally {
                forward.cancel()
                config.onWebSocketClients(clients.decrementAndGet())
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

private fun Route.eqRoutes(eq: EqRemoteApi) {
    get("/api/v0/eq/state") {
        if (!eq.ready) throw EqRemoteApi.notReady()
        respondJson(call, eq.state())
    }
    get("/api/v0/eq/options") {
        if (!eq.ready) throw EqRemoteApi.notReady()
        respondJson(call, eq.options())
    }
    post("/api/v0/eq/bypass") { respondJson(call, eq.setBypass(call.jsonBody())) }
    post("/api/v0/eq/preset") { respondJson(call, eq.setPreset(call.jsonBody())) }
    post("/api/v0/eq/goal") { respondJson(call, eq.setGoal(call.jsonBody())) }
    post("/api/v0/eq/context") { respondJson(call, eq.setContext(call.jsonBody())) }
    // 04f
    post("/api/v0/eq/preset/import") { respondJson(call, eq.importPreset(call.jsonBody(MAX_IMPORT_BYTES)), HttpStatusCode.Created) }
    post("/api/v0/eq/preset/save") { respondJson(call, eq.savePreset(call.jsonBody()), HttpStatusCode.Created) }
    post("/api/v0/eq/reset") { respondJson(call, eq.reset()) }
    post("/api/v0/eq/high-resolution") { respondJson(call, eq.setHighResolution(call.jsonBody())) }
    post("/api/v0/eq/band") { respondJson(call, eq.setBand(call.jsonBody())) }
    post("/api/v0/eq/parametric") { respondJson(call, eq.setParametric(call.jsonBody())) }
    post("/api/v0/eq/compare") { respondJson(call, eq.setCompare(call.jsonBody())) }
}

private fun Route.playbackRoutes(playback: PlaybackRemoteApi) {
    get("/api/v0/playback/now-playing") { respondJson(call, playback.nowPlaying()) }
    post("/api/v0/playback/playpause") { respondJson(call, playback.playPause()) }
    post("/api/v0/playback/next") { respondJson(call, playback.next()) }
    post("/api/v0/playback/prev") { respondJson(call, playback.previous()) }
    post("/api/v0/playback/shuffle") { respondJson(call, playback.setShuffle(call.jsonBody())) }
    post("/api/v0/playback/repeat") { respondJson(call, playback.setRepeat(call.jsonBody())) }
    post("/api/v0/playback/volume") { respondJson(call, playback.setVolume(call.jsonBody())) }
    // 202: the switch is announced as source.changed (contract 3.1).
    post("/api/v0/system/source") {
        playback.setSource(call.jsonBody())
        respondJson(call, buildJsonObject { put("accepted", true) }, HttpStatusCode.Accepted)
    }
}

private fun Route.feedRoutes(
    feed: FeedRemoteApi,
    playback: PlaybackRemoteApi?,
) {
    get("/api/v0/player/feed") { respondJson(call, feed.feed()) }
    post("/api/v0/player/pull") {
        feed.pull()
        respondJson(call, buildJsonObject { put("accepted", true) }, HttpStatusCode.Accepted)
    }
    // Like the devserver: answered with the now-playing state of the built-in player.
    post("/api/v0/player/play-all") {
        val count = feed.playAll()
        if (playback != null) {
            playback.usePlayer()
            respondJson(call, playback.nowPlaying())
        } else {
            respondJson(call, buildJsonObject { put("trackCount", count) })
        }
    }
}

private fun Route.libraryRoutes(
    library: SpotifyLibraryRemoteApi,
    playback: PlaybackRemoteApi?,
) {
    get("/api/v0/library/spotify") { respondJson(call, library.index()) }
    get("/api/v0/library/spotify/tracks") { respondJson(call, library.tracks()) }
    post("/api/v0/library/spotify/sync") {
        library.sync()
        respondJson(call, buildJsonObject { put("accepted", true) }, HttpStatusCode.Accepted)
    }
    // Like play-all: Spotify becomes the active source and the answer is its now-playing state
    // (the new song follows as playback.changed once Spotify reports it).
    post("/api/v0/library/spotify/play") {
        library.play(call.jsonBody())
        if (playback != null) {
            playback.useSpotify()
            respondJson(call, playback.nowPlaying())
        } else {
            respondJson(call, buildJsonObject { put("accepted", true) })
        }
    }
}

/** The request body as a JSON object, at most [maxBytes]; anything else is 400 BAD_REQUEST. */
internal suspend fun ApplicationCall.jsonBody(maxBytes: Int = MAX_BODY_BYTES): JsonObject {
    val declared = request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (declared != null && declared > maxBytes) throw EqRemoteApi.badRequest("Body zu groß")
    // Read at most one byte more than allowed: a chunked body (no Content-Length) never lands in
    // memory in full before its size is known.
    val bytes = receiveChannel().readRemaining(maxBytes + 1L).readByteArray()
    if (bytes.size > maxBytes) throw EqRemoteApi.badRequest("Body zu groß")
    val text = bytes.toString(Charsets.UTF_8)
    if (text.isBlank()) return JsonObject(emptyMap())
    val element = runCatching { Json.parseToJsonElement(text) }.getOrNull() ?: throw EqRemoteApi.badRequest("Body ist kein JSON")
    return element as? JsonObject ?: throw EqRemoteApi.badRequest("Body muss ein Objekt sein")
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
private const val MAX_BODY_BYTES = 64 * 1024

// 04f: a preset link is short; the codec allows 100 KB unpacked, the request at most 16 KB.
private const val MAX_IMPORT_BYTES = 16 * 1024
