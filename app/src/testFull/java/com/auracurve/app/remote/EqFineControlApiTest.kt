package com.auracurve.app.remote

import com.auracurve.app.audio.AudioCapabilities
import com.auracurve.app.audio.FakeAudioEngine
import com.auracurve.app.data.preset.PresetShareCodec
import com.auracurve.app.eq.CompareSide
import com.auracurve.app.eq.EqController
import com.auracurve.app.eq.EqFakeAppProfileRepository
import com.auracurve.app.eq.EqFakeAppSettingsRepository
import com.auracurve.app.eq.EqFakeCarConnectionRepository
import com.auracurve.app.eq.EqFakeCorrectionProfileRepository
import com.auracurve.app.eq.EqFakeDeviceProfileRepository
import com.auracurve.app.eq.EqFakePlayerBridge
import com.auracurve.app.eq.EqFakePresetRepository
import com.auracurve.app.eq.EqFakeRouteRepository
import com.auracurve.app.eq.EqFakeSessionRepository
import com.auracurve.app.eq.EqFakeVolumeRepository
import com.auracurve.app.preset.BuiltInPresets
import com.auracurve.app.quick.QuickControls
import com.auracurve.app.text.FakeTextProvider
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pixel-Car module 04f: EQ fine control over the remote API. */
@OptIn(ExperimentalCoroutinesApi::class)
class EqFineControlApiTest {
    private val token = "t"
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val presets = EqFakePresetRepository()
    private val settings = EqFakeAppSettingsRepository()

    @After
    fun tearDown() = controllerScope.cancel()

    private fun controller(engine: FakeAudioEngine = FakeAudioEngine()) =
        EqController(
            audioEngine = engine,
            routeRepository = EqFakeRouteRepository(),
            volumeRepository = EqFakeVolumeRepository(),
            playerBridge = EqFakePlayerBridge(),
            presetRepository = presets,
            appSettingsRepository = settings,
            correctionProfileRepository = EqFakeCorrectionProfileRepository(),
            deviceProfileRepository = EqFakeDeviceProfileRepository(),
            sessionRepository = EqFakeSessionRepository(),
            carConnectionRepository = EqFakeCarConnectionRepository(),
            quickControls = QuickControls(),
            appProfileRepository = EqFakeAppProfileRepository(),
            scope = controllerScope,
        )

    private fun api(
        eq: EqController = controller(),
        block: suspend ApplicationTestBuilder.(EqController) -> Unit,
    ) = testApplication {
        val remote = EqRemoteApi(eq, FakeTextProvider(), Dispatchers.Unconfined)
        application {
            remoteApi(
                RemoteApiConfig(
                    appVersion = "t",
                    token = { token },
                    systemStatus = { SystemStatus(1, false, "NONE", "SPEAKER", "S") },
                    events = emptyFlow(),
                    webRoot = { null },
                    addressAllowed = { _, _ -> true },
                    eq = remote,
                ),
            )
        }
        block(eq)
    }

    private suspend fun ApplicationTestBuilder.post(
        path: String,
        body: String,
    ): HttpResponse =
        client.post(path) {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(body)
        }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun HttpResponse.code(): String = json()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    @Test
    fun `options carry the presets and the limits`() =
        api { eq ->
            val response =
                client.get("/api/v0/eq/options") { header(HttpHeaders.Authorization, "Bearer $token") }
            val options = response.json()
            assertEquals(eq.allPresets.value.size, options["presets"]!!.jsonArray.size)
            assertEquals("true", options["highResolutionAvailable"]!!.jsonPrimitive.content)
            val limits = options["limits"]!!.jsonObject
            assertEquals("-15.0", limits["bandGainDb"]!!.jsonObject["min"]!!.jsonPrimitive.content)
            assertEquals("8", limits["parametric"]!!.jsonObject["maxBands"]!!.jsonPrimitive.content)
        }

