package com.auracurve.app.ui.equalizer

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.height
import com.auracurve.app.R
import com.auracurve.app.audio.AudioDeviceType
import com.auracurve.app.audio.AudioEngineState
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.EqualizerBandCapabilities
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.correction.BuiltInCorrectionProfiles
import com.auracurve.app.dsp.HeadphonePower
import com.auracurve.app.preset.BuiltInGenrePresets
import com.auracurve.app.preset.BuiltInGenreVariants
import com.auracurve.app.preset.BuiltInPresets
import com.auracurve.app.preset.GenrePresetGroups
import com.auracurve.app.preset.GenreUseCase
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.SoundGoal
import com.auracurve.app.preset.TargetPoint
import com.auracurve.app.ui.theme.AuraCurveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// AuraCurve roadmap 04, part A (docs/EQUALIZER_REDESIGN.md): the equalizer screen renders with
// realistic data, the curve stays in the fixed head whatever tab is open, and every section of the
// old single list is still reachable in a tab or the signal chain sheet. Screenshots go to
// app/build/equalizer-screenshots/ (not compared, nothing is checked in). A plain Application: the
// Hilt app is not needed here.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EqualizerScreenUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val bands =
        listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000).mapIndexed { index, hz ->
            EqualizerBandCapabilities(index = index, centerFreqHz = hz, minGainDb = -15f, maxGainDb = 15f)
        }

    private val settings =
        ProcessingSettings(
            bandGainsDb = mapOf(0 to 6f, 1 to 4.5f, 2 to 2f, 3 to 0f, 4 to -1f, 5 to 0f, 6 to 1.5f, 7 to 2f, 8 to 1f, 9 to 0f),
        )

    private fun text(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun showEqualizer(
        state: AudioEngineState = AudioEngineState.Active(sessionId = 42),
        activePreset: Preset = BuiltInPresets.all.first(),
        customPresets: List<Preset> = emptyList(),
        deviceTabFooter: (@Composable ColumnScope.() -> Unit)? = null,
    ) {
        compose.setContent {
            AuraCurveTheme {
                EqualizerScreen(
                    state = state,
                    route = AudioRoute(AudioDeviceType.BLUETOOTH, "Sony WH-1000XM5", "bt_sony"),
                    settings = settings,
                    bands = bands,
                    activePreset = activePreset,
                    customPresets = customPresets,
                    genrePresets = BuiltInGenrePresets.all,
                    moreIntensity = false,
                    activeCorrectionProfile = BuiltInCorrectionProfiles.None,
                    allCorrectionProfiles = listOf(BuiltInCorrectionProfiles.None),
                    suggestedCorrectionProfile = null,
                    effectiveHeadphoneAcoustics = true,
                    onOpenServices = {},
                    activeGoal = SoundGoal.entries.first(),
                    onGoalSelected = {},
                    pathLabel = "Spotify",
                    compare = null,
                    onStartCompare = {},
                    onCompareSide = {},
                    onEndCompare = {},
                    headphonePower = HeadphonePower(),
                    activeContext = null,
                    currentLevelDb = -12f,
                    isDirty = false,
                    onMasterToggled = {},
                    onOpenSourcePicker = {},
                    onBypassToggled = {},
                    onPresetSelected = {},
                    onMoreIntensityChanged = {},
                    onCorrectionProfileSelected = {},
                    onImportCorrectionProfileRequested = {},
                    onExportCorrectionProfile = {},
                    onAcceptSuggestedCorrectionProfile = {},
                    onDismissSuggestedCorrectionProfile = {},
                    onHeadphoneAcousticsChanged = {},
                    onHeadphonePowerChanged = {},
                    onContextModeChanged = {},
                    onLoudnessCompensationChanged = {},
                    onSubsonicFilterChanged = {},
                    onVirtualBassChanged = {},
                    onResetToActivePreset = {},
                    onSaveAsNewRequest = {},
                    onDuplicatePreset = {},
                    onRenamePresetRequest = {},
                    onDeletePresetRequest = {},
                    onSharePreset = {},
                    onImportPresetLinkRequested = {},
                    onMacroBassChanged = {},
                    onMacroPunchChanged = {},
                    onMacroHaerteChanged = {},
                    onBandGainChanged = { _, _ -> },
                    onRetryAttach = {},
                    deviceTabFooter = deviceTabFooter,
                )
            }
        }
    }

    // Every section of the former single list, by its title, and where it lives now. The redesign
    // moved them; it must not drop one.
    private val sectionsByTab =
        mapOf(
            R.string.eq_tab_sound to
                listOf(R.string.genre_title, R.string.modules_title, R.string.goal_title, R.string.compare_title),
            R.string.eq_tab_fine_tune to listOf(R.string.fine_tune_title, R.string.parametric_title),
            R.string.eq_tab_device to
                listOf(R.string.profiles_title, R.string.correction_title, R.string.headphone_mode_title, R.string.context_title),
        )

    private fun openTab(labelId: Int) {
        compose.onNodeWithText(text(labelId)).performClick()
        compose.waitForIdle()
    }

    private fun assertCurveInHead() = compose.onNodeWithContentDescription(curveDescription, substring = true).assertIsDisplayed()

    // ResponseGraph describes itself with the strongest change; the test data peaks at +6 dB.
    private val curveDescription = "+6.0"

    @Test
    fun everySectionIsInATabAndTheCurveStaysInTheHead() {
        showEqualizer()

        sectionsByTab.entries.forEachIndexed { index, (tabLabel, titles) ->
            openTab(tabLabel)
            titles.forEach { id -> compose.onAllNodesWithText(text(id)).onFirst().assertExists() }
            assertCurveInHead()
            compose.onRoot().saveScreenshot("0${index + 1}-tab-${text(tabLabel).lowercase().replace(' ', '-')}")
        }
    }

    @Test
    fun theHeadroomBadgeOpensTheSignalChain() {
        showEqualizer()

        compose.onNodeWithContentDescription("Open the signal chain", substring = true).performClick()
        compose.waitForIdle()

        compose.onAllNodesWithText(text(R.string.meters_title)).onFirst().assertExists()
        compose.onRoot().saveScreenshot("04-signal-chain-sheet")
    }

    @Test
    fun scrollingATabKeepsTheCurve() {
        showEqualizer()

        openTab(R.string.eq_tab_device)
        compose.onAllNodesWithText(text(R.string.context_title)).onFirst().performScrollTo()
        assertCurveInHead()
    }

    // The whole Sound tab on one tall screen, to check how much of it a normal phone shows
    // without scrolling (411 x 891 dp: everything down to "Bass & loudness").
    @Test
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi")
    fun theWholeSoundTabOnATallScreen() {
        showEqualizer()
        compose.onNodeWithText(text(R.string.compare_title)).assertIsDisplayed()
        compose.onRoot().saveScreenshot("11-sound-tab-whole")
    }

    // A short phone: the head shrinks the curve, the tabs still get room.
    @Test
    @Config(qualifiers = "w360dp-h600dp-xhdpi")
    fun shortScreenUsesTheCompactCurve() {
        showEqualizer()

        assertCurveInHead()
        compose.onNodeWithText(text(R.string.eq_tab_sound)).assertIsDisplayed()
        compose.onRoot().saveScreenshot("05-short-screen")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscapePutsTheHeadBesideTheTabs() {
        showEqualizer()

        assertCurveInHead()
        compose.onNodeWithText(text(R.string.eq_tab_sound)).assertIsDisplayed()
        compose.onRoot().saveScreenshot("06-landscape")
    }

    // Waiting for a session is said once, by the status line; tapping it opens what to do.
    @Test
    fun waitingForASessionRenders() {
        showEqualizer(state = AudioEngineState.Listening)

        compose.onNodeWithText(text(R.string.eq_status_waiting)).assertIsDisplayed()
        compose.onAllNodesWithText(text(R.string.no_session_text)).assertCountEquals(0)
        compose.onRoot().saveScreenshot("07-waiting-for-a-session")

        compose.onNodeWithText(text(R.string.eq_status_waiting)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.no_session_text)).assertIsDisplayed()
    }

    // Scrolling the options down folds the curve to give them the room; a tap brings it back.
    @Test
    fun scrollingTheOptionsFoldsTheCurveAndATapUnfoldsIt() {
        showEqualizer()
        val curve = compose.onNodeWithContentDescription(curveDescription, substring = true)
        val fullHeight = curve.getUnclippedBoundsInRoot().height

        compose.onAllNodesWithText(text(R.string.genre_title)).onFirst().performTouchInput { swipeUp() }
        compose.waitForIdle()
        val foldedHeight = curve.getUnclippedBoundsInRoot().height
        assertTrue("folded $foldedHeight, full $fullHeight", foldedHeight < fullHeight / 2)
        assertCurveInHead()
        compose.onRoot().saveScreenshot("10-curve-folded")

        curve.performClick()
        compose.waitForIdle()
        assertEquals(fullHeight, curve.getUnclippedBoundsInRoot().height)
    }

    // Diagnostics live at the end of the Device tab, not under every tab.
    @Test
    fun theDiagnosticsEntryIsInTheDeviceTabOnly() {
        showEqualizer(deviceTabFooter = { Text(text(R.string.more_tools_show)) })

        compose.onAllNodesWithText(text(R.string.more_tools_show)).assertCountEquals(0)
        openTab(R.string.eq_tab_device)
        compose.onNodeWithText(text(R.string.more_tools_show)).performScrollTo().assertIsDisplayed()
    }

    // The values of the shared "Uptempo – Kick Snap Max" link: kick focus and a +10 dB boost.
    private val kickSnapMax =
        Preset(
            id = "custom_kick_snap_max",
            name = "Uptempo – Kick Snap Max",
            targetCurve =
                listOf(
                    TargetPoint(60f, 3f),
                    TargetPoint(110f, 3f),
                    TargetPoint(300f, -4.5f),
                    TargetPoint(1200f, -1f),
                    TargetPoint(3500f, 2f),
                    TargetPoint(7000f, 0.5f),
                    TargetPoint(12000f, -1f),
                ),
            macroBassDb = 2f,
            macroPunchDb = 5f,
            macroHaerteDb = 1f,
        )

    @Test
    fun presetUsageInfoExplainsTheActivePresetAndEachCustomOne() {
        showEqualizer(activePreset = kickSnapMax, customPresets = listOf(kickSnapMax, BuiltInPresets.DeepRumble.copy(id = "c2")))

        compose
            .onNodeWithContentDescription(text(R.string.preset_usage_active_more))
            .performScrollTo()
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.preset_usage_kick), substring = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.preset_usage_very_strong), substring = true).performScrollTo().assertIsDisplayed()
        compose.onRoot().saveScreenshot("08-preset-usage-inline")

        compose
            .onNodeWithContentDescription(
                RuntimeEnvironment.getApplication().getString(R.string.preset_more_actions, "Uptempo – Deep Rumble"),
            ).performScrollTo()
            .performClick()
        compose.onNodeWithText(text(R.string.preset_usage_menu)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.preset_usage_sub), substring = true).assertIsDisplayed()
        compose.onNode(isDialog()).saveScreenshot("09-preset-usage-dialog")
    }

    // The genre list: collapsed rows per genre, opening into every preset with its "best for" text.
    @Test
    fun genrePresetListOpensAGenreAndShowsWhenEachPresetFits() {
        showEqualizer(activePreset = BuiltInPresets.Flat)
        val uptempo = GenrePresetGroups.all.first { it.genre.id == BuiltInGenrePresets.Uptempo.id }
        val count = uptempo.entries.size

        compose.onAllNodesWithText(GenreUseCase.CAR.description).assertCountEquals(0)
        compose
            .onNodeWithText(RuntimeEnvironment.getApplication().resources.getQuantityString(R.plurals.genre_presets_count, count, count))
            .performScrollTo()
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Deep Rumble").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(GenreUseCase.CAR.description).performScrollTo().assertIsDisplayed()
        compose.onRoot().saveScreenshot("10-genre-preset-list")
    }

    @Test
    fun theGenreOfTheActiveVariantStartsOpen() {
        val car = BuiltInGenreVariants.useCases(BuiltInGenrePresets.Uptempo).first { it.useCase == GenreUseCase.CAR }.preset
        showEqualizer(activePreset = car)

        compose
            .onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.genre_presets_active, "Car"), substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(GenreUseCase.QUIET.description).performScrollTo().assertIsDisplayed()
        compose.onRoot().saveScreenshot("11-genre-preset-active-variant")
    }

    private fun SemanticsNodeInteraction.saveScreenshot(name: String) {
        val bitmap = captureToImage().asAndroidBitmap()
        val dir = File("build/equalizer-screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
