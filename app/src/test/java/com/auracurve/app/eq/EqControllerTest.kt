package com.auracurve.app.eq

import com.auracurve.app.BuildConfig
import com.auracurve.app.audio.AudioDeviceType
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.AudioSession
import com.auracurve.app.audio.FakeAudioEngine
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.context.SoundContext
import com.auracurve.app.data.profile.DeviceProfileEntity
import com.auracurve.app.dsp.LoudnessMatch
import com.auracurve.app.dsp.ParametricBand
import com.auracurve.app.dsp.ParametricFilter
import com.auracurve.app.dsp.ParametricFilterType
import com.auracurve.app.preset.BuiltInPresets
import com.auracurve.app.quick.QuickCommand
import com.auracurve.app.quick.QuickControls
import com.auracurve.app.settings.AppSettingsRepository
import com.auracurve.app.settings.LiveSettings
import com.auracurve.app.ui.main.CompareSide
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
import kotlin.math.abs

/** E-008 stage 2: the equalizer is steered without any MainViewModel (app closed). */
@OptIn(ExperimentalCoroutinesApi::class)
class EqControllerTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val engine = FakeAudioEngine()
    private val routes = EqFakeRouteRepository()
    private val bridge = EqFakePlayerBridge()
    private val quick = QuickControls()
    private val sessions = EqFakeSessionRepository()

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
        sessionRepository = sessions,
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

    // Roadmap 08, P8-1: the own player took back the whole boost and played 6-9 dB quieter
    // than Spotify with the same preset; now it takes back the same share as the system path.
    @Test
    fun `the player takes back the same share of the boost as the system path`() =
        runTest(dispatcher) {
            val eq = controller()
            eq.awaitRestored()
            eq.selectPreset(BuiltInPresets.DeepRumble)
            idle()
            assertEquals(0.5f, bridge.lastHeadroomSafetyRatio)

            eq.setContextMode(SoundContext.BLUETOOTH_SPEAKER)
            idle()
            assertEquals("speaker", 0.8f, bridge.lastHeadroomSafetyRatio)

            eq.setContextMode(SoundContext.SOUNDBAR)
            idle()
            assertEquals("soundbar", 0.8f, bridge.lastHeadroomSafetyRatio)

            eq.setContextMode(SoundContext.CAR)
            idle()
            assertEquals("car", 0.5f, bridge.lastHeadroomSafetyRatio)
        }

    // Roadmap 08, P8-2: a manual band slider was only heard through the system equalizer.
    @Test
    fun `a manual band slider changes the player's curve around that band`() =
        runTest(dispatcher) {
            val eq = controller()
            eq.awaitRestored()
            eq.selectPreset(BuiltInPresets.DeepRumble)
            idle()
            val before = bridge.lastPlayerCurve
            val band =
                engine.capabilities.value.bands
                    .first { it.centerFreqHz == 910 }
            val onCurve =
                eq.processingSettings.value.bandGainsDb
                    .getValue(band.index)

            eq.setBandGain(band.index, onCurve + 6f)
            idle()
            val after = bridge.lastPlayerCurve

            assertEquals(before.map { it.frequencyHz }, after.map { it.frequencyHz })

            fun change(hz: Float): Float {
                val i = after.indices.minBy { abs(after[it].frequencyHz - hz) }
                return after[i].gainDb - before[i].gainDb
            }
            assertTrue("at the band: ${change(910f)} dB", change(910f) in 5f..6.01f)
            assertTrue("an octave away it fades: ${change(1_820f)} dB", abs(change(1_820f)) < 1f)
            assertEquals("far away untouched", 0f, change(100f), 1e-4f)
            assertEquals("far away untouched", 0f, change(10_000f), 1e-4f)

            // Back on the curve, the player follows the curve alone again.
            eq.setBandGain(band.index, onCurve)
            idle()
            assertEquals(before.map { it.gainDb }, bridge.lastPlayerCurve.map { it.gainDb })
        }

    // Roadmap 10, P10-2: the player runs the parametric bands exactly, so they travel as filters
    // and are not sampled into its curve a second time.
    @Test
    fun `parametric bands reach the player as exact filters, not in its curve`() =
        runTest(dispatcher) {
            val eq = controller()
            eq.awaitRestored()
            eq.selectPreset(BuiltInPresets.DeepRumble)
            idle()
            val before = bridge.lastPlayerCurve
            assertTrue(bridge.lastParametricFilters.isEmpty())

            eq.addParametricBand()
            eq.updateParametricBand(0, ParametricBand(frequencyHz = 3_150f, gainDb = 6f, q = 6f))
            eq.addParametricBand()
            eq.updateParametricBand(1, ParametricBand(frequencyHz = 500f, gainDb = -4f, q = 2f, enabled = false))
            idle()

            assertEquals(
                listOf(ParametricFilter(ParametricFilterType.PEAK, 3_150f, 6f, 6f)),
                bridge.lastParametricFilters,
            )
            assertEquals("the curve stays without them", before.map { it.gainDb }, bridge.lastPlayerCurve.map { it.gainDb })

            eq.removeParametricBand(0)
            eq.removeParametricBand(0)
            idle()
            assertTrue(bridge.lastParametricFilters.isEmpty())
        }

    // Roadmap 08, P8-3: with the own player the comparison's loudness match was overwritten by 0 dB.
    @Test
    fun `the comparison's loudness match reaches the engine with the own player`() =
        runTest(dispatcher) {
            val eq = controller()
            eq.awaitRestored()
            sessions.setActiveSession(AudioSession(9, BuildConfig.APPLICATION_ID))
            eq.selectPreset(BuiltInPresets.Flat)
            idle()
            // A strong presence boost: the EQ side is clearly louder than the original.
            val band =
                engine.capabilities.value.bands
                    .first { it.centerFreqHz == 3600 }
            eq.setBandGain(band.index, 12f)
            idle()
            assertEquals("the player applies the headroom itself", 0f, engine.currentSettings.value.inputGainDb)

            eq.setCompareSide(CompareSide.EQ)
            idle()
            val plan = LoudnessMatch.plan(eq.compare.value!!.eqLevelDeltaDb)
            assertTrue("the EQ side needs a cut: ${plan.eqExtraGainDb}", plan.eqExtraGainDb < -0.5f)
            assertEquals(plan.eqExtraGainDb, engine.currentSettings.value.inputGainDb, 1e-4f)
            assertTrue("the player keeps the curve", bridge.lastPlayerCurve.isNotEmpty())

            eq.setCompareSide(CompareSide.ORIGINAL)
            idle()
            assertEquals(plan.originalGainDb, engine.currentSettings.value.inputGainDb, 1e-4f)
            assertTrue("the player is bypassed", bridge.lastPlayerCurve.isEmpty())

            eq.endCompare()
            idle()
            assertEquals(0f, engine.currentSettings.value.inputGainDb)
        }

    @Test
    fun `the original side's loudness match reaches the engine with the own player`() =
        runTest(dispatcher) {
            val eq = controller()
            eq.awaitRestored()
            sessions.setActiveSession(AudioSession(9, BuildConfig.APPLICATION_ID))
            eq.selectPreset(BuiltInPresets.Flat)
            idle()
            // Everything cut: the EQ side is quieter, so the original is turned down to match.
            engine.capabilities.value.bands
                .forEach { eq.setBandGain(it.index, -6f) }
            idle()

            eq.setCompareSide(CompareSide.ORIGINAL)
            idle()
            val plan = LoudnessMatch.plan(eq.compare.value!!.eqLevelDeltaDb)
            assertTrue("the original needs a cut: ${plan.originalGainDb}", plan.originalGainDb < -0.5f)
            assertEquals(plan.originalGainDb, engine.currentSettings.value.inputGainDb, 1e-4f)
            assertTrue("the player is bypassed", bridge.lastPlayerCurve.isEmpty())
        }
}
