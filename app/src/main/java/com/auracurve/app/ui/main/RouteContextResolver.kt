package com.auracurve.app.ui.main

import com.auracurve.app.audio.AudioDeviceType
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.soundContext
import com.auracurve.app.context.SoundContext

// DeviceProfileEntity.contextMode value for "mode explicitly off on this route".
const val CONTEXT_MODE_NONE = "NONE"

/** The outcome of [RouteContextResolver.resolve]: which mode a route starts with, and whether detection chose it. */
data class RouteContextDecision(
    val mode: SoundContext?,
    val automatic: Boolean,
)

/**
 * Which listening-context mode (car / Bluetooth speaker) a route gets, taken out of
 * `MainViewModel` (roadmap 06, P6-1): the rules for device profiles are decisions on plain values,
 * so they are testable on their own.
 *
 * The saved choice of the route wins, then a legacy profile that stored the context as a preset,
 * then the one restored from the last session, and only then what detection suggests (Android
 * Auto, or the route's type). A manual choice therefore never follows the user to another route.
 */
object RouteContextResolver {
    fun resolve(
        route: AudioRoute,
        carConnected: Boolean,
        storedMode: String?,
        legacyContext: SoundContext?,
        restoredContext: SoundContext?,
    ): RouteContextDecision {
        val detected = if (carConnected) SoundContext.CAR else route.type.soundContext()
        val mode =
            when {
                storedMode == null -> legacyContext ?: restoredContext ?: detected
                storedMode == CONTEXT_MODE_NONE -> null
                else -> SoundContext.entries.firstOrNull { it.name == storedMode } ?: detected
            }
        val automatic = storedMode == null && legacyContext == null && restoredContext == null && detected != null
        return RouteContextDecision(mode, automatic)
    }

    /**
     * Whether to ask "headphones, car or speaker?" for [route]: only for a Bluetooth route nobody has
     * classified yet, not dismissed before, and not while Android Auto already says "car".
     */
    fun shouldAskDeviceKind(
        route: AudioRoute,
        storedMode: String?,
        legacyContext: SoundContext?,
        dismissedRouteIds: Set<String>,
        carConnected: Boolean,
    ): Boolean =
        route.type == AudioDeviceType.BLUETOOTH &&
            storedMode == null &&
            legacyContext == null &&
            route.id !in dismissedRouteIds &&
            !carConnected
}
