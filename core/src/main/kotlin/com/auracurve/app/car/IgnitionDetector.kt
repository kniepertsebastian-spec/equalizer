package com.auracurve.app.car

/** Car on or off (Pixel-Car module 11, F2). */
enum class IgnitionState { ON, OFF }

/** What keeps the car "on": the charging power (switched with the ignition) or the radio's Bluetooth. */
enum class IgnitionSource { POWER, BLUETOOTH, NONE }

data class Ignition(
    val state: IgnitionState,
    /** When the state began, in the clock the detector is fed with (System.currentTimeMillis). */
    val sinceMs: Long,
    val source: IgnitionSource,
)

/**
 * One signal "car on / car off" from two hints (module 11, F2): the phone charges (the power in
 * the car is switched with the ignition) or a Bluetooth audio output is connected (the radio).
 *
 * On is immediate. Off only after both hints have been gone for [offDelayMs]: cranking the engine
 * briefly cuts the USB power, the start-stop system may too, and the radio sometimes drops
 * Bluetooth for a few seconds. A hint that comes back within the delay cancels the off.
 *
 * Pure and clock-free: the caller passes the time, and calls [tick] at [pendingOffAtMs].
 */
class IgnitionDetector(
    startMs: Long,
    private val offDelayMs: Long = DEFAULT_OFF_DELAY_MS,
) {
    private var power = false
    private var bluetooth = false

    /** Since when both hints are gone while the state is still ON; null otherwise. */
    private var lostAtMs: Long? = null

    var current: Ignition = Ignition(IgnitionState.OFF, startMs, IgnitionSource.NONE)
        private set

    /** When [tick] must run to turn the state off, if an off is pending. */
    val pendingOffAtMs: Long? get() = lostAtMs?.plus(offDelayMs)

    fun update(
        power: Boolean,
        bluetooth: Boolean,
        nowMs: Long,
    ): Ignition {
        this.power = power
        this.bluetooth = bluetooth
        val source =
            when {
                power -> IgnitionSource.POWER
                bluetooth -> IgnitionSource.BLUETOOTH
                else -> IgnitionSource.NONE
            }
        if (source != IgnitionSource.NONE) {
            lostAtMs = null
            current =
                if (current.state == IgnitionState.OFF) {
                    Ignition(IgnitionState.ON, nowMs, source)
                } else {
                    current.copy(source = source)
                }
        } else if (current.state == IgnitionState.ON && lostAtMs == null) {
            lostAtMs = nowMs
        }
        return tick(nowMs)
    }

    /** Turns the state off once both hints have been gone long enough. */
    fun tick(nowMs: Long): Ignition {
        val lost = lostAtMs
        if (lost != null && nowMs - lost >= offDelayMs) {
            lostAtMs = null
            // Off since the moment the hints went away, not since the delay ran out.
            current = Ignition(IgnitionState.OFF, lost, IgnitionSource.NONE)
        }
        return current
    }

    companion object {
        /** Long enough for cranking, start-stop and a Bluetooth reconnect; short enough for parking (F3). */
        const val DEFAULT_OFF_DELAY_MS = 15_000L
    }
}
