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
import com.auracurve.app.preset.BuiltInGenrePresets
import com.auracurve.app.preset.BuiltInPlayerPresets
import com.auracurve.app.preset.BuiltInPresets
import com.auracurve.app.preset.GenreUseCase
import com.auracurve.app.preset.GenreUseCaseTuning
import com.auracurve.app.preset.PresetIntensity
import com.auracurve.app.preset.PresetIntensityResolver
import com.auracurve.app.preset.PresetSourceScope
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
import org.junit.Assert.assertNotEquals
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
        presets: EqFakePresetRepository = EqFakePresetRepository(),
    ) = EqController(
        audioEngine = engine,
        routeRepository = routes,
        volumeRepository = EqFakeVolumeRepository(),
        playerBridge = bridge,
        presetRepository = presets,
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

    // --- Roadmap 16: playback source, speaker protection on a source change ------------------

    private val spotify = AudioSession(7, "com.spotify.music")
    private val ownPlayer = AudioSession(9, BuildConfig.APPLICATION_ID)
    private val uptempo = PresetIntensityResolver.resolve(BuiltInGenrePresets.Uptempo, PresetIntensity.MODERATE)

    @Test
    fun `no session means no detected source, not the own player`() =
        runTest(dispatcher) {
            val eq = controller()
            eq.awaitRestored()
            idle()
            assertEquals(ActivePlaybackSource.None, eq.activePlaybackSource.value)

            sessions.setActiveSession(spotify)
            idle()
            assertEquals(ActivePlaybackSource.ExternalApp("com.spotify.music"), eq.activePlaybackSource.value)

            sessions.setActiveSession(ownPlayer)
            idle()
            assertEquals(ActivePlaybackSource.OwnPlayer, eq.activePlaybackSource.value)

            sessions.setActiveSession(null)
            idle()
            assertEquals(ActivePlaybackSource.None, eq.activePlaybackSource.value)
        }

    @Test
    fun `the playing own player is the source even while another app's session is attached`() =
        runTest(dispatcher) {
            val eq = controller()
            eq.awaitRestored()
            sessions.setActiveSession(spotify)
            idle()

            bridge.playing.value = true
            idle()
            assertEquals(ActivePlaybackSource.OwnPlayer, eq.activePlaybackSource.value)

            bridge.playing.value = false
            idle()
            assertEquals(ActivePlaybackSource.ExternalApp("com.spotify.music"), eq.activePlaybackSource.value)
        }

    // P16-1: the system equalizer and the player's PCM equalizer never carry the curve together.
    @Test
    fun `the curve goes either to the system equalizer or to the player, never both`() =
        runTest(dispatcher) {
            val eq = controller()
            eq.awaitRestored()
            sessions.setActiveSession(spotify)
            eq.selectPreset(BuiltInPresets.DeepRumble)
            idle()
            assertTrue(
                "the system path has the curve",
                engine.currentSettings.value.bandGainsDb.values
                    .any { it != 0f },
            )
            assertTrue("the player has none", bridge.lastPlayerCurve.isEmpty())
            assertEquals(0f, bridge.lastVirtualBassMix)

            sessions.setActiveSession(ownPlayer)
            idle()
            assertTrue(
                "the system path is flat",
                engine.currentSettings.value.bandGainsDb.values
                    .all { it == 0f },
            )
            assertTrue("the player has the curve", bridge.lastPlayerCurve.isNotEmpty())

            sessions.setActiveSession(null)
            idle()
            assertTrue("nothing detected: the player is primed", bridge.lastPlayerCurve.isNotEmpty())
        }

    @Test
    fun `switching from Spotify to the own player replaces a Spotify preset by its player counterpart`() =
        runTest(dispatcher) {
            val settings = EqFakeAppSettingsRepository()
            val eq = controller(settings)
            eq.awaitRestored()
            sessions.setActiveSession(spotify)
            idle()
            eq.selectPreset(uptempo)
            idle()
            assertEquals(PresetSourceScope.EXTERNAL_APPS, eq.activePreset.value.metadata.sourceScope)

            sessions.setActiveSession(ownPlayer)
            idle()

            val active = eq.activePreset.value
            assertEquals(BuiltInPlayerPresets.Uptempo.base.id, active.id)
            assertEquals(PresetSourceScope.OWN_PLAYER, active.metadata.sourceScope)
            assertEquals(
                "the player preset's limiter is applied",
                active.limiter.thresholdDb,
                eq.processingSettings.value.limiterThresholdDb,
            )
            assertEquals(active.subsonicCutoffHz, eq.processingSettings.value.subsonicCutoffHz)
            assertEquals(active.id, settings.savedSettings.last().activePresetId)

            // ... and back: the Spotify side gets the genre's Spotify base again.
            sessions.setActiveSession(spotify)
            idle()
            assertEquals(uptempo.id, eq.activePreset.value.id)
        }

    @Test
    fun `a use case keeps its use case on the other side`() =
        runTest(dispatcher) {
            val eq = controller()
            eq.awaitRestored()
            sessions.setActiveSession(spotify)
            idle()
            val loud = GenreUseCaseTuning.apply(BuiltInGenrePresets.Gabber, GenreUseCase.LOUD)
            eq.selectPreset(loud)
            idle()

            bridge.playing.value = true
            idle()

            assertEquals(GenreUseCaseTuning.presetId(BuiltInPlayerPresets.Gabber, GenreUseCase.LOUD), eq.activePreset.value.id)
        }

    @Test
    fun `Flat fits every source and stays on a source change`() =
        runTest(dispatcher) {
            val eq = controller()
            eq.awaitRestored()
            sessions.setActiveSession(spotify)
            eq.selectPreset(BuiltInPresets.Flat)
            idle()

            sessions.setActiveSession(ownPlayer)
            idle()
            assertEquals(BuiltInPresets.Flat.id, eq.activePreset.value.id)

            sessions.setActiveSession(spotify)
            idle()
            assertEquals(BuiltInPresets.Flat.id, eq.activePreset.value.id)
        }

    @Test
    fun `with no source detected any preset can be chosen and stays`() =
        runTest(dispatcher) {
            val eq = controller()
            eq.awaitRestored()
            eq.selectPreset(BuiltInPlayerPresets.Hardtekk.base)
            idle()
            assertEquals(BuiltInPlayerPresets.Hardtekk.base.id, eq.activePreset.value.id)
            eq.selectPreset(uptempo)
            idle()
            assertEquals(uptempo.id, eq.activePreset.value.id)
        }

    // A player-only genre has no Spotify version: the preset last played from Spotify comes back.
    @Test
    fun `without a counterpart the preset last played from that source comes back`() =
        runTest(dispatcher) {
            val settings = EqFakeAppSettingsRepository()
            val eq = controller(settings)
            eq.awaitRestored()
            sessions.setActiveSession(spotify)
            eq.selectPreset(BuiltInPresets.DeepRumble)
            idle()
            assertEquals(BuiltInPresets.DeepRumble.id, settings.savedSettings.last().lastExternalAppsPresetId)

            sessions.setActiveSession(ownPlayer)
            idle()
            eq.selectPreset(BuiltInPlayerPresets.Hardtekk.base)
            idle()
            assertEquals(BuiltInPlayerPresets.Hardtekk.base.id, settings.savedSettings.last().lastOwnPlayerPresetId)

            sessions.setActiveSession(spotify)
            idle()
            assertEquals(BuiltInPresets.DeepRumble.id, eq.activePreset.value.id)
        }

    @Test
    fun `without a counterpart or a remembered preset Flat is the safe fallback`() =
        runTest(dispatcher) {
            val eq = controller()
            eq.awaitRestored()
            eq.selectPreset(BuiltInPlayerPresets.Schranz.base)
            idle()

            sessions.setActiveSession(spotify)
            idle()

            assertEquals(BuiltInPresets.Flat.id, eq.activePreset.value.id)
        }

    @Test
    fun `the remembered presets survive a restart`() =
        runTest(dispatcher) {
            val saved =
                LiveSettings(
                    activePresetId = BuiltInPlayerPresets.Techno.base.id,
                    isDirty = false,
                    processingSettings = ProcessingSettings(),
                    lastExternalAppsPresetId = BuiltInPresets.KickAttack.id,
                )
            val eq = controller(EqFakeAppSettingsRepository(saved))
            eq.awaitRestored()
            idle()

            sessions.setActiveSession(spotify)
            idle()

            assertEquals(BuiltInPresets.KickAttack.id, eq.activePreset.value.id)
        }

    // A route bound to a Spotify preset must not put it on the own player's speakers.
    @Test
    fun `a route binding is fitted to the source playing now`() =
        runTest(dispatcher) {
            val speaker = AudioRoute(type = AudioDeviceType.BLUETOOTH, name = "JBL Charge", id = "bt_jbl")
            val devices =
                EqFakeDeviceProfileRepository(
                    mapOf(
                        "bt_jbl" to
                            DeviceProfileEntity(
                                routeId = "bt_jbl",
                                routeType = "BLUETOOTH",
                                displayName = "JBL",
                                boundPresetId = uptempo.id,
                            ),
                    ),
                )
            val eq = controller(devices = devices)
            eq.awaitRestored()
            sessions.setActiveSession(ownPlayer)
            idle()

            routes.setRoute(speaker)
            idle()

            assertEquals(BuiltInPlayerPresets.Uptempo.base.id, eq.activePreset.value.id)
        }

    @Test
    fun `a quick tile only steps through presets for the source playing now`() =
        runTest(dispatcher) {
            val eq = controller()
            eq.awaitRestored()
            sessions.setActiveSession(ownPlayer)
            idle()

            repeat(5) {
                quick.send(QuickCommand.NEXT_PRESET)
                idle()
                assertTrue(
                    eq.activePreset.value.id,
                    eq.activePreset.value.metadata.sourceScope
                        .fits(PresetSourceScope.OWN_PLAYER),
                )
            }
        }

    @Test
    fun `a new preset is saved for the source chosen in the dialog`() =
        runTest(dispatcher) {
            val presets = EqFakePresetRepository()
            val eq = controller(presets = presets)
            eq.awaitRestored()
            sessions.setActiveSession(ownPlayer)
            idle()
            assertEquals(PresetSourceScope.OWN_PLAYER, eq.defaultScopeForNewPreset())

            val saved = eq.saveAsNewPreset("Mine", PresetSourceScope.ALL)
            idle()
            assertEquals(PresetSourceScope.ALL, saved.metadata.sourceScope)
            assertEquals(
                PresetSourceScope.ALL,
                presets.savedPresets
                    .last()
                    .metadata.sourceScope,
            )

            // Saved for the player only: replaced when Spotify plays (by Flat, it has no counterpart).
            val playerOnly = eq.saveAsNewPreset("Speakers", PresetSourceScope.OWN_PLAYER)
            idle()
            assertEquals(playerOnly.id, eq.activePreset.value.id)
            sessions.setActiveSession(spotify)
            idle()
            assertNotEquals(playerOnly.id, eq.activePreset.value.id)
        }
}
