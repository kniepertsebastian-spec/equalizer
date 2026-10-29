package com.hardbasseq.eq.ui.equalizer

import com.hardbasseq.eq.preset.TargetPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

// Pure math behind the response graph: where a frequency sits on the logarithmic
// axis and what gain the drawn curve shows there. Kept free of Compose so it can
// be unit tested on the JVM.
internal object CurveGeometry {
    const val MIN_HZ = 20f
    const val MAX_HZ = 20_000f
    const val MIN_RANGE_DB = 6f
    const val DEFAULT_SCALE_DB = 12f

    private val logMin = log10(MIN_HZ)
    private val logSpan = log10(MAX_HZ) - logMin

    /** 0 at 20 Hz, 1 at 20 kHz, logarithmic in between. */
    fun xFraction(frequencyHz: Float): Float = (log10(frequencyHz.coerceIn(MIN_HZ, MAX_HZ)) - logMin) / logSpan

    /** Inverse of [xFraction]. */
    fun frequencyAt(fraction: Float): Float {
        val exponent = logMin + fraction.coerceIn(0f, 1f) * logSpan
        return 10.0.pow(exponent.toDouble()).toFloat()
    }

    /**
     * Gain the drawn curve shows at [frequencyHz]. Between two points it eases with a
     * cosine (flat at every point, smooth in between), so five EQ bands read as one
     * continuous curve instead of a zig-zag. Outside the first and last point it holds
     * their gain.
     */
    fun gainAt(
        points: List<TargetPoint>,
        frequencyHz: Float,
    ): Float {
        if (points.isEmpty()) return 0f
        val sorted = points.sortedBy { it.frequencyHz }
        if (frequencyHz <= sorted.first().frequencyHz) return sorted.first().gainDb
        if (frequencyHz >= sorted.last().frequencyHz) return sorted.last().gainDb
        val upperIndex = sorted.indexOfFirst { it.frequencyHz >= frequencyHz }
        val lower = sorted[upperIndex - 1]
        val upper = sorted[upperIndex]
        val span = xFraction(upper.frequencyHz) - xFraction(lower.frequencyHz)
        if (span <= 0f) return upper.gainDb
        val t = (xFraction(frequencyHz) - xFraction(lower.frequencyHz)) / span
        val eased = ((1.0 - cos(PI * t)) / 2.0).toFloat()
        return lower.gainDb + (upper.gainDb - lower.gainDb) * eased
    }

    /** Symmetric dB range of the plot: at least [MIN_RANGE_DB], rounded up to whole dB. */
    fun rangeDb(points: List<TargetPoint>): Float {
        val largest = points.maxOfOrNull { abs(it.gainDb) } ?: 0f
        return max(MIN_RANGE_DB, ceil(largest))
    }

    /** The point with the strongest boost or cut, or null for an empty or flat curve. */
    fun strongestChange(points: List<TargetPoint>): TargetPoint? = points.maxByOrNull { abs(it.gainDb) }?.takeIf { it.gainDb != 0f }

    /**
     * Fixed gain scale of the graph: the device's band range when known, else 12 dB. Fixed
     * so that +3 dB looks the same height whichever preset is active.
     */
    fun scaleDb(
        minGainDb: Float?,
        maxGainDb: Float?,
    ): Float {
        if (minGainDb == null || maxGainDb == null) return DEFAULT_SCALE_DB
        return max(MIN_RANGE_DB, ceil(max(abs(minGainDb), abs(maxGainDb))))
    }

    /** Labelled gain ticks from +[rangeDb] to -[rangeDb], always including 0 dB. */
    fun gainTicks(rangeDb: Float): List<Float> {
        val step =
            when {
                rangeDb <= 6f -> 3f
                rangeDb <= 12f -> 6f
                rangeDb <= 15f -> 5f
                else -> 10f
            }
        val count = (rangeDb / step).toInt()
        return (count downTo -count).map { it * step }
    }

    /** Frequencies of the labelled vertical grid lines: 1-2-5 steps per decade. */
    val labelledFrequencies: List<Float> = listOf(20f, 50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10_000f, 20_000f)

    /** Unlabelled grid lines: every remaining whole step of each decade. */
    val minorFrequencies: List<Float> =
        listOf(10f, 100f, 1000f, 10_000f)
            .flatMap { decade -> (1..9).map { it * decade } }
            .filter { it in MIN_HZ..MAX_HZ && it !in labelledFrequencies }

    /** Axis label without unit: "50", "500", "5k". The unit is printed once at the axis. */
    fun axisLabel(frequencyHz: Float): String =
        if (frequencyHz >= 1000f) "${(frequencyHz / 1000f).roundToInt()}k" else frequencyHz.roundToInt().toString()

    /** "+6", "0", "−6" - with a real minus sign, which is as wide as the plus. */
    fun gainTickLabel(gainDb: Float): String =
        when {
            gainDb > 0f -> "+${gainDb.roundToInt()}"
            gainDb < 0f -> "\u2212${abs(gainDb).roundToInt()}"
            else -> "0"
        }

    /** "+4.5 dB", "0.0 dB", "−2.0 dB" for values and readouts. */
    fun formatGain(gainDb: Float): String {
        val rounded = (gainDb * 10f).roundToInt() / 10f
        val sign =
            when {
                rounded > 0f -> "+"
                rounded < 0f -> "\u2212"
                else -> ""
            }
        return sign + String.format("%.1f", abs(rounded)) + " dB"
    }

    /** Index of the frequency nearest to [fraction] on the log axis; null for an empty list. */
    fun nearestIndex(
        frequenciesHz: List<Float>,
        fraction: Float,
    ): Int? = frequenciesHz.indices.minByOrNull { abs(xFraction(frequenciesHz[it]) - fraction) }

    /** "55 Hz", "1.2 kHz", "10 kHz" - short labels for the curve and its description. */
    fun formatFrequency(frequencyHz: Float): String {
        if (frequencyHz < 1000f) return "${frequencyHz.roundToInt()} Hz"
        if (frequencyHz >= 10_000f) return "${(frequencyHz / 1000f).roundToInt()} kHz"
        val tenths = (frequencyHz / 100f).roundToInt()
        return if (tenths % 10 == 0) "${tenths / 10} kHz" else "${tenths / 10}.${tenths % 10} kHz"
    }
}