    @Test
    fun `a band inside the limits is set, outside it is refused rather than clamped`() =
        api { eq ->
            val ok = post("/api/v0/eq/band", """{"index":2,"gainDb":-3.5}""")
            assertEquals(HttpStatusCode.OK, ok.status)
            assertEquals(-3.5f, eq.processingSettings.value.bandGainsDb[2])
            assertEquals("true", ok.json()["dirty"]!!.jsonPrimitive.content)

            assertEquals("OUT_OF_RANGE", post("/api/v0/eq/band", """{"index":2,"gainDb":16}""").code())
            assertEquals("OUT_OF_RANGE", post("/api/v0/eq/band", """{"index":9,"gainDb":1}""").code())
            assertEquals("BAD_REQUEST", post("/api/v0/eq/band", """{"index":1.5,"gainDb":1}""").code())
            assertEquals(-3.5f, eq.processingSettings.value.bandGainsDb[2])
        }

    @Test
    fun `the parametric list is replaced as a whole and checked against the limits`() =
        api { eq ->
            val ok =
                post(
                    "/api/v0/eq/parametric",
                    """{"bands":[{"frequencyHz":60,"gainDb":4,"q":0.8},{"frequencyHz":3000,"gainDb":-2,"q":2}]}""",
                )
            assertEquals(HttpStatusCode.OK, ok.status)
            assertEquals(2, eq.parametricBands.value.size)
            assertEquals(
                2,
                settings.savedSettings
                    .last()
                    .parametricBands.size,
            )
            assertEquals(2, ok.json()["parametric"]!!.jsonArray.size)

            post("/api/v0/eq/parametric", """{"bands":[{"frequencyHz":60,"gainDb":4,"q":0.8}]}""")
            assertEquals(1, eq.parametricBands.value.size)

            assertEquals("OUT_OF_RANGE", post("/api/v0/eq/parametric", """{"bands":[{"frequencyHz":10,"gainDb":0,"q":1}]}""").code())
            assertEquals("OUT_OF_RANGE", post("/api/v0/eq/parametric", """{"bands":[{"frequencyHz":100,"gainDb":30,"q":1}]}""").code())
            assertEquals("OUT_OF_RANGE", post("/api/v0/eq/parametric", """{"bands":[{"frequencyHz":100,"gainDb":0,"q":11}]}""").code())
            val nine = (1..9).joinToString(",") { """{"frequencyHz":${it * 100},"gainDb":0,"q":1}""" }
            assertEquals("OUT_OF_RANGE", post("/api/v0/eq/parametric", """{"bands":[$nine]}""").code())
            assertEquals("BAD_REQUEST", post("/api/v0/eq/parametric", """{"bands":[{"frequencyHz":100}]}""").code())
            assertEquals(1, eq.parametricBands.value.size)
        }

    @Test
    fun `import a valid link, refuse a broken and an oversized one`() =
        api { eq ->
            val link = PresetShareCodec.encode(BuiltInPresets.DeepRumble.copy(id = "shared", name = "Shared Rumble"))
            val created = post("/api/v0/eq/preset/import", """{"link":"$link"}""")
            assertEquals(HttpStatusCode.Created, created.status)
            val id = created.json()["id"]!!.jsonPrimitive.content
            assertEquals(id, eq.activePreset.value.id)
            assertEquals("Shared Rumble", created.json()["label"]!!.jsonPrimitive.content)
            assertTrue(presets.savedPresets.any { it.id == id })

            assertEquals("INVALID_LINK", post("/api/v0/eq/preset/import", """{"link":"auracurve://preset?v=1&d=broken"}""").code())
            val huge = "x".repeat(17 * 1024)
            val tooBig = post("/api/v0/eq/preset/import", """{"link":"$huge"}""")
            assertEquals(HttpStatusCode.BadRequest, tooBig.status)
            assertEquals("BAD_REQUEST", tooBig.code())
        }

