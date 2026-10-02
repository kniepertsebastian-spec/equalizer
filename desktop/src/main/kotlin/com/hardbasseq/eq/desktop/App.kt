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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.desktop.exporter.EasyEffectsExporter
import com.hardbasseq.eq.desktop.exporter.EqualizerApoExporter
import com.hardbasseq.eq.preset.BuiltInPresets
import com.hardbasseq.eq.preset.PortableSoundProfile
import com.hardbasseq.eq.preset.PortableSoundProfileJson
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
    var phoneProfile by remember { mutableStateOf<PortableSoundProfile?>(null) }
    var phoneProfilePath by remember { mutableStateOf("") }

    var equalizerApoDir by remember { mutableStateOf(defaultEqualizerApoConfigDir()) }
    var easyEffectsDir by remember { mutableStateOf(EasyEffectsExporter.defaultPresetDir().absolutePath) }
    // Chat feature: opt-in, off by default - see EqualizerApoExporter's class-level
    // comment on why the generated dynamics file is reference-only/commented out.
    var includeExperimentalDynamics by remember { mutableStateOf(false) }

    fun selectPreset(preset: Preset) {
        activePreset = preset
        phoneProfile = null
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
                    "Detected system: ${detectedPlatform.label()}. " +
                        "Presets are exported for Equalizer APO (Windows) or EasyEffects (Linux) – " +
                        "both tools must be installed separately.",
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
                MacroSlider("Hardness", macroHaerteDb) { macroHaerteDb = it }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Import profile from phone", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = phoneProfilePath,
                            onValueChange = { phoneProfilePath = it },
                            label = { Text("Path to HardBassEQ-Desktop.json") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                statusMessage =
                                    runCatching {
                                        val imported = PortableSoundProfileJson.import(File(phoneProfilePath).readText())
                                        phoneProfile = imported
                                        activePreset = imported.preset
                                        macroBassDb = imported.macroBassDb
                                        macroPunchDb = imported.macroPunchDb
                                        macroHaerteDb = imported.macroHaerteDb
                                        "Phone profile loaded: ${imported.preset.name}"
                                    }.getOrElse { "Import failed: ${it.message}" }
                            },
                        ) { Text("Load file") }
                        if (phoneProfile != null) {
                            Text("The active phone settings, including correction and dynamics, are exported for EasyEffects.")
                        }
                    }
                }

                Text("Target platform", style = MaterialTheme.typography.titleMedium)
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
                            includeExperimentalDynamics = includeExperimentalDynamics,
                            onIncludeExperimentalDynamicsChange = { includeExperimentalDynamics = it },
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
                                        var message =
                                            "Written to ${file.absolutePath} " +
                                                "(config.txt in this folder now contains " +
                                                "the Include line automatically)."
                                        if (includeExperimentalDynamics) {
                                            val dynamicsFile =
                                                EqualizerApoExporter.installDynamicsTo(File(equalizerApoDir), activePreset)
                                            message +=
                                                " Experimentelle Dynamik-Referenz nach " +
                                                "${dynamicsFile.absolutePath} geschrieben - " +
                                                "alle VST-Zeilen darin sind auskommentiert, siehe README."
                                        }
                                        message
                                    }.getOrElse { "Error: ${it.message}" }
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
                                            phoneProfile?.let {
                                                EasyEffectsExporter.installTo(
                                                    File(easyEffectsDir),
                                                    it,
                                                    macroBassDb,
                                                    macroPunchDb,
                                                    macroHaerteDb,
                                                )
                                            } ?: EasyEffectsExporter.installTo(
                                                File(easyEffectsDir),
                                                activePreset,
                                                macroBassDb,
                                                macroPunchDb,
                                                macroHaerteDb,
                                            )
                                        "Written to ${file.absolutePath}. " +
                                            "Select it under Presets in EasyEffects to activate it."
                                    }.getOrElse { "Error: ${it.message}" }
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
    includeExperimentalDynamics: Boolean,
    onIncludeExperimentalDynamicsChange: (Boolean) -> Unit,
    onApply: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = configDir,
            onValueChange = onConfigDirChange,
            label = { Text("Equalizer APO config folder") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = includeExperimentalDynamics, onCheckedChange = onIncludeExperimentalDynamicsChange)
            Column {
                Text("Experimental: compressor/limiter reference (ReaComp-VST)")
                Text(
                    "Untested - only writes commented-out reference values, does not change your " +
                        "sound automatically. See the README before enabling the VST lines.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Button(onClick = onApply) { Text("Install preset") }
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
            label = { Text("EasyEffects preset folder") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onApply) { Text("Export preset") }
    }
}

private fun DesktopPlatform.label(): String =
    when (this) {
        DesktopPlatform.WINDOWS -> "Windows"
        DesktopPlatform.LINUX -> "Linux"
        DesktopPlatform.OTHER -> "Unknown"
    }
