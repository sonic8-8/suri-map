package com.surimap.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalWarningMonitorTest {

    @Test
    fun gpsStoppedRaisesAfterFifteenSecondsAndClearsAfterTwoFreshFixes() {
        val monitor = LocalWarningMonitor()

        assertFalse(
            monitor.evaluate(healthySignals(nowMs = 14_999L, gpsProviderEnabled = false, gpsStoppedSinceMs = 0L))
                .has(LocalWarningCode.GPS_STOPPED)
        )

        assertTrue(
            monitor.evaluate(healthySignals(nowMs = 15_000L, gpsProviderEnabled = false, gpsStoppedSinceMs = 0L))
                .has(LocalWarningCode.GPS_STOPPED)
        )

        assertTrue(
            monitor.evaluate(healthySignals(nowMs = 16_000L, lastGpsFixAgeMs = 9_000L))
                .has(LocalWarningCode.GPS_STOPPED)
        )
        assertTrue(
            monitor.evaluate(healthySignals(nowMs = 17_000L, lastGpsFixAgeMs = 11_000L))
                .has(LocalWarningCode.GPS_STOPPED)
        )
        assertTrue(
            monitor.evaluate(healthySignals(nowMs = 18_000L, lastGpsFixAgeMs = 9_000L))
                .has(LocalWarningCode.GPS_STOPPED)
        )
        assertFalse(
            monitor.evaluate(healthySignals(nowMs = 19_000L, lastGpsFixAgeMs = 8_000L))
                .has(LocalWarningCode.GPS_STOPPED)
        )
    }

    @Test
    fun batteryLowRaisesBelowTwentyPercentWhenNotChargingAndClearsAtTwentyFivePercent() {
        val monitor = LocalWarningMonitor()

        assertFalse(
            monitor.evaluate(healthySignals(batteryPercent = 20, batteryCharging = false))
                .has(LocalWarningCode.BATTERY_LOW)
        )
        assertFalse(
            monitor.evaluate(healthySignals(batteryPercent = 19, batteryCharging = true))
                .has(LocalWarningCode.BATTERY_LOW)
        )

        assertTrue(
            monitor.evaluate(healthySignals(batteryPercent = 19, batteryCharging = false))
                .has(LocalWarningCode.BATTERY_LOW)
        )
        assertTrue(
            monitor.evaluate(healthySignals(batteryPercent = 24, batteryCharging = false))
                .has(LocalWarningCode.BATTERY_LOW)
        )
        assertFalse(
            monitor.evaluate(healthySignals(batteryPercent = 25, batteryCharging = false))
                .has(LocalWarningCode.BATTERY_LOW)
        )
    }

    @Test
    fun packageMissingWarningConsumesS7AvailabilityInputAndClearsForCompleteActiveManifest() {
        val monitor = LocalWarningMonitor()

        listOf(
            packageAvailability(PackageAvailability.Status.MISSING),
            packageAvailability(PackageAvailability.Status.STALE),
            packageAvailability(PackageAvailability.Status.EXPIRED),
            packageAvailability(
                status = PackageAvailability.Status.COMPLETE,
                failedRequiredItemKeys = setOf("tile/precinct-a/17/114233/51402")
            )
        ).forEach { availability ->
            assertTrue(
                "Expected PACKAGE_MISSING for $availability",
                monitor.evaluate(healthySignals(packageAvailability = availability))
                    .has(LocalWarningCode.PACKAGE_MISSING)
            )
        }

        assertFalse(
            monitor.evaluate(
                healthySignals(packageAvailability = packageAvailability(PackageAvailability.Status.COMPLETE))
            )
                .has(LocalWarningCode.PACKAGE_MISSING)
        )
        assertTrue(
            monitor.evaluate(
                healthySignals(
                    packageAvailability = packageAvailability(
                        status = PackageAvailability.Status.COMPLETE,
                        manifestVersion = "manifest-old",
                        activeManifestVersion = "manifest-active"
                    )
                )
            ).has(LocalWarningCode.PACKAGE_MISSING)
        )
    }

    @Test
    fun packageAvailabilityInputAdapterMapsS7ReadyAndUnavailableStatuses() {
        assertEquals(
            PackageAvailability.Status.COMPLETE,
            PackageAvailabilityInputAdapter.fromS7Status(
                status = "READY",
                manifestVersion = "manifest-active",
                activeManifestVersion = "manifest-active"
            ).status
        )
        assertEquals(
            PackageAvailability.Status.STALE,
            PackageAvailabilityInputAdapter.fromS7Status(
                status = "STALE",
                manifestVersion = "manifest-old",
                activeManifestVersion = "manifest-active"
            ).status
        )
        assertEquals(
            PackageAvailability.Status.MISSING,
            PackageAvailabilityInputAdapter.fromS7Status(
                status = "FAILED",
                manifestVersion = "manifest-active",
                activeManifestVersion = "manifest-active",
                failedRequiredItemKeys = setOf("tile-404")
            ).status
        )
    }

    @Test
    fun offlineRecordingRaisesAfterSixtySecondsAndClearsAfterSuccessfulSync() {
        val monitor = LocalWarningMonitor()

        assertFalse(
            monitor.evaluate(healthySignals(nowMs = 60_999L, offlineRecordingStartedAtMs = 1_000L))
                .has(LocalWarningCode.OFFLINE_RECORDING)
        )

        assertTrue(
            monitor.evaluate(healthySignals(nowMs = 61_000L, offlineRecordingStartedAtMs = 1_000L))
                .has(LocalWarningCode.OFFLINE_RECORDING)
        )

        assertFalse(
            monitor.evaluate(
                healthySignals(
                    nowMs = 61_001L,
                    offlineRecordingStartedAtMs = 1_000L,
                    lastSuccessfulSyncAtMs = 61_001L
                )
            ).has(LocalWarningCode.OFFLINE_RECORDING)
        )
    }

    @Test
    fun outboxBacklogRaisesAboveTenAndClearsAtThree() {
        val monitor = LocalWarningMonitor()

        assertFalse(
            monitor.evaluate(healthySignals(pendingOutboxCount = 10))
                .has(LocalWarningCode.OUTBOX_BACKLOG)
        )
        assertTrue(
            monitor.evaluate(healthySignals(pendingOutboxCount = 11))
                .has(LocalWarningCode.OUTBOX_BACKLOG)
        )
        assertTrue(
            monitor.evaluate(healthySignals(pendingOutboxCount = 4))
                .has(LocalWarningCode.OUTBOX_BACKLOG)
        )
        assertFalse(
            monitor.evaluate(healthySignals(pendingOutboxCount = 3))
                .has(LocalWarningCode.OUTBOX_BACKLOG)
        )
    }

    @Test
    fun localWarningsDoNotRequireServerRoundTrip() {
        val serverRoundTrip = CountingServerRoundTrip()
        val monitor = LocalWarningMonitor(serverRoundTrip = serverRoundTrip)

        val snapshot = monitor.evaluate(
            healthySignals(
                nowMs = 61_000L,
                gpsProviderEnabled = false,
                gpsStoppedSinceMs = 0L,
                batteryPercent = 10,
                batteryCharging = false,
                packageAvailability = packageAvailability(PackageAvailability.Status.MISSING),
                offlineRecordingStartedAtMs = 1_000L,
                networkConnected = false,
                pendingOutboxCount = 11
            )
        )

        assertEquals(
            setOf(
                LocalWarningCode.GPS_STOPPED,
                LocalWarningCode.BATTERY_LOW,
                LocalWarningCode.PACKAGE_MISSING,
                LocalWarningCode.OFFLINE_RECORDING,
                LocalWarningCode.OUTBOX_BACKLOG
            ),
            snapshot.activeWarnings
        )
        assertEquals(0, serverRoundTrip.callCount)
    }

    private fun LocalWarningSnapshot.has(code: LocalWarningCode): Boolean = activeWarnings.contains(code)

    private fun healthySignals(
        nowMs: Long = 0L,
        gpsProviderEnabled: Boolean = true,
        gpsStoppedSinceMs: Long? = null,
        lastGpsFixAgeMs: Long? = 0L,
        batteryPercent: Int = 80,
        batteryCharging: Boolean = true,
        packageAvailability: PackageAvailability = packageAvailability(PackageAvailability.Status.COMPLETE),
        offlineRecordingStartedAtMs: Long? = null,
        lastSuccessfulSyncAtMs: Long? = null,
        networkConnected: Boolean = true,
        pendingOutboxCount: Int = 0
    ): LocalWarningSignals = LocalWarningSignals(
        nowMs = nowMs,
        gpsProviderEnabled = gpsProviderEnabled,
        gpsStoppedSinceMs = gpsStoppedSinceMs,
        lastGpsFixAgeMs = lastGpsFixAgeMs,
        batteryPercent = batteryPercent,
        batteryCharging = batteryCharging,
        packageAvailability = packageAvailability,
        offlineRecordingStartedAtMs = offlineRecordingStartedAtMs,
        lastSuccessfulSyncAtMs = lastSuccessfulSyncAtMs,
        networkConnected = networkConnected,
        pendingOutboxCount = pendingOutboxCount
    )

    private fun packageAvailability(
        status: PackageAvailability.Status,
        manifestVersion: String = "manifest-active",
        activeManifestVersion: String = "manifest-active",
        failedRequiredItemKeys: Set<String> = emptySet()
    ): PackageAvailability = PackageAvailability(
        status = status,
        manifestVersion = manifestVersion,
        activeManifestVersion = activeManifestVersion,
        failedRequiredItemKeys = failedRequiredItemKeys
    )

    private class CountingServerRoundTrip : LocalWarningServerRoundTrip {
        var callCount = 0
            private set

        override fun probe(): Boolean {
            callCount += 1
            return false
        }
    }
}
