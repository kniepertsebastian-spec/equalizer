package com.auracurve.app.car

/** How hard the app protects the built-in Pixel (module 11, F1). Ordered: later = stronger. */
enum class ProtectionLevel {
    /** Nothing to do. */
    NONE,

    /** Warm: the remote shows a warning, nothing is paused. */
    WARN,

    /** Hot: feed pulls, library sync and web updates pause. */
    SHED,

    /** Very hot: also playback stops; only the server and the status stay. */
    CRITICAL,
}

data class Protection(
    val level: ProtectionLevel,
    /**
     * Parked long enough or the battery is low. Reported only for now: switching hotspot and server
     * off needs a device test first (how does the Pixel come back?). It does not pause the sync,
     * because the home Wi-Fi sync happens exactly while the car is parked.
     */
    val standby: Boolean,
) {
    /** Feed, library sync and web updates must wait. */
    val pauseBackground: Boolean get() = level >= ProtectionLevel.SHED

    companion object {
        val NONE = Protection(ProtectionLevel.NONE, standby = false)
    }
}

/**
 * The rules of module 11, F1. Pure: the caller passes the readings and the time.
 *
 * The app can only take load off, not cool. Android throttles and finally shuts down by itself;
 * these steps come before that.
 */
object HeatProtection {
    /**
     * From Android's thermal status (PowerManager.THERMAL_STATUS_*: 2 MODERATE, 3 SEVERE, 4+
     * CRITICAL) and the battery temperature, whichever is worse. The battery reacts to the sun
     * on the windscreen earlier than the thermal status on some devices.
     */
    fun level(
        thermalStatus: Int,
        batteryTempC: Float?,
    ): ProtectionLevel {
        val byThermal =
            when {
                thermalStatus >= THERMAL_CRITICAL -> ProtectionLevel.CRITICAL
                thermalStatus >= THERMAL_SEVERE -> ProtectionLevel.SHED
                thermalStatus >= THERMAL_MODERATE -> ProtectionLevel.WARN
                else -> ProtectionLevel.NONE
            }
        val byBattery =
            when {
                batteryTempC == null -> ProtectionLevel.NONE
                batteryTempC >= BATTERY_CRITICAL_C -> ProtectionLevel.CRITICAL
                batteryTempC >= BATTERY_SHED_C -> ProtectionLevel.SHED
                batteryTempC >= BATTERY_WARN_C -> ProtectionLevel.WARN
                else -> ProtectionLevel.NONE
            }
        return maxOf(byThermal, byBattery)
    }

    /**
     * Standby rule with F2: the car has been off for [STANDBY_AFTER_MS], or it is off and the
     * battery is below [STANDBY_BATTERY_PERCENT]. Unknown ignition never means standby.
     */
    fun standby(
        ignition: Ignition?,
        batteryPercent: Int?,
        nowMs: Long,
    ): Boolean {
        if (ignition == null || ignition.state != IgnitionState.OFF) return false
        val lowBattery = batteryPercent != null && batteryPercent < STANDBY_BATTERY_PERCENT
        return lowBattery || nowMs - ignition.sinceMs >= STANDBY_AFTER_MS
    }

    fun protection(
        thermalStatus: Int,
        batteryTempC: Float?,
        batteryPercent: Int?,
        ignition: Ignition?,
        nowMs: Long,
    ): Protection = Protection(level(thermalStatus, batteryTempC), standby(ignition, batteryPercent, nowMs))

    const val THERMAL_MODERATE = 2
    const val THERMAL_SEVERE = 3
    const val THERMAL_CRITICAL = 4

    // Li-ion: charging usually stops at about 45 °C, damage grows above 50 °C.
    const val BATTERY_WARN_C = 42f
    const val BATTERY_SHED_C = 45f
    const val BATTERY_CRITICAL_C = 50f

    const val STANDBY_AFTER_MS = 2 * 60 * 60 * 1000L
    const val STANDBY_BATTERY_PERCENT = 30
}
