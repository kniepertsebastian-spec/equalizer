package com.auracurve.app.eq

import com.auracurve.app.BuildConfig
import com.auracurve.app.audio.AudioCapabilities
import com.auracurve.app.audio.AudioDeviceType
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.AudioSession
import com.auracurve.app.audio.FakeAudioEngine
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.context.SoundContext
import com.auracurve.app.data.profile.DeviceProfileEntity
import com.auracurve.app.diagnostics.InMemoryDiagnosticsRecorder
import com.auracurve.app.dsp.HeadphonePower
import com.auracurve.app.dsp.ParametricBand
import com.auracurve.app.dsp.PreEqLayout
import com.auracurve.app.preset.BuiltInGenrePresets
import com.auracurve.app.preset.BuiltInPresets
import com.auracurve.app.preset.PresetIntensity
import com.auracurve.app.preset.SoundGoal
import com.auracurve.app.quick.QuickCommand
import com.auracurve.app.quick.QuickControls
import com.auracurve.app.settings.LiveSettings
import com.auracurve.app.text.FakeTextProvider
import com.auracurve.app.ui.main.CompareSide
import com.auracurve.app.ui.main.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * E-008 stage 2, step 2: characterization tests. Each scenario drives the equalizer the way the
 * app does and writes down, after every step, what reached the engine (audioEngine.apply), the
 * built-in player (curve, subsonic, virtual bass) and the store (LiveSettings, route binding).
 * The expected text lives in src/test/resources/e008-golden/ and was recorded against
 * MainViewModel before the EqController refactor; the refactor must not change a single line.
 *
 * A missing golden file is written and the test fails once, so a new scenario is recorded
 * deliberately (and reviewed in the diff) rather than silently.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EqCharacterizationTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Rig(
        val engine: FakeAudioEngine = FakeAudioEngine(),
        val routes: EqFakeRouteRepository = EqFakeRouteRepository(),
        val volume: EqFakeVolumeRepository = EqFakeVolumeRepository(),
        val bridge: EqFakePlayerBridge = EqFakePlayerBridge(),
        val presets: EqFakePresetRepository = EqFakePresetRepository(),
        val settings: EqFakeAppSettingsRepository = EqFakeAppSettingsRepository(),
        val corrections: EqFakeCorrectionProfileRepository = EqFakeCorrectionProfileRepository(),
        val devices: EqFakeDeviceProfileRepository = EqFakeDeviceProfileRepository(),
        val sessions: EqFakeSessionRepository = EqFakeSessionRepository(),
        val car: EqFakeCarConnectionRepository = EqFakeCarConnectionRepository(),
        val quick: QuickControls = QuickControls(),
    ) {
        val log = StringBuilder()
    }

    private fun Rig.viewModel(): MainViewModel =
        MainViewModel(
            repository = EqFakeEffectRepository(),
            audioEngine = engine,
            routeRepository = routes,
            volumeRepository = volume,
            diagnosticsRecorder = InMemoryDiagnosticsRecorder(),
            playerBridge = bridge,
            presetRepository = presets,
            appSettingsRepository = settings,
            correctionProfileRepository = corrections,
            deviceProfileRepository = devices,
            texts = FakeTextProvider(),
            sessionRepository = sessions,
            carConnectionRepository = car,
            quickControls = quick,
            appProfileRepository = EqFakeAppProfileRepository(),
            backgroundDispatcher = dispatcher,
        )

    private fun Rig.step(
        label: String,
        action: () -> Unit = {},
    ) {
        action()
        dispatcher.scheduler.advanceUntilIdle()
        log.appendLine("## $label")
        log.appendLine("engine: ${engine.currentSettings.value.describe()}")
        log.appendLine(
            "player: vbass=${bridge.lastVirtualBassMix} subsonic=${bridge.lastSubsonicCutoffHz} curve=${bridge.lastPlayerCurve.joinToString(
                " ",
            ) { "%.0f:%.3f".format(Locale.ROOT, it.frequencyHz, it.gainDb) }}",
        )
        log.appendLine("quick: ${quick.state.value}")
        val saved = settings.savedSettings.lastOrNull()
        log.appendLine(
            "saved: " +
                if (saved == null) {
                    "none"
                } else {
                    "preset=${saved.activePresetId} dirty=${saved.isDirty} correction=${saved.activeCorrectionProfileId} " +
                        "context=${saved.activeContext} auto=${saved.activeContextAutomatic} goal=${saved.activeGoal} " +
                        "power=${saved.headphonePower} bands=${saved.parametricBands} settings=${saved.processingSettings.describe()}"
                },
        )
        log.appendLine("binding: ${devices.savedBindings.lastOrNull()}")
    }

    private fun ProcessingSettings.describe(): String = toString()

    private fun Rig.verify(name: String) {
        val file = File("src/test/resources/e008-golden/$name.txt")
        val actual = log.toString()
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.writeText(actual)
            fail("Recorded new golden file ${file.path}; review it and run the test again")
        }
        assertEquals(file.readText(), actual)
    }

    private val savedState =
        LiveSettings(
            activePresetId = BuiltInPresets.DeepRumble.id,
            isDirty = true,
            processingSettings = ProcessingSettings(macroBassDb = 3f, virtualBassMix = 0.2f, subsonicCutoffHz = 30f),
            activeContext = SoundContext.CAR.name,
            activeContextAutomatic = false,
            headphonePower = HeadphonePower(bassDb = 4f),
            activeGoal = SoundGoal.entries.last().id,
            parametricBands = listOf(ParametricBand(frequencyHz = 120f, gainDb = 3f, q = 1.2f)),
        )

    @Test
    fun `restore, then preset changes and quick commands`() =
        runTest {
            val rig = Rig(settings = EqFakeAppSettingsRepository(savedState))
            rig.viewModel()
            rig.step("restored")
            rig.step("quick NEXT_PRESET") { rig.quick.send(QuickCommand.NEXT_PRESET) }
            rig.step("quick TOGGLE_MASTER off") { rig.quick.send(QuickCommand.TOGGLE_MASTER) }
            rig.step("quick TOGGLE_MASTER on") { rig.quick.send(QuickCommand.TOGGLE_MASTER) }
            rig.verify("restore-and-quick")
        }

    @Test
    fun `selections and direct controls`() =
        runTest {
            val rig = Rig()
            val vm = rig.viewModel()
            rig.step("fresh start")
            rig.step("select DeepRumble") { vm.selectPreset(BuiltInPresets.DeepRumble) }
            rig.step("genre x intensity") { vm.selectGenreIntensity(BuiltInGenrePresets.all.first(), PresetIntensity.entries.last()) }
            rig.step("macros") {
                vm.setMacroBass(4f)
                vm.setMacroPunch(-2f)
                vm.setMacroHaerte(1.5f)
            }
            rig.step("band gain") { vm.setBandGain(2, -3f) }
            rig.step("parametric band") {
                vm.addParametricBand()
                vm.updateParametricBand(0, ParametricBand(frequencyHz = 80f, gainDb = 5f, q = 0.8f))
            }
            rig.step("goal and headphone power") {
                vm.setSoundGoal(SoundGoal.entries[1])
                vm.setHeadphonePower(HeadphonePower(bassDb = 6f, easeDynamics = false))
            }
            rig.step("context features") {
                vm.setLoudnessCompensationEnabled(true)
                vm.setSubsonicFilterEnabled(true)
                vm.setVirtualBassEnabled(true)
            }
            rig.step("context mode speaker") { vm.setContextMode(SoundContext.BLUETOOTH_SPEAKER) }
            rig.step("bypass") { vm.setBypass(true) }
            rig.step("bypass off, reset") {
                vm.setBypass(false)
                vm.resetToActivePreset()
            }
            rig.step("master off") { vm.setMasterEnabled(false) }
            rig.verify("selections-and-controls")
        }

    @Test
    fun `route changes between car, headphones and speaker`() =
        runTest {
            val headphones = AudioRoute(type = AudioDeviceType.BLUETOOTH, name = "Sony WH-1000XM4", id = "bt_sony")
            val car = AudioRoute(type = AudioDeviceType.CAR, name = "Audi MMI", id = "car_audi")
            val bound =
                EqFakeDeviceProfileRepository(
                    mapOf(
                        "bt_sony" to
                            DeviceProfileEntity(
                                routeId = "bt_sony",
                                routeType = "BLUETOOTH",
                                displayName = "Sony",
                                boundPresetId = BuiltInPresets.Flat.id,
                                headphoneAcousticsOverride = true,
                            ),
                    ),
                )
            val rig = Rig(devices = bound)
            val vm = rig.viewModel()
            rig.step("speaker")
            rig.step("car") { rig.routes.setRoute(car) }
            rig.step("headphones (bound to Flat)") { rig.routes.setRoute(headphones) }
            rig.step("volume down") { rig.volume.setLevelDb(-30f) }
            rig.step("back to car") { rig.routes.setRoute(car) }
            rig.step("Android Auto connects") { rig.car.carConnected.value = true }
            rig.step("speaker again") { rig.routes.setRoute(AudioRoute()) }
            rig.step("user picks preset on speaker") { vm.selectPreset(BuiltInPresets.DeepRumble) }
            rig.verify("routes")
        }

    @Test
    fun `own player binding and the original vs eq comparison`() =
        runTest {
            val hiRes = FakeAudioEngine(FakeAudioEngine.defaultFakeCapabilities().copy(preEqCutoffsHz = PreEqLayout.CUTOFFS_HZ))
            val rig = Rig(engine = hiRes)
            val vm = rig.viewModel()
            rig.step("no session") { vm.selectPreset(BuiltInPresets.DeepRumble) }
            rig.step("foreign session") { rig.sessions.setActiveSession(AudioSession(7, "com.spotify.music")) }
            rig.step("compare EQ") { vm.setCompareSide(CompareSide.EQ) }
            rig.step("compare ORIGINAL") { vm.setCompareSide(CompareSide.ORIGINAL) }
            rig.step("end compare") { vm.endCompare() }
            rig.step("own player session") { rig.sessions.setActiveSession(AudioSession(9, BuildConfig.APPLICATION_ID)) }
            rig.step("own player compare ORIGINAL") { vm.setCompareSide(CompareSide.ORIGINAL) }
            rig.step("own player end compare") { vm.endCompare() }
            rig.step("capabilities lose the pre-EQ") {
                hiRes.updateCapabilities(AudioCapabilities(bands = FakeAudioEngine.defaultFakeCapabilities().bands))
            }
            rig.verify("player-and-compare")
        }
}
