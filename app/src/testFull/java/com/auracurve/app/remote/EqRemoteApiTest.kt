package com.auracurve.app.remote

import com.auracurve.app.audio.FakeAudioEngine
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
import com.auracurve.app.preset.BuiltInGenrePresets
import com.auracurve.app.preset.PresetIntensity
import com.auracurve.app.preset.PresetIntensityResolver
import com.auracurve.app.preset.SoundGoal
import com.auracurve.app.quick.QuickControls
import com.auracurve.app.settings.AppSettingsRepository
import com.auracurve.app.settings.LiveSettings
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
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqRemoteApiTest {
    private val token = "eq-token"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val settings = EqFakeAppSettingsRepository()
    private val engine = FakeAudioEngine()

    @After
    fun tearDown() = scope.cancel()

    private fun controller(store: AppSettingsRepository = settings) =
        EqController(
            audioEngine = engine,
            routeRepository = EqFakeRouteRepository(),
            volumeRepository = EqFakeVolumeRepository(),
            playerBridge = EqFakePlayerBridge(),
            presetRepository = EqFakePresetRepository(),
            appSettingsRepository = store,
            correctionProfileRepository = EqFakeCorrectionProfileRepository(),
            deviceProfileRepository = EqFakeDeviceProfileRepository(),
            sessionRepository = EqFakeSessionRepository(),
            carConnectionRepository = EqFakeCarConnectionRepository(),
            quickControls = QuickControls(),
            appProfileRepository = EqFakeAppProfileRepository(),
            scope = scope,
        )

    private fun api(
        eq: EqController = controller(),
        block: suspend ApplicationTestBuilder.(EqController) -> Unit,
    ) = testApplication {
        val remote = EqRemoteApi(eq, FakeTextProvider(), Dispatchers.Unconfined)
        application {
            remoteApi(
                RemoteApiConfig(
                    appVersion = "test",
                    token = { token },
                    systemStatus = { SystemStatus(50, false, "NONE", "SPEAKER", "Speaker") },
                    events = emptyFlow(),
                    webRoot = { null },
                    addressAllowed = { _, _ -> true },
                    eq = remote,
                ),
            )
        }
        block(eq)
    }

    private suspend fun ApplicationTestBuilder.getJson(path: String): HttpResponse =
        client.get(path) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

    private suspend fun ApplicationTestBuilder.postJson(
        path: String,
        body: String,
    ): HttpResponse =
        client.post(path) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun HttpResponse.errorCode(): String = json()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    @Test
    fun `options list the code's values in the app's order`() =
        api {
            val options = getJson("/api/v0/eq/options").json()
            val genres = options["genres"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
            assertEquals(listOf("TERROR", "UPTEMPO", "GABBER", "EARLY", "FRENCHCORE", "DANCE", "POP", "RAP", "FLAT"), genres)
            assertEquals(
                PresetIntensity.entries.map {
                    it.name
                },
                options["intensities"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content },
            )
            assertEquals(
                SoundGoal.entries.map { it.name },
                options["goals"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content },
            )
            assertEquals(
                listOf("CAR", "BLUETOOTH_SPEAKER"),
                options["contexts"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content },
            )
            assertTrue(
                options["genres"]!!.jsonArray.all {
                    it.jsonObject["label"]!!
                        .jsonPrimitive.content
                        .isNotEmpty()
                },
            )
        }

    @Test
    fun `state has the devserver's shape`() =
        api {
            val state = getJson("/api/v0/eq/state").json()
            assertEquals(setOf("masterEnabled", "bypass", "preset", "goal", "context", "contextAuto"), state.keys)
            assertEquals("BALANCED", state["goal"]!!.jsonPrimitive.content)
            assertEquals("true", state["contextAuto"]!!.jsonPrimitive.content)
        }

    @Test
    fun `a preset from the api reaches the controller and is saved`() =
        api { eq ->
            val response = postJson("/api/v0/eq/preset", """{"genre":"UPTEMPO","intensity":"VERY_AGGRESSIVE"}""")
            assertEquals(HttpStatusCode.OK, response.status)
            val preset = response.json()["preset"]!!.jsonObject
            assertEquals("UPTEMPO", preset["genre"]!!.jsonPrimitive.content)
            assertEquals("VERY_AGGRESSIVE", preset["intensity"]!!.jsonPrimitive.content)

            val expected = PresetIntensityResolver.resolve(BuiltInGenrePresets.Uptempo, PresetIntensity.VERY_AGGRESSIVE)
            assertEquals(expected.id, eq.activePreset.value.id)
            assertEquals(expected.id, settings.savedSettings.last().activePresetId)
        }

    @Test
    fun `bypass goal and context reach the controller and are saved`() =
        api { eq ->
            assertEquals("true", postJson("/api/v0/eq/bypass", """{"enabled":true}""").json()["bypass"]!!.jsonPrimitive.content)
            assertTrue(eq.processingSettings.value.bypass)
            assertTrue(engine.currentSettings.value.bypass)

            postJson("/api/v0/eq/goal", """{"goal":"MORE_PUNCH"}""")
            assertEquals(SoundGoal.MORE_PUNCH, eq.activeGoal.value)
            assertEquals(SoundGoal.MORE_PUNCH.id, settings.savedSettings.last().activeGoal)

            val car = postJson("/api/v0/eq/context", """{"context":"CAR"}""").json()
            assertEquals("CAR", car["context"]!!.jsonPrimitive.content)
            assertEquals("false", car["contextAuto"]!!.jsonPrimitive.content)
            assertEquals("CAR", settings.savedSettings.last().activeContext)

            val auto = postJson("/api/v0/eq/context", """{"auto":true}""").json()
            assertEquals("true", auto["contextAuto"]!!.jsonPrimitive.content)
            assertEquals("the speaker route detects no mode", "null", auto["context"].toString())

            postJson("/api/v0/eq/context", """{"context":null}""")
            assertEquals(null, eq.activeContext.value)
        }

    @Test
    fun `unknown values and broken bodies are refused`() =
        api {
            assertEquals("UNKNOWN_VALUE", postJson("/api/v0/eq/preset", """{"genre":"POLKA","intensity":"SOFT"}""").errorCode())
            assertEquals("UNKNOWN_VALUE", postJson("/api/v0/eq/preset", """{"genre":"TERROR","intensity":"NORMAL"}""").errorCode())
            assertEquals("UNKNOWN_VALUE", postJson("/api/v0/eq/goal", """{"goal":"LOUDER"}""").errorCode())
            assertEquals("UNKNOWN_VALUE", postJson("/api/v0/eq/context", """{"context":"BOAT"}""").errorCode())
            assertEquals("UNKNOWN_VALUE", postJson("/api/v0/eq/context", "{}").errorCode())
            val missing = postJson("/api/v0/eq/bypass", "{}")
            assertEquals(HttpStatusCode.BadRequest, missing.status)
            assertEquals("BAD_REQUEST", missing.errorCode())
            assertEquals("BAD_REQUEST", postJson("/api/v0/eq/bypass", """{"enabled":"yes"}""").errorCode())
            assertEquals("BAD_REQUEST", postJson("/api/v0/eq/bypass", "not json").errorCode())
            assertEquals("BAD_REQUEST", postJson("/api/v0/eq/bypass", "[true]").errorCode())
        }

    @Test
    fun `before the saved state is loaded the eq answers 503`() {
        val never =
            object : AppSettingsRepository {
                override val liveSettings: Flow<LiveSettings?> = flow { awaitCancellation() }

                override suspend fun save(liveSettings: LiveSettings) = Unit
            }
        api(controller(never)) {
            val state = getJson("/api/v0/eq/state")
            assertEquals(HttpStatusCode.ServiceUnavailable, state.status)
            assertEquals("ENGINE_NOT_READY", state.errorCode())
            assertEquals("ENGINE_NOT_READY", postJson("/api/v0/eq/bypass", """{"enabled":true}""").errorCode())
        }
    }

    @Test
    fun `eq changes from anywhere are reported`() {
        val eq = controller()
        val remote = EqRemoteApi(eq, FakeTextProvider(), Dispatchers.Unconfined)
        runBlocking {
            assertEquals(
                "BALANCED",
                remote
                    .changes()
                    .first()["goal"]!!
                    .jsonPrimitive.content,
            )
            eq.setSoundGoal(SoundGoal.LESS_HARSH)
            assertEquals(
                "LESS_HARSH",
                remote
                    .changes()
                    .first()["goal"]!!
                    .jsonPrimitive.content,
            )
        }
    }
}
