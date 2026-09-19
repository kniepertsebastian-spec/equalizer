package com.hardbasseq.eq.autoeq

import com.hardbasseq.eq.preset.Preset
import com.hardbasseq.eq.preset.PresetMetadata
import com.hardbasseq.eq.preset.TargetPoint

object AutoEqParser {

    fun parseAutoEqText(presetName: String, text: String): Result<Preset> {
        return runCatching {
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
            val maxPosGain = sortedPoints.maxOf { it.gainDb }.coerceAtLeast(0f)

            Preset(
                id = "autoeq_${presetName.lowercase().replace("\\s+".toRegex(), "_")}",
                name = presetName,
                targetCurve = sortedPoints,
                requestedHeadroomDb = (maxPosGain + 1.0f).coerceAtLeast(0f),
                metadata = PresetMetadata(genre = "autoeq", builtIn = false)
            )
        }
    }
}
