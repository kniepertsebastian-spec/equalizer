package com.auracurve.app.car

/** One GPS reading (module 11, F3/F6); the clock is the caller's. */
data class GeoFix(
    val lat: Double,
    val lon: Double,
    /** Metres, null if unknown. */
    val accuracyM: Float?,
    /** When the fix arrived. */
    val timeMs: Long,
)

enum class ParkingQuality {
    /** Within [ParkingTracker.goodAccuracyM]. */
    GOOD,

    /** Only a rough fix (underground car park, city canyon): shown with a warning. */
    INACCURATE,

    /** No usable fix at all. */
    NONE,
}

data class ParkedSpot(
    val fix: GeoFix?,
    val quality: ParkingQuality,
    /** When the car was switched off. */
    val parkedAtMs: Long,
)

/**
 * Module 11, F3: where the car was left. During the drive it remembers the last good fix. At
 * "ignition off" a recent good fix is taken at once; otherwise it waits up to [waitMs] for one,
 * and then settles for the best it has, marked as inaccurate. It never hands out an old position
 * as a new one: fixes older than [maxAgeMs] at switch-off do not count.
 *
 * Pure: the caller passes fixes and the time, and calls [tick] while [waiting].
 */
class ParkingTracker(
    val goodAccuracyM: Float = DEFAULT_GOOD_ACCURACY_M,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val waitMs: Long = DEFAULT_WAIT_MS,
) {
    private var lastGood: GeoFix? = null
    private var best: GeoFix? = null
    private var offAtMs: Long? = null

    /** True between "ignition off" and the decision. */
    val waiting: Boolean get() = offAtMs != null

    /** A new fix; returns the spot if it settles a pending switch-off. */
    fun onFix(fix: GeoFix): ParkedSpot? {
        if (isGood(fix)) lastGood = fix
        val off = offAtMs ?: return null
        if (fix.timeMs < off - maxAgeMs) return null
        if (best == null || accuracy(fix) < accuracy(best!!)) best = fix
        return if (isGood(fix)) settle(fix, ParkingQuality.GOOD, off) else null
    }

    /** The car was switched off; returns the spot at once if a recent good fix is known. */
    fun onIgnitionOff(nowMs: Long): ParkedSpot? {
        offAtMs = nowMs
        val recent = lastGood?.takeIf { nowMs - it.timeMs <= maxAgeMs }
        best = recent
        return if (recent != null) settle(recent, ParkingQuality.GOOD, nowMs) else null
    }

    /** The car is on again before a decision: forget the pending one. */
    fun onIgnitionOn() {
        offAtMs = null
        best = null
    }

    /** Settles a pending switch-off once [waitMs] have passed. */
    fun tick(nowMs: Long): ParkedSpot? {
        val off = offAtMs ?: return null
        if (nowMs - off < waitMs) return null
        val fallback = best
        return if (fallback != null) settle(fallback, ParkingQuality.INACCURATE, off) else settle(null, ParkingQuality.NONE, off)
    }

    private fun settle(
        fix: GeoFix?,
        quality: ParkingQuality,
        offMs: Long,
    ): ParkedSpot {
        offAtMs = null
        best = null
        return ParkedSpot(fix, quality, offMs)
    }

    private fun isGood(fix: GeoFix) = fix.accuracyM != null && fix.accuracyM <= goodAccuracyM

    private fun accuracy(fix: GeoFix) = fix.accuracyM ?: Float.MAX_VALUE

    companion object {
        const val DEFAULT_GOOD_ACCURACY_M = 30f
        const val DEFAULT_MAX_AGE_MS = 2 * 60_000L
        const val DEFAULT_WAIT_MS = 30_000L
    }
}