    @Test
    fun `an oversized chunked body without Content-Length is refused`() =
        api { _ ->
            val chunked =
                object : OutgoingContent.WriteChannelContent() {
                    override val contentType = ContentType.Application.Json

                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        channel.writeFully("""{"link":"""".toByteArray())
                        // 17 KB in 1 KB pieces, no length announced: Ktor sends it chunked.
                        repeat(17) { channel.writeFully(ByteArray(1024) { 'x'.code.toByte() }) }
                        channel.writeFully(""""}""".toByteArray())
                    }
                }
            val tooBig =
                client.post("/api/v0/eq/preset/import") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    setBody(chunked)
                }
            assertEquals(HttpStatusCode.BadRequest, tooBig.status)
            assertEquals("BAD_REQUEST", tooBig.code())
        }

    @Test
    fun `select by preset id, save, reset`() =
        api { eq ->
            post("/api/v0/eq/preset", """{"presetId":"${BuiltInPresets.DeepRumble.id}"}""")
            assertEquals(BuiltInPresets.DeepRumble.id, eq.activePreset.value.id)
            assertEquals("UNKNOWN_VALUE", post("/api/v0/eq/preset", """{"presetId":"nope"}""").code())

            post("/api/v0/eq/band", """{"index":0,"gainDb":5}""")
            assertTrue(eq.isDirty.value)
            post("/api/v0/eq/reset", "")
            assertTrue(!eq.isDirty.value)

            val saved = post("/api/v0/eq/preset/save", """{"name":"Car night"}""")
            assertEquals(HttpStatusCode.Created, saved.status)
            assertEquals("Car night", eq.activePreset.value.name)
            assertEquals("BAD_REQUEST", post("/api/v0/eq/preset/save", """{"name":"   "}""").code())
            assertEquals("BAD_REQUEST", post("/api/v0/eq/preset/save", """{"name":"${"n".repeat(41)}"}""").code())
        }

    @Test
    fun `high resolution is refused on a device without the pre-eq`() {
        val refusing = FakeAudioEngine(FakeAudioEngine.defaultFakeCapabilities().copy(preEqUnavailableReason = "refused"))
        api(controller(refusing)) { eq ->
            assertEquals("SOURCE_UNAVAILABLE", post("/api/v0/eq/high-resolution", """{"enabled":true}""").code())
            assertEquals(HttpStatusCode.OK, post("/api/v0/eq/high-resolution", """{"enabled":false}""").status)
            assertTrue(!eq.processingSettings.value.highResolutionEq)
        }
        val noDynamics = FakeAudioEngine(AudioCapabilities(totalEffectCount = 1, hasEqualizer = true))
        api(controller(noDynamics)) {
            assertEquals("SOURCE_UNAVAILABLE", post("/api/v0/eq/high-resolution", """{"enabled":true}""").code())
        }
    }

    @Test
    fun `a remote comparison ends after 120 s and when the last client leaves`() =
        runTest {
            val eq = controller()
            val remote = EqRemoteApi(eq, FakeTextProvider(), Dispatchers.Unconfined, scope = this)

            fun body(side: String?) = Json.parseToJsonElement("""{"side":${side?.let { "\"$it\"" } ?: "null"}}""").jsonObject

            remote.setCompare(body("ORIGINAL"))
            assertEquals(CompareSide.ORIGINAL, eq.compare.value?.side)
            advanceTimeBy(EqRemoteApi.COMPARE_TIMEOUT_MS - 1)
            runCurrent()
            assertEquals(CompareSide.ORIGINAL, eq.compare.value?.side)
            advanceTimeBy(2)
            runCurrent()
            assertNull("ended by the safety net", eq.compare.value)

            remote.setCompare(body("EQ"))
            remote.onAllClientsGone()
            assertNull("ended when the last web remote left", eq.compare.value)

            remote.setCompare(body("ORIGINAL"))
            val state = remote.setCompare(body(null))
            assertNull(eq.compare.value)
            assertEquals("null", state["compare"].toString())

            // A comparison held in the app is not the web remote's to end.
            eq.setCompareSide(CompareSide.ORIGINAL)
            remote.onAllClientsGone()
            assertEquals(CompareSide.ORIGINAL, eq.compare.value?.side)
            eq.endCompare()
        }
}
