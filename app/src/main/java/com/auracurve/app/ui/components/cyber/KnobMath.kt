package com.auracurve.app.ui.components.cyber

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The geometry of [RotaryKnob], free of Compose so it can be tested on the JVM. Angles are in
 * degrees from 12 o'clock, clockwise positive: the dial runs 270° from −135° (lowest value) to
 * +135° (highest), so 0 dB of a symmetric range sits at the top.
 */
internal object KnobMath {
    const val START_DEGREES = -135f
    const val SWEEP_DEGREES = 270f

    /** The dial angle that shows [value]. */
    fun angleFor(
        value: Float,
        range: ClosedFloatingPointRange<Float>,
    ): Float {
        val span = range.endInclusive - range.start
        if (span <= 0f) return START_DEGREES
        val t = (value.coerceIn(range.start, range.endInclusive) - range.start) / span
        return START_DEGREES + t * SWEEP_DEGREES
    }

    /** The value at a dial angle; angles outside the dial clamp to its ends. */
    fun valueAt(
        angle: Float,
        range: ClosedFloatingPointRange<Float>,
    ): Float {
        val t = ((angle - START_DEGREES) / SWEEP_DEGREES).coerceIn(0f, 1f)
        return range.start + t * (range.endInclusive - range.start)
    }

    /** [value] on the nearest multiple of [step], inside [range]. */
    fun snap(
        value: Float,
        step: Float,
        range: ClosedFloatingPointRange<Float>,
    ): Float {
        val snapped = if (step > 0f) (value / step).roundToInt() * step else value
        return snapped.coerceIn(range.start, range.endInclusive)
    }

    /**
     * The LED segments that light up for [value]: from the origin (0 when the range spans it,
     * else the range start) to the value, so a cut lights to the left of the top and a boost to
     * the right. Any value off the origin lights at least the segment it falls in; the origin
     * itself lights none.
     */
    fun litSegments(
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        segments: Int = 24,
    ): IntRange {
        val span = range.endInclusive - range.start
        if (span <= 0f || segments <= 0) return IntRange.EMPTY
        val origin = if (range.start <= 0f && range.endInclusive >= 0f) 0f else range.start
        val o = (origin - range.start) / span * segments
        val v = (value.coerceIn(range.start, range.endInclusive) - range.start) / span * segments
        if (abs(v - o) < EPSILON) return IntRange.EMPTY
        val low = minOf(o, v)
        val high = maxOf(o, v)
        val first = floor(low + EPSILON).toInt().coerceIn(0, segments - 1)
        val last = (ceil(high - EPSILON).toInt() - 1).coerceIn(0, segments - 1)
        return first..last
    }

    /** The value change for a vertical drag of [dyPx]: the full range over [travelPx], up raises. */
    fun dragDelta(
        dyPx: Float,
        travelPx: Float,
        range: ClosedFloatingPointRange<Float>,
    ): Float = if (travelPx <= 0f) 0f else -dyPx / travelPx * (range.endInclusive - range.start)

    /** The angle of a point [dx], [dy] away from the center, in the dial's convention. */
    fun pointerAngle(
        dx: Float,
        dy: Float,
    ): Float = Math.toDegrees(atan2(dx.toDouble(), (-dy).toDouble())).toFloat()

    /**
     * The turn from the finger angle [from] to [to] on the short way round, −180..180: a radial
     * drag adds this to the value, so the knob turns with the finger and never jumps to it.
     */
    fun angleDelta(
        from: Float,
        to: Float,
    ): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    /** The value change for a radial turn of [degrees]: the full range over the dial's sweep. */
    fun turnDelta(
        degrees: Float,
        range: ClosedFloatingPointRange<Float>,
    ): Float = degrees / SWEEP_DEGREES * (range.endInclusive - range.start)

    private const val EPSILON = 1e-4f
}
