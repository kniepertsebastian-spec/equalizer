package com.auracurve.app.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.auracurve.app.dsp.HeadroomWarningLevel
import com.auracurve.app.preset.TargetPoint
import com.auracurve.app.ui.components.cyber.CyberBottomBar
import com.auracurve.app.ui.components.cyber.CyberCard
import com.auracurve.app.ui.components.cyber.CyberNavItem
import com.auracurve.app.ui.components.cyber.CyberOutlineButton
import com.auracurve.app.ui.components.cyber.CyberPill
import com.auracurve.app.ui.components.cyber.CyberPlayKey
import com.auracurve.app.ui.components.cyber.CyberPowerKey
import com.auracurve.app.ui.components.cyber.CyberRoundButton
import com.auracurve.app.ui.components.cyber.CyberSegmented
import com.auracurve.app.ui.components.cyber.CyberTextField
import com.auracurve.app.ui.components.cyber.CyberToggle
import com.auracurve.app.ui.components.cyber.LimiterLedMeter
import com.auracurve.app.ui.components.cyber.RadialVisualizer
import com.auracurve.app.ui.components.cyber.RotaryKnob
import com.auracurve.app.ui.components.cyber.SpectrumEqGraph
import com.auracurve.app.ui.components.cyber.SpectrumMode
import com.auracurve.app.ui.components.cyber.WaveformSeekbar
import com.auracurve.app.ui.equalizer.GraphBand
import com.auracurve.app.ui.theme.AuraCurveTheme
import com.auracurve.app.ui.theme.CyberColors
import com.auracurve.app.ui.theme.cyberBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// The Cyber components on their own, before they are put into the screens, for looking at:
// writes app/build/mockup-screenshots/components-*.png like MockupScreensTest. Animations are
// held at their first frame by the test clock, so the moving parts show their resting look.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h873dp-xxhdpi")
class CyberComponentsGalleryTest {
    @get:Rule
    val compose = createComposeRule()

    private val bands =
        listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f).mapIndexed { i, hz ->
            val gains = listOf(4f, 5.5f, 3f, -2.5f, -4f, -1f, 1.5f, 3f, 2f, -0.5f)
            GraphBand(index = i, frequencyHz = hz, gainDb = gains[i], minGainDb = -12f, maxGainDb = 12f)
        }

    @Test
    fun controls() {
        show {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberPill("Bass Boost", selected = true, onClick = {})
                CyberPill("Vocal", selected = false, onClick = {})
                CyberPill("Off", selected = false, onClick = {}, enabled = false)
            }
            CyberSegmented(listOf("Sound", "Fine-tune", "Device"), selectedIndex = 1, onSelect = {})
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CyberToggle(checked = true, onCheckedChange = {})
                CyberToggle(checked = false, onCheckedChange = {})
                CyberPowerKey(checked = true, onCheckedChange = {}, description = "EQ")
                CyberPowerKey(checked = false, onCheckedChange = {}, description = "EQ")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                LimiterLedMeter(null)
                LimiterLedMeter(HeadroomWarningLevel.SUFFICIENT)
                LimiterLedMeter(HeadroomWarningLevel.OCCASIONAL_LIMITING)
                LimiterLedMeter(HeadroomWarningLevel.HEAVY_LIMITING)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CyberRoundButton(onClick = {}) { Icon(Icons.Default.Tune, null) }
                CyberRoundButton(onClick = {}, selected = true) { Icon(Icons.Default.Tune, null) }
                CyberPlayKey(playing = true, enabled = true, onClick = {}) { Icon(Icons.Default.Pause, null, Modifier.size(36.dp)) }
                CyberRoundButton(onClick = {}, enabled = false) { Icon(Icons.Default.SkipNext, null) }
            }
            CyberTextField(value = "", onValueChange = {}, placeholder = "Search tracks", leadingIcon = Icons.Default.Search)
            CyberOutlineButton(text = "Import EQ", onClick = {})
            CyberCard(hud = true) {
                Text("Card with HUD corners", style = MaterialTheme.typography.titleMedium)
                Text("Glass surface, gradient border", color = CyberColors.mutedText)
            }
        }
        save("components-controls")
    }

    @Test
    fun knobsAndGraph() {
        show {
            CyberCard(
                contentPadding = PaddingValues(8.dp),
            ) {
                SpectrumEqGraph(
                    curve = emptyList(),
                    bands = bands,
                    selectedIndex = 1,
                    onSelect = {},
                    onGainChange = { _, _ -> },
                    processing = true,
                    stateLabel = "Active",
                    hotFrequencyHz = null,
                    description = "graph",
                    hint = "Drag a point",
                    spectrum = SpectrumMode.LIVE,
                )
            }
            SpectrumEqGraph(
                curve = listOf(TargetPoint(60f, 3f), TargetPoint(300f, -4.5f), TargetPoint(3500f, 2f)),
                bands = emptyList(),
                selectedIndex = null,
                onSelect = {},
                onGainChange = { _, _ -> },
                processing = false,
                stateLabel = "Bypassed",
                hotFrequencyHz = null,
                description = "graph",
                height = 140.dp,
                collapsed = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val ring = listOf(CyberColors.orchid, CyberColors.violet, CyberColors.cyan)
                RotaryKnob(4.5f, -6f..6f, {}, ring, "Bass", "+4.5 dB", Modifier.weight(1f), knobSize = 104.dp)
                RotaryKnob(-2f, -6f..6f, {}, ring, "Punch", "−2.0 dB", Modifier.weight(1f), knobSize = 104.dp)
                RotaryKnob(1f, -2f..2f, {}, ring, "Harshness", "+1.0 dB", Modifier.weight(1f), knobSize = 104.dp, enabled = false)
            }
        }
        save("components-graph-knobs")
    }

    @Test
    fun player() {
        show {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                RadialVisualizer(playing = true, seed = 42L, modifier = Modifier.size(320.dp))
                Box(
                    modifier = Modifier.size(180.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Cover", color = CyberColors.mutedText) }
            }
            WaveformSeekbar(positionMs = 74_000, durationMs = 214_000, enabled = true, onSeek = {}, seed = 42L, playing = true)
            WaveformSeekbar(positionMs = 0, durationMs = 0, enabled = false, onSeek = {}, seed = 7L)
            RadialVisualizer(playing = false, seed = 7L, modifier = Modifier.size(140.dp))
        }
        save("components-player")
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            AuraCurveTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.fillMaxSize().cyberBackdrop()) {
                        Column(
                            modifier = Modifier.weight(1f).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) { content() }
                        CyberBottomBar(
                            items =
                                listOf(
                                    CyberNavItem("player", "Player", Icons.Default.LibraryMusic),
                                    CyberNavItem("eq", "Equalizer", Icons.Default.GraphicEq),
                                ),
                            selectedRoute = "eq",
                            onSelect = {},
                        )
                    }
                }
            }
        }
    }

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/mockup-screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
