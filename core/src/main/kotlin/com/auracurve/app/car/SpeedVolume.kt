package com.auracurve.app.car

import kotlin.math.roundToInt

/** Module 11, F6: louder at speed. Stored settings, as the remote sees them. */
data class SpeedVolumeSettings(
    val enabled: Boolean = false,
    /** The extra level at [fullSpeedKmh] and above. */
    val maxBoostDb: Float = DEFAULT_MAX_BOOST_DB,
    /** Below this nothing is added (town). */
    val startSpeedKmh: Float = DEFAULT_START_KMH,
    /** From here the full [maxBoostDb] (motorway). */
    val fullSpeedKmh: Float = DEFAULT_FULL_KMH,
) {
    /** Within the limits the API accepts; [fullSpeedKmh] stays at least 20 km/h above [startSpeedKmh]. */
    fun sanitized(): SpeedVolumeSettings {
        val start = startSpeedKmh.coerceIn(0f, MAX_START_KMH)
        return copy(
            maxBoostDb = maxBoostDb.coerceIn(0f, MAX_BOOST_DB),
            startSpeedKmh = start,
            fullSpeedKmh = fullSpeedKmh.coerceIn(start + MIN_SPAN_KMH, MAX_FULL_KMH),
        )
    }

    companion object {
        const val DEFAULT_MAX_BOOST_DB = 4f
        const val MAX_BOOST_DB = 6f
        const val DEFAULT_START_KMH = 30f
        const val DEFAULT_FULL_KMH = 130f
        const val MAX_START_KMH = 150f
        const val MAX_FULL_KMH = 200f
        const val MIN_SPAN_KMH = 20f
    }
}

/**
 * The F6 regulator: speed and time in, extra gain in dB out. Pure and clock-free.
 *
 * - No thresholds: the speed sets a stepless mix between town and motorway.
 * - The speed is averaged over [averageMs]; without a fix (tunnel) the last average holds.
 * - The gain moves at most [riseDbPerS] up and [fallDbPerS] down, so nothing pumps.
 * - Frozen (paused, nothing playing) it does not move at all.
 * - Switched off it fades to 0 and then leaves the chain alone.
 * - The output comes in [STEP_DB] steps, so the engine is not fed every tiny change.
 *
 * Manual volume changes need no handling here: they go to the system volume, and this gain
 * sits on top of whatever the user set.
 */
class SpeedVolumeRegulator(
    private val averageMs: Long = DEFAULT_AVERAGE_MS,
    private val riseDbPerS: Float = DEFAULT_RISE_DB_PER_S,
    private val fallDbPerS: Float = DEFAULT_FALL_DB_PER_S,
) {
    private val samples = ArrayDeque<Pair<Long, Float>>()
    private var heldKmh: Float? = null
    private var gainDb = 0f
    private var lastMs: Long? = null

    /** The current extra gain, in [STEP_DB] steps. */
    val outputDb: Float get() = quantize(gainDb)

    /**
     * @param speedKmh the latest speed, or null without a fix
     * @param frozen true while nothing plays (pause, between tracks)
     */
    fun update(
        settings: SpeedVolumeSettings,
        speedKmh: Float?,
        nowMs: Long,
        frozen: Boolean = false,
    ): Float {
        val dtS = ((nowMs - (lastMs ?: nowMs)).coerceAtLeast(0)) / 1000f
        lastMs = nowMs
        if (speedKmh != null && speedKmh >= 0f) samples.addLast(nowMs to speedKmh)
        while (samples.isNotEmpty() && nowMs - samples.first().first > averageMs) samples.removeFirst()
        if (samples.isNotEmpty()) heldKmh = samples.map { it.second }.average().toFloat()

        val s = settings.sanitized()
        val target = if (s.enabled) targetDb(s, heldKmh) else 0f
        if (!frozen || !s.enabled) {
            gainDb =
                if (target > gainDb) {
                    minOf(target, gainDb + riseDbPerS * dtS)
                } else {
                    maxOf(target, gainDb - fallDbPerS * dtS)
                }
        }
        return outputDb
    }

    companion object {
        const val DEFAULT_AVERAGE_MS = 4_000L
        const val DEFAULT_RISE_DB_PER_S = 0.5f
        const val DEFAULT_FALL_DB_PER_S = 0.25f
        const val STEP_DB = 0.1f

        fun targetDb(
            settings: SpeedVolumeSettings,
            speedKmh: Float?,
        ): Float {
            if (speedKmh == null) return 0f
            val span = settings.fullSpeedKmh - settings.startSpeedKmh
            val mix = ((speedKmh - settings.startSpeedKmh) / span).coerceIn(0f, 1f)
            return settings.maxBoostDb * mix
        }

        private fun quantize(db: Float): Float = (db / STEP_DB).roundToInt() * STEP_DB
    }
}
