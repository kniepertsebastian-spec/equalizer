package com.auracurve.app.eq

import com.auracurve.app.audio.AudioDeviceType
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.FakeAudioEngine
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.data.profile.DeviceProfileEntity
import com.auracurve.app.preset.BuiltInPresets
import com.auracurve.app.quick.QuickCommand
import com.auracurve.app.quick.QuickControls
import com.auracurve.app.settings.AppSettingsRepository
import com.auracurve.app.settings.LiveSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** E-008 stage 2: the equalizer is steered without any MainViewModel (app closed). */
@OptIn(ExperimentalCoroutinesApi::class)
class EqControllerTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val engine = FakeAudioEngine()
    private val routes = EqFakeRouteRepository()
    private val bridge = EqFakePlayerBridge()
    private val quick = QuickControls()

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun controller(
        settings: AppSettingsRepository = EqFakeAppSettingsRepository(),
        devices: EqFakeDeviceProfileRepository = EqFakeDeviceProfileRepository(),
    ) = EqController(
        audioEngine = engine,
        routeRepository = routes,
        volumeRepository = EqFakeVolumeRepository(),
        playerBridge = bridge,
        presetRepository = EqFakePresetRepository(),
        appSettingsRepository = settings,
        correctionProfileRepository = EqFakeCorrectionProfileRepository(),
        deviceProfileRepository = devices,
        sessionRepository = EqFakeSessionRepository(),
        carConnectionRepository = EqFakeCarConnectionRepository(),
        quickControls = quick,
        appProfileRepository = EqFakeAppProfileRepository(),
        scope = scope,
    )

    private fun idle() = dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `restores the saved sound onto the engine without a screen`() =
        runTest(dispatcher) {
            val saved =
                LiveSettings(
                    activePresetId = BuiltInPresets.DeepRumble.id,
                    isDirty = false,
                    processingSettings = ProcessingSettings(macroBassDb = 5f, virtualBassMix = 0.3f),
                )
            val eq = controller(EqFakeAppSettingsRepository(saved))
            assertFalse(eq.isRestored)

            eq.awaitRestored()
            idle()

            assertTrue(eq.isRestored)
            assertEquals(BuiltInPresets.DeepRumble.id, eq.activePreset.value.id)
            assertEquals(5f, engine.currentSettings.value.macroBassDb)
            assertEquals("the player gets the same", engine.currentSettings.value.virtualBassMix, bridge.lastVirtualBassMix)
            assertTrue("the quick tiles can send to it", quick.hasConsumer)
        }

    @Test
    fun `a quick tile steps to the next preset and it is saved`() =
        runTest(dispatcher) {
            val settings = EqFakeAppSettingsRepository()
            val eq = controller(settings)
            idle()
            val ids = eq.allPresets.value.map { it.id }
            val start = eq.activePreset.value.id

            assertTrue(quick.send(QuickCommand.NEXT_PRESET))
            idle()

            val next = ids[(ids.indexOf(start) + 1) % ids.size]
            assertEquals(next, eq.activePreset.value.id)
            assertEquals(next, settings.savedSettings.last().activePresetId)
            assertEquals(eq.activePreset.value.name, quick.state.value?.presetName)
        }

    @Test
    fun `a quick tile switching off stops the player and opens no dialog`() =
        runTest(dispatcher) {
            val eq = controller()
            idle()

            quick.send(QuickCommand.TOGGLE_MASTER)
            idle()

            assertFalse(eq.processingSettings.value.masterEnabled)
            assertFalse(engine.currentSettings.value.masterEnabled)
            assertEquals(1, bridge.stopCount)
            assertEquals(0, bridge.launchCount)
        }

    @Test
    fun `a route change applies the route's sound without a screen`() =
        runTest(dispatcher) {
            val settings = EqFakeAppSettingsRepository()
            val devices =
                EqFakeDeviceProfileRepository(
                    mapOf("car_1" to DeviceProfileEntity("car_1", "CAR", "Car", BuiltInPresets.Flat.id)),
                )
            val eq = controller(settings, devices)
            idle()

            routes.setRoute(AudioRoute(type = AudioDeviceType.CAR, name = "Car", id = "car_1"))
            idle()

            assertEquals(BuiltInPresets.Flat.id, eq.activePreset.value.id)
            assertEquals(BuiltInPresets.Flat.id, settings.savedSettings.last().activePresetId)
            assertEquals(settings.savedSettings.last().processingSettings, engine.currentSettings.value)
        }

    @Test
    fun `a preset change from outside is saved without a screen`() =
        runTest(dispatcher) {
            val settings = EqFakeAppSettingsRepository()
            val eq = controller(settings)
            idle()

            eq.selectPreset(BuiltInPresets.DeepRumble)
            idle()

            val last = settings.savedSettings.last()
            assertEquals(BuiltInPresets.DeepRumble.id, last.activePresetId)
            assertEquals(BuiltInPresets.DeepRumble.macroBassDb, last.processingSettings.macroBassDb)
            assertEquals(last.processingSettings, engine.currentSettings.value)
        }

    @Test
    fun `damaged saved data still ends the restore with the defaults`() =
        runTest(dispatcher) {
            val broken =
                object : AppSettingsRepository {
                    override val liveSettings: Flow<LiveSettings?> = flow { throw IOException("disk") }

                    override suspend fun save(liveSettings: LiveSettings) = Unit
                }
            val eq = controller(broken)
            idle()

            assertTrue(eq.isRestored)
            assertEquals(BuiltInPresets.CleanPunch.id, eq.activePreset.value.id)
        }
}
