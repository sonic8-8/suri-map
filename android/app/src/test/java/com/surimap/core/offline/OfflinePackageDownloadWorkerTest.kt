package com.surimap.core.offline

import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.sync.LocalSyncRuntime
import com.surimap.core.sync.OutboxReplayWorkRequest
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.manifestIdFixture
import com.surimap.testing.policePhoneIdFixture
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
        LocalSyncRuntime.outboxReplayScheduler = null
        LocalSyncRuntime.accessTokenProvider = null
    }

    @Test
    fun workerDelegatesManifestInstallToRuntimeInstaller() = runBlocking {
        val installs = mutableListOf<OfflinePackageWorkerInstallRequest>()
        val replayRequests = mutableListOf<OutboxReplayWorkRequest>()
        OfflinePackageDownloadRuntime.installer =
            OfflinePackageWorkerInstaller { request -> installs += request }
        LocalSyncRuntime.outboxReplayScheduler = { request -> replayRequests += request }
        LocalSyncRuntime.accessTokenProvider = AccessTokenProvider { "bootstrap-token-1" }
        val worker =
            TestListenableWorkerBuilder<OfflinePackageDownloadWorker>(
                RuntimeEnvironment.getApplication()
            )
                .setInputData(
                    Data.Builder()
                        .putString("incidentId", INCIDENT_ID)
                        .putString("policePhoneId", POLICE_PHONE_ID)
                        .putString("manifestId", MANIFEST_ID)
                        .putLong("clockOffsetMs", 120L)
                        .putString("clockSyncedAt", "2026-05-11T06:00:00.120Z")
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
                clockOffsetMs = 120L,
                clockSyncedAt = "2026-05-11T06:00:00.120Z"
            ),
            installs.single()
        )
        assertEquals(
            OutboxReplayWorkRequest(
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID
            ),
            replayRequests.single()
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
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-001")
        val MANIFEST_ID = manifestIdFixture("precinct-first-rev-18")
    }
}
