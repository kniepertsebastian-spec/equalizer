package com.auracurve.app.preset

import com.auracurve.app.dsp.EqualizerInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/** One "when is this preset a good choice" statement; the UI turns each into a sentence. */
enum class PresetUsageHint {
    /** No coloration at all: a reference, or a base for a correction profile only. */
    NEUTRAL,

    /** A boost of [PresetUsageAdvisor.VERY_STRONG_BOOST_DB] or more: loud listening on capable speakers. */
    VERY_STRONG_BOOST,

    /** The low end peaks at the kick (90-160 Hz) rather than the sub: punchy, booms less in a car. */
    KICK_FOCUS,

    /** The low end peaks in the sub (30-60 Hz): for headphones and systems that reach that deep. */
    SUB_FOCUS,

    /** Loudness compensation is on: made for low volume. */
    QUIET_LISTENING,

    /** Virtual bass is on: for in-ears and small speakers (built-in player only). */
    SMALL_DRIVERS,

    /** The upper mids (2.5-6 kHz) are cut: for harsh, distorted tracks or bright earbuds. */
    SOFTENED_HIGHS,
}

/**
 * What [PresetUsageAdvisor] reads out of a preset. [strongestBoostDb] is the largest gain of the
 * curve with its macros (0 when nothing is boosted): the built-in player lowers everything else by
 * that much to keep the boost from clipping, so it is worth telling the listener.
 */
data class PresetUsage(
    val hints: List<PresetUsageHint>,
    val strongestBoostDb: Float,
)

/**
 * Derives usage hints from a preset's own values, so every preset gets them - built-in, imported
 * from a link or made by the user - without a description field in the preset format. The curve is
 * resolved with its macros exactly as the equalizer does (EqualizerInterpolator), on a dense log grid.
 */
object PresetUsageAdvisor {
    const val VERY_STRONG_BOOST_DB = 9f
    private const val FOCUS_MIN_DB = 3f
    private const val FOCUS_MARGIN_DB = 0.5f
    private const val SOFTENED_HIGHS_DB = -1f
    private const val NEUTRAL_TOLERANCE_DB = 0.5f

    fun describe(preset: Preset): PresetUsage {
        val curve = preset.targetCurve.sortedBy { it.frequencyHz }
        val gains =
            GRID_HZ.map { hz ->
                hz to
                    EqualizerInterpolator.resolveGainAtFrequency(
                        hz,
                        curve,
                        preset.macroBassDb,
                        preset.macroPunchDb,
                        preset.macroHaerteDb,
                    )
            }

        fun peakIn(range: ClosedFloatingPointRange<Float>) = gains.filter { it.first in range }.maxOf { it.second }

        val strongest = max(0f, gains.maxOf { it.second })
        val flat = gains.all { abs(it.second) < NEUTRAL_TOLERANCE_DB }
        val hints = mutableListOf<PresetUsageHint>()

        if (flat && preset.loudnessMaxBoostDb <= 0f && preset.virtualBassMix <= 0f) {
            return PresetUsage(listOf(PresetUsageHint.NEUTRAL), 0f)
        }
        // The kick window starts above the Punch macro's lower edge (70 Hz): a sub shelf still
        // reaches 70 Hz on its way down and would otherwise always tie with the kick.
        val sub = peakIn(30f..60f)
        val kick = peakIn(90f..160f)
        if (kick >= FOCUS_MIN_DB && kick >= sub + FOCUS_MARGIN_DB) {
            hints += PresetUsageHint.KICK_FOCUS
        } else if (sub >= FOCUS_MIN_DB && sub >= kick + FOCUS_MARGIN_DB) {
            hints += PresetUsageHint.SUB_FOCUS
        }
        if (strongest >= VERY_STRONG_BOOST_DB) hints += PresetUsageHint.VERY_STRONG_BOOST
        if (preset.loudnessMaxBoostDb > 0f) hints += PresetUsageHint.QUIET_LISTENING
        if (preset.virtualBassMix > 0f) hints += PresetUsageHint.SMALL_DRIVERS
        if (peakIn(2500f..6000f) <= SOFTENED_HIGHS_DB) hints += PresetUsageHint.SOFTENED_HIGHS
        return PresetUsage(hints, strongest)
    }

    // 1/12 octave from 20 Hz to 20 kHz: fine enough that no macro window (70-160 Hz is the
    // narrowest) falls between two points.
    private val GRID_HZ: List<Float> =
        generateSequence(20f) { it * 2f.pow(1f / 12f) }.takeWhile { it <= 20_000f }.toList()
}
