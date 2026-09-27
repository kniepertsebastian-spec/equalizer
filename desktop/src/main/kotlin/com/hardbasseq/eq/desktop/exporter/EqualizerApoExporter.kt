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

    // Chat feature ("kann man das auch für Win11 mit vollen Funktionen nutzen?"):
    // reference values for approximating the app's multiband compressor and
    // limiter on Windows via a free VST (Cockos ReaComp, part of the free
    // ReaPlugs VST FX Suite - https://www.reaper.fm/reaplugs/), which Equalizer
    // APO can host. ReaComp is single-band, so this can only approximate the
    // app's real 3-band MBC as one wideband stage - the mid-band's own
    // attack/release (AndroidAudioEngine.kt) is used as a representative
    // middle ground. The limiter uses a hard ratio since ReaComp has no true
    // brickwall mode.
    //
    // EXPLICITLY UNVERIFIED: this sandbox has no Windows machine, no Equalizer
    // APO, and no ReaComp to test against, so neither the exact VST-automation
    // config syntax nor ReaComp's parameter names/value scaling for the
    // installed version are confirmed. A wrong directive could make Equalizer
    // APO fail to parse config.txt at all (silencing system audio), so every
    // automated line below is generated already commented out - see
    // desktop/README.md's "Experimentelle Windows-Dynamik" section for the
    // verification procedure before uncommenting anything.
    const val INCLUDE_FILE_NAME_DYNAMICS = "HardBassEQ-Dynamics.txt"
    private val includeLineDynamics = "Include: $INCLUDE_FILE_NAME_DYNAMICS"
    private const val REACOMP_DLL_NAME = "reacomp.dll"
    private const val MBC_ATTACK_MS = 8f
    private const val MBC_RELEASE_MS = 120f
    private const val LIMITER_RATIO = 20f
    private const val LIMITER_ATTACK_MS = 1f
    private const val LIMITER_RELEASE_MS = 40f

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
        ensureIncludeWired(File(configDir, CONFIG_FILE_NAME), includeLine)
        return includeFile
    }

    // Chat feature: experimental, opt-in-only counterpart to generateConfig()/
    // installTo() above - see the class-level comment on why every VST
    // directive this writes is commented out. A separate include file/line
    // (rather than folding into HardBassEQ.txt) so a user can remove or
    // comment out just this one without touching the proven EQ curve export.
    fun generateDynamicsConfig(preset: Preset): String =
        buildString {
            appendLine("# HardBass EQ - EXPERIMENTAL dynamics reference for ${preset.name}")
            appendLine("# This file does NOT change your audio yet - every VST/VSTPlugin line below")
            appendLine("# is commented out. Equalizer APO's VST parameter automation syntax and")
            appendLine("# ReaComp's exact parameter names/value scaling for your installed version")
            appendLine("# are NOT verified (generated without access to a Windows machine or")
            appendLine("# Equalizer APO/ReaComp to test against). See desktop/README.md's")
            appendLine("# \"Experimentelle Windows-Dynamik\" section before uncommenting anything.")
            appendLine("#")
            appendLine("# Requires: ReaPlugs VST FX Suite (free, https://www.reaper.fm/reaplugs/),")
            appendLine("# $REACOMP_DLL_NAME copied into this Equalizer APO config folder.")
            appendLine("#")
            if (preset.mbcEnabled) {
                appendLine("# Compressor (approximates the app's multiband compressor as a single")
                appendLine("# wideband stage - ReaComp itself isn't multiband):")
                appendLine("#   Threshold: ${formatDb(preset.mbcThresholdDb)} dB")
                appendLine("#   Ratio: ${formatDb(preset.mbcRatio)}:1")
                appendLine("#   Attack: ${formatDb(MBC_ATTACK_MS)} ms")
                appendLine("#   Release: ${formatDb(MBC_RELEASE_MS)} ms")
                appendLine(
                    "# VST: $REACOMP_DLL_NAME" +
                        " -- uncomment only after confirming the syntax below matches your setup",
                )
                appendLine(
                    "# VSTPlugin: Library \"$REACOMP_DLL_NAME\" " +
                        "\"threshold\" ${formatDb(preset.mbcThresholdDb)} " +
                        "\"ratio\" ${formatDb(preset.mbcRatio)} " +
                        "\"attack\" ${formatDb(MBC_ATTACK_MS)} " +
                        "\"release\" ${formatDb(MBC_RELEASE_MS)}",
                )
                appendLine("#")
            }
            if (preset.limiter.enabled) {
                appendLine("# Limiter (approximates the app's safety limiter - ReaComp has no true")
                appendLine("# brickwall mode, a hard ratio approximates one):")
                appendLine("#   Threshold: ${formatDb(preset.limiter.thresholdDb)} dB")
                appendLine("#   Ratio: ${formatDb(LIMITER_RATIO)}:1")
                appendLine("#   Attack: ${formatDb(LIMITER_ATTACK_MS)} ms")
                appendLine("#   Release: ${formatDb(LIMITER_RELEASE_MS)} ms")
                appendLine(
                    "# VST: $REACOMP_DLL_NAME" +
                        " -- uncomment only after confirming the syntax below matches your setup",
                )
                appendLine(
                    "# VSTPlugin: Library \"$REACOMP_DLL_NAME\" " +
                        "\"threshold\" ${formatDb(preset.limiter.thresholdDb)} " +
                        "\"ratio\" ${formatDb(LIMITER_RATIO)} " +
                        "\"attack\" ${formatDb(LIMITER_ATTACK_MS)} " +
                        "\"release\" ${formatDb(LIMITER_RELEASE_MS)}",
                )
            }
        }

    fun installDynamicsTo(
        configDir: File,
        preset: Preset,
    ): File {
        configDir.mkdirs()
        val includeFile = File(configDir, INCLUDE_FILE_NAME_DYNAMICS)
        includeFile.writeText(generateDynamicsConfig(preset))
        ensureIncludeWired(File(configDir, CONFIG_FILE_NAME), includeLineDynamics)
        return includeFile
    }

    private fun ensureIncludeWired(
        configTxt: File,
        line: String,
    ) {
        if (!configTxt.exists()) {
            configTxt.writeText(line + System.lineSeparator())
            return
        }
        val existing = configTxt.readText()
        val alreadyWired = existing.lineSequence().any { it.trim() == line }
        if (!alreadyWired) {
            val separator = if (existing.isEmpty() || existing.endsWith("\n")) "" else System.lineSeparator()
            configTxt.appendText(separator + line + System.lineSeparator())
        }
    }

    private fun formatDb(value: Float): String = String.format(Locale.ROOT, "%.2f", value)
}
