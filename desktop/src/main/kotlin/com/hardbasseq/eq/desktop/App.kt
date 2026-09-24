package com.hardbasseq.eq.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.desktop.exporter.EasyEffectsExporter
import com.hardbasseq.eq.desktop.exporter.EqualizerApoExporter
import com.hardbasseq.eq.preset.BuiltInPresets
import com.hardbasseq.eq.preset.Preset
import java.io.File

@Composable
fun App() {
    val detectedPlatform = remember { detectPlatform() }

    var activePreset by remember { mutableStateOf(BuiltInPresets.CleanPunch) }
    var macroBassDb by remember { mutableStateOf(activePreset.macroBassDb) }
    var macroPunchDb by remember { mutableStateOf(activePreset.macroPunchDb) }
    var macroHaerteDb by remember { mutableStateOf(activePreset.macroHaerteDb) }
    var targetPlatform by remember { mutableStateOf(detectedPlatform) }
    var statusMessage by remember { mutableStateOf("") }

    var equalizerApoDir by remember { mutableStateOf(defaultEqualizerApoConfigDir()) }
    var easyEffectsDir by remember { mutableStateOf(EasyEffectsExporter.defaultPresetDir().absolutePath) }

    fun selectPreset(preset: Preset) {
        activePreset = preset
        macroBassDb = preset.macroBassDb
        macroPunchDb = preset.macroPunchDb
        macroHaerteDb = preset.macroHaerteDb
        statusMessage = ""
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("HardBass EQ – Desktop", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Erkanntes System: ${detectedPlatform.label()}. " +
                        "Presets werden für Equalizer APO (Windows) oder EasyEffects (Linux) exportiert – " +
                        "beide Tools müssen separat installiert sein.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Text("Preset", style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BuiltInPresets.all.forEach { preset ->
                        val selected = preset.id == activePreset.id
                        Button(onClick = { selectPreset(preset) }, enabled = !selected) {
                            Text(if (selected) "${preset.name} (aktiv)" else preset.name)
                        }
                    }
                }

                Text("Macros", style = MaterialTheme.typography.titleMedium)
                MacroSlider("Bass", macroBassDb) { macroBassDb = it }
                MacroSlider("Punch", macroPunchDb) { macroPunchDb = it }
                MacroSlider("Härte", macroHaerteDb) { macroHaerteDb = it }

                Text("Ziel-Plattform", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { targetPlatform = DesktopPlatform.WINDOWS },
                        enabled = targetPlatform != DesktopPlatform.WINDOWS,
                    ) { Text("Windows / Equalizer APO") }
                    Button(
                        onClick = { targetPlatform = DesktopPlatform.LINUX },
                        enabled = targetPlatform != DesktopPlatform.LINUX,
                    ) { Text("Linux / EasyEffects") }
                }

                when (targetPlatform) {
                    DesktopPlatform.WINDOWS ->
                        EqualizerApoPanel(
                            configDir = equalizerApoDir,
                            onConfigDirChange = { equalizerApoDir = it },
                            onApply = {
                                statusMessage =
                                    runCatching {
                                        val file =
                                            EqualizerApoExporter.installTo(
                                                File(equalizerApoDir),
                                                activePreset,
                                                macroBassDb,
                                                macroPunchDb,
                                                macroHaerteDb,
                                            )
                                        "Geschrieben nach ${file.absolutePath} " +
                                            "(config.txt in diesem Ordner enthält jetzt " +
                                            "automatisch die Include-Zeile)."
                                    }.getOrElse { "Fehler: ${it.message}" }
                            },
                        )
                    DesktopPlatform.LINUX, DesktopPlatform.OTHER ->
                        EasyEffectsPanel(
                            presetDir = easyEffectsDir,
                            onPresetDirChange = { easyEffectsDir = it },
                            onApply = {
                                statusMessage =
                                    runCatching {
                                        val file =
                                            EasyEffectsExporter.installTo(
                                                File(easyEffectsDir),
                                                activePreset,
                                                macroBassDb,
                                                macroPunchDb,
                                                macroHaerteDb,
                                            )
                                        "Geschrieben nach ${file.absolutePath}. " +
                                            "In EasyEffects unter Presets auswählen, um es zu aktivieren."
                                    }.getOrElse { "Fehler: ${it.message}" }
                            },
                        )
                }

                if (statusMessage.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(statusMessage, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text("$label: ${"%.1f".format(value)} dB")
        Slider(value = value, onValueChange = onValueChange, valueRange = -5f..5f)
    }
}

@Composable
private fun EqualizerApoPanel(
    configDir: String,
    onConfigDirChange: (String) -> Unit,
    onApply: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = configDir,
            onValueChange = onConfigDirChange,
            label = { Text("Equalizer APO config-Ordner") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onApply) { Text("Preset installieren") }
    }
}

@Composable
private fun EasyEffectsPanel(
    presetDir: String,
    onPresetDirChange: (String) -> Unit,
    onApply: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = presetDir,
            onValueChange = onPresetDirChange,
            label = { Text("EasyEffects Preset-Ordner") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onApply) { Text("Preset exportieren") }
    }
}

private fun DesktopPlatform.label(): String =
    when (this) {
        DesktopPlatform.WINDOWS -> "Windows"
        DesktopPlatform.LINUX -> "Linux"
        DesktopPlatform.OTHER -> "Unbekannt"
    }
