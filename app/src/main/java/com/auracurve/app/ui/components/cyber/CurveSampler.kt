package com.auracurve.app.ui.components.cyber

import com.auracurve.app.ui.equalizer.CurveGeometry
import kotlin.math.PI
import kotlin.math.cos

/**
 * The response curve of [com.auracurve.app.ui.equalizer.CurveGeometry.gainAt] without its
 * allocations, for drawing every frame of a drag: the points live in two arrays sorted by
 * frequency, as fractions of the log axis, and are read in place.
 */
internal class CurveSampler(
    capacity: Int,
) {
    private var xs = FloatArray(capacity)
    private var gains = FloatArray(capacity)

    /** Number of points in use. */
    var size = 0
        private set

    /** Makes room for [count] points; grows only when there are more points than before. */
    fun resize(count: Int) {
        if (count > xs.size) {
            xs = FloatArray(count)
            gains = FloatArray(count)
        }
        size = count
    }

    /** Sets point [index]; the points' frequencies must ascend with the index. */
    fun put(
        index: Int,
        frequencyHz: Float,
        gainDb: Float,
    ) {
        xs[index] = CurveGeometry.xFraction(frequencyHz)
        gains[index] = gainDb
    }

    /** The gain at [fraction] of the log axis: cosine-eased between points, held past the ends. */
    fun gainAt(fraction: Float): Float {
        val n = size
        if (n == 0) return 0f
        if (fraction <= xs[0]) return gains[0]
        if (fraction >= xs[n - 1]) return gains[n - 1]
        var upper = 1
        while (upper < n - 1 && xs[upper] < fraction) upper++
        val lower = upper - 1
        val span = xs[upper] - xs[lower]
        if (span <= 0f) return gains[upper]
        val t = (fraction - xs[lower]) / span
        val eased = ((1.0 - cos(PI * t)) / 2.0).toFloat()
        return gains[lower] + (gains[upper] - gains[lower]) * eased
    }
}
