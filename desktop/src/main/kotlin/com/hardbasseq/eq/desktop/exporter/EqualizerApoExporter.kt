package com.hardbasseq.eq.desktop.exporter

import com.hardbasseq.eq.desktop.DESKTOP_FILTER_Q
import com.hardbasseq.eq.desktop.VirtualBands
import com.hardbasseq.eq.desktop.automaticPreampDb
import com.hardbasseq.eq.desktop.resolveBandGains
import com.hardbasseq.eq.preset.Preset
import java.io.File
import java.util.Locale

/**
 * Generates and installs an Equalizer APO (Windows) config snippet for a
 * [Preset]. We never overwrite the user's own config.txt directly - instead
 * we write our own include file and wire a single `Include:` line into
 * config.txt once, idempotently, so re-applying a preset or re-running the
 * app never duplicates lines or clobbers anything else the user configured.
 */
object EqualizerApoExporter {
    const val INCLUDE_FILE_NAME = "HardBassEQ.txt"
    private const val CONFIG_FILE_NAME = "config.txt"
    private val includeLine = "Include: $INCLUDE_FILE_NAME"

    fun generateConfig(
        preset: Preset,
        macroBassDb: Float = preset.macroBassDb,
        macroPunchDb: Float = preset.macroPunchDb,
        macroHaerteDb: Float = preset.macroHaerteDb,
    ): String {
        val bandGains = resolveBandGains(preset, macroBassDb, macroPunchDb, macroHaerteDb)
        val preampDb = automaticPreampDb(bandGains)

        return buildString {
            appendLine("# HardBass EQ - generated preset: ${preset.name}")
            appendLine("# Regenerate from the HardBass EQ desktop app instead of editing by hand.")
            appendLine("Preamp: ${formatDb(preampDb)} dB")
            VirtualBands.bands.forEachIndexed { position, band ->
                val gainDb = bandGains[band.index] ?: 0f
                appendLine(
                    "Filter ${position + 1}: ON PK Fc ${band.centerFreqHz} Hz " +
                        "Gain ${formatDb(gainDb)} dB Q ${formatDb(DESKTOP_FILTER_Q)}",
                )
            }
        }
    }

    /**
     * Writes the include file into [configDir] (Equalizer APO's own
     * `config` folder, typically `C:\Program Files\EqualizerAPO\config`)
     * and ensures config.txt includes it. Returns the include file written.
     */
    fun installTo(
        configDir: File,
        preset: Preset,
        macroBassDb: Float = preset.macroBassDb,
        macroPunchDb: Float = preset.macroPunchDb,
        macroHaerteDb: Float = preset.macroHaerteDb,
    ): File {
        configDir.mkdirs()
        val includeFile = File(configDir, INCLUDE_FILE_NAME)
        includeFile.writeText(generateConfig(preset, macroBassDb, macroPunchDb, macroHaerteDb))
        ensureIncludeWired(File(configDir, CONFIG_FILE_NAME))
        return includeFile
    }

    private fun ensureIncludeWired(configTxt: File) {
        if (!configTxt.exists()) {
            configTxt.writeText(includeLine + System.lineSeparator())
            return
        }
        val existing = configTxt.readText()
        val alreadyWired = existing.lineSequence().any { it.trim() == includeLine }
        if (!alreadyWired) {
            val separator = if (existing.isEmpty() || existing.endsWith("\n")) "" else System.lineSeparator()
            configTxt.appendText(separator + includeLine + System.lineSeparator())
        }
    }

    private fun formatDb(value: Float): String = String.format(Locale.ROOT, "%.2f", value)
}
