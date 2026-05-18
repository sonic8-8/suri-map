package com.surimap.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import java.time.Instant

interface LocationRecorder {
    fun start()

    fun pause()

    fun stop()
}

data class GpsLocationFix(
    val lon: Double,
    val lat: Double,
    val bearingDegrees: Double?,
    val speedMps: Double?,
    val horizontalAccuracyM: Int?,
    val capturedAt: Instant
)

fun interface LocationUpdatesHandle {
    fun stop()
}

fun interface LocationUpdates {
    fun start(onFix: (GpsLocationFix) -> Unit): LocationUpdatesHandle
}

class AndroidLocationUpdates(
    private val context: Context,
    private val now: () -> Instant = { Instant.now() }
) : LocationUpdates {
    fun lastKnownFix(): GpsLocationFix? {
        if (!hasLocationPermission()) {
            return null
        }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        return activeProviders(locationManager)
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull(Location::getTime)
            ?.toGpsLocationFix(now)
    }

    override fun start(onFix: (GpsLocationFix) -> Unit): LocationUpdatesHandle {
        if (!hasLocationPermission()) {
            return LocationUpdatesHandle {}
        }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return LocationUpdatesHandle {}
        val provider = activeProvider(locationManager) ?: return LocationUpdatesHandle {}
        val listener =
            object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    onFix(location.toGpsLocationFix(now))
                }
            }
        return try {
            locationManager.requestLocationUpdates(
                provider,
                LOCATION_SAMPLE_INTERVAL_MS,
                0f,
                listener,
                Looper.getMainLooper()
            )
            LocationUpdatesHandle { runCatching { locationManager.removeUpdates(listener) } }
        } catch (_: SecurityException) {
            LocationUpdatesHandle {}
        } catch (_: IllegalArgumentException) {
            LocationUpdatesHandle {}
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun activeProvider(locationManager: LocationManager): String? =
        activeProviders(locationManager).firstOrNull()

    private fun activeProviders(locationManager: LocationManager): List<String> =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider -> runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false) }

    private companion object {
        const val LOCATION_SAMPLE_INTERVAL_MS = 5_000L
    }
}

private fun Location.toGpsLocationFix(now: () -> Instant): GpsLocationFix =
    GpsLocationFix(
        lon = longitude,
        lat = latitude,
        bearingDegrees = if (hasBearing()) bearing.toDouble() else null,
        speedMps = if (hasSpeed()) speed.toDouble() else null,
        horizontalAccuracyM = if (hasAccuracy()) accuracy.toInt() else null,
        capturedAt = time.takeIf { it > 0L }?.let(Instant::ofEpochMilli) ?: now()
    )
