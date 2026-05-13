package com.surimap.core.offline

import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflinePackageDownloadWorkerTest {

    @After
    fun tearDown() {
        OfflinePackageDownloadRuntime.installer = null
    }

    @Test
    fun workerDelegatesManifestInstallToRuntimeInstaller() = runBlocking {
        val installs = mutableListOf<OfflinePackageWorkerInstallRequest>()
        OfflinePackageDownloadRuntime.installer =
            OfflinePackageWorkerInstaller { request -> installs += request }
        val worker =
            TestListenableWorkerBuilder<OfflinePackageDownloadWorker>(
                RuntimeEnvironment.getApplication()
            )
                .setInputData(
                    Data.Builder()
                        .putString("incidentId", INCIDENT_ID)
                        .putString("policePhoneId", POLICE_PHONE_ID)
                        .putString("manifestId", MANIFEST_ID)
                        .putString("accessToken", "bootstrap-token-1")
                        .build()
                )
                .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(
            OfflinePackageWorkerInstallRequest(
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID,
                manifestId = MANIFEST_ID,
                accessToken = "bootstrap-token-1"
            ),
            installs.single()
        )
    }

    @Test
    fun workerFailsWhenRequiredInputIsMissing() = runBlocking {
        val worker =
            TestListenableWorkerBuilder<OfflinePackageDownloadWorker>(
                RuntimeEnvironment.getApplication()
            ).build()

        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }

    private companion object {
        const val INCIDENT_ID = "inc-precinct-first-001"
        const val POLICE_PHONE_ID = "phone-precinct-001"
        const val MANIFEST_ID = "pkg-precinct-first-rev-18"
    }
}
