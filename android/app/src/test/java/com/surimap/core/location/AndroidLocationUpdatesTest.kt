package com.surimap.core.location

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidLocationUpdatesTest {

    @Test
    fun startUsesRequestedSampleInterval() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLocationManager = shadowOf(locationManager)
        shadowLocationManager.setLocationEnabled(true)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)

        val handle = AndroidLocationUpdates(context, sampleIntervalMs = 2_500L).start { }

        val request = shadowLocationManager
            .getLegacyLocationRequests(LocationManager.GPS_PROVIDER)
            .single()
        assertEquals(2_500L, request.intervalMillis)
        handle.stop()
    }

    @Test
    fun startCollectsNetworkFixWhenGpsIsEnabledButHasNoFix() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLocationManager = shadowOf(locationManager)
        shadowLocationManager.setLocationEnabled(true)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        val fixes = mutableListOf<GpsLocationFix>()

        val handle = AndroidLocationUpdates(context).start { fix -> fixes += fix }
        shadowLocationManager.simulateLocation(
            LocationManager.NETWORK_PROVIDER,
            Location(LocationManager.NETWORK_PROVIDER).apply {
                latitude = 35.14935
                longitude = 126.851212
                accuracy = 18f
                time = 1_779_199_386_227L
            }
        )
        shadowOf(Looper.getMainLooper()).idle()
        handle.stop()

        assertEquals(1, fixes.size)
        assertEquals(35.14935, fixes.single().lat, 0.000001)
        assertEquals(126.851212, fixes.single().lon, 0.000001)
        assertTrue(fixes.single().horizontalAccuracyM!! <= 18)
    }
}
