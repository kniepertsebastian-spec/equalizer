package com.auracurve.app.remote.car

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/** One GPS reading, as the car functions use it (module 11, F3/F6). */
data class CarFix(
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float?,
    /** km/h, null when the fix carries no speed. */
    val speedKmh: Float?,
)

/**
 * The shared location service of module 11 (F3 parking, F6 speed volume): plain GPS from
 * LocationManager, no Play services. Needs fine location and, because the server runs in the
 * background, "allow all the time" (ACCESS_BACKGROUND_LOCATION). Without them there are no fixes
 * and everything else keeps working.
 */
@Singleton
class CarLocation
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val manager = context.getSystemService(LocationManager::class.java)

        fun hasPermission(): Boolean {
            val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
            val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            return fine && background
        }

        /** Fixes about once a second while collected; nothing without permission or GPS. */
        @SuppressLint("MissingPermission") // checked in hasPermission()
        fun fixes(): Flow<CarFix> {
            val lm = manager ?: return emptyFlow()
            if (!hasPermission() || !lm.allProviders.contains(LocationManager.GPS_PROVIDER)) return emptyFlow()
            return callbackFlow {
                val listener = LocationListener { trySend(it.toFix()) }
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, 0f, listener, Looper.getMainLooper())
                awaitClose { lm.removeUpdates(listener) }
            }
        }

        private fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        companion object {
            const val INTERVAL_MS = 1_000L

            fun Location.toFix() =
                CarFix(
                    timeMs = time,
                    lat = latitude,
                    lon = longitude,
                    accuracyM = if (hasAccuracy()) accuracy else null,
                    speedKmh = if (hasSpeed()) speed * 3.6f else null,
                )
        }
    }
