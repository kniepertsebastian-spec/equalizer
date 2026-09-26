package com.hardbasseq.eq.autoeq

import com.hardbasseq.eq.correction.CorrectionProfile
import com.hardbasseq.eq.preset.TargetPoint

// roadmap-2026.md M5: an AutoEQ file describes a headphone/speaker CORRECTION
// curve ("Mein Kopfhörer", M3), meant to be combined with a separate voicing
// preset ("Klangstil") via CurveComposer - not a Preset/VoicingPreset itself, so
// this returns CorrectionProfile.
//
// Moved here from :app (session with BuiltInAutoEqCatalog/AutoEqCatalogMatcher):
// pure Kotlin, no Android dependency, and now used by :core's own
// BuiltInAutoEqCatalog to parse its bundled GraphicEQ data - :app still uses it
// unchanged via the existing :core dependency (same package, same API).
object AutoEqParser {
    fun parseAutoEqText(
        profileName: String,
        text: String,
    ): Result<CorrectionProfile> =
        runCatching {
            val targetPoints = mutableListOf<TargetPoint>()

            // AutoEQ GraphicEQ format line example: "GraphicEQ: 20 0; 40 1.5; 80 2.5; 160 -1.0"
            val graphicEqLine = text.lines().firstOrNull { it.trim().startsWith("GraphicEQ:", ignoreCase = true) }

            if (graphicEqLine != null) {
                val content = graphicEqLine.substringAfter("GraphicEQ:").trim()
                val pairs = content.split(";")
                for (pair in pairs) {
                    val tokens = pair.trim().split("\\s+".toRegex())
                    if (tokens.size >= 2) {
                        val freq = tokens[0].toFloatOrNull()
                        val gain = tokens[1].toFloatOrNull()
                        if (freq != null && gain != null && freq > 0f) {
                            targetPoints.add(TargetPoint(freq, gain.coerceIn(-24f, 24f)))
                        }
                    }
                }
            } else {
                // Try CSV / TSV line by line: "frequency, gain" or "frequency\tgain"
                for (line in text.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.isBlank() || trimmed.startsWith("#")) continue
                    val parts = trimmed.split("[,;\\t\\s]+".toRegex())
                    if (parts.size >= 2) {
                        val freq = parts[0].toFloatOrNull()
                        val gain = parts[1].toFloatOrNull()
                        if (freq != null && gain != null && freq > 0f) {
                            targetPoints.add(TargetPoint(freq, gain.coerceIn(-24f, 24f)))
                        }
                    }
                }
            }

            if (targetPoints.isEmpty()) {
                throw IllegalArgumentException("No valid AutoEQ frequency/gain points found in input")
            }

            val sortedPoints = targetPoints.sortedBy { it.frequencyHz }

            CorrectionProfile(
                id = "autoeq_${profileName.lowercase().replace("\\s+".toRegex(), "_")}",
                name = profileName,
                curve = sortedPoints,
                sourceLabel = "AutoEQ-Import",
                builtIn = false,
            )
        }
}
