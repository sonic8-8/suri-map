package com.surimap.feature.search

import android.Manifest
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.surimap.BuildConfig
import com.surimap.SuriMapApplication
import com.surimap.feature.search.data.SearchPathLocationService
import com.surimap.feature.search.data.SearchPathWriteContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchPathLocationServiceTest {
    private val application = RuntimeEnvironment.getApplication() as SuriMapApplication

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application,
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build()
        )
    }

    @After
    fun tearDown() {
        runBlocking { application.stopSearchPathLocationRecording() }
    }

    @Test
    fun manifestDeclaresLocationForegroundService() {
        val serviceInfo =
            application.packageManager.getServiceInfo(
                ComponentName(application, SearchPathLocationService::class.java),
                PackageManager.ComponentInfoFlags.of(0)
            )

        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, serviceInfo.foregroundServiceType)
    }

    @Test
    fun startingServiceKeepsLocationUpdatesInForeground() {
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val locationManager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLocationManager = shadowOf(locationManager)
        shadowLocationManager.setLocationEnabled(true)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        application.configureSearchPathRecording("https://suri-map.sonic8-8.com")
        val controller = Robolectric.buildService(SearchPathLocationService::class.java).create()
        val service = controller.get()

        val result =
            service.onStartCommand(
                SearchPathLocationService.startIntent(
                    application,
                    WRITE_CONTEXT,
                    SEARCH_PATH_ID,
                    "https://suri-map.sonic8-8.com"
                ),
                0,
                1
            )
        shadowOf(Looper.getMainLooper()).idle()

        val locationRequest =
            shadowLocationManager
                .getLegacyLocationRequests(LocationManager.GPS_PROVIDER)
                .single()
        val shadowService = shadowOf(service)
        assertEquals(Service.START_NOT_STICKY, result)
        assertEquals(BuildConfig.SURI_MAP_LOCATION_SAMPLE_INTERVAL_MS, locationRequest.intervalMillis)
        assertNotNull(shadowService.lastForegroundNotification)

        controller.destroy()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            shadowLocationManager
                .getLegacyLocationRequests(LocationManager.GPS_PROVIDER)
                .isEmpty()
        )
    }

    @Test
    fun startingServiceWithoutLocationPermissionStopsBeforeCollectingGps() {
        val controller = Robolectric.buildService(SearchPathLocationService::class.java).create()
        val service = controller.get()

        val result =
            service.onStartCommand(
                SearchPathLocationService.startIntent(
                    application,
                    WRITE_CONTEXT,
                    SEARCH_PATH_ID,
                    "https://suri-map.sonic8-8.com"
                ),
                0,
                1
            )

        assertEquals(Service.START_NOT_STICKY, result)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertNull(shadowOf(service).lastForegroundNotification)
    }

    private companion object {
        const val SEARCH_PATH_ID = "ffffffff-ffff-ffff-ffff-ffffffff0001"
        val WRITE_CONTEXT =
            SearchPathWriteContext(
                incidentId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
                opId = "88888888-8888-8888-8888-888888880001",
                accountId = "account-path-001",
                policePhoneId = "50000000-0000-0000-0000-000000000001"
            )
    }
}
