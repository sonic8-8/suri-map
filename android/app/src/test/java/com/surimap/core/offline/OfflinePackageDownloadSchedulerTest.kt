package com.surimap.core.offline

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflinePackageDownloadSchedulerTest {

    @Test
    fun schedulesUniqueInstallWorkWithManifestIdentityAndApiBaseUrl() {
        val enqueued = mutableListOf<EnqueuedWork>()
        val scheduler =
            OfflinePackageDownloadScheduler { name, policy, request ->
                enqueued += EnqueuedWork(name, policy, request)
            }

        scheduler.schedule(
            OfflinePackageDownloadWorkRequest(
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID,
                manifestId = MANIFEST_ID,
                apiBaseUrl = "https://suri-map.internal",
                accessToken = "bootstrap-token-1"
            )
        )

        val work = enqueued.single()
        assertEquals(
            "offline-package-download-$INCIDENT_ID-$POLICE_PHONE_ID-$MANIFEST_ID",
            work.name
        )
        assertEquals(ExistingWorkPolicy.KEEP, work.policy)
        assertEquals(INCIDENT_ID, work.request.workSpec.input.getString("incidentId"))
        assertEquals(POLICE_PHONE_ID, work.request.workSpec.input.getString("policePhoneId"))
        assertEquals(MANIFEST_ID, work.request.workSpec.input.getString("manifestId"))
        assertEquals("https://suri-map.internal", work.request.workSpec.input.getString("apiBaseUrl"))
        assertEquals("bootstrap-token-1", work.request.workSpec.input.getString("accessToken"))
    }

    @Test
    fun sameManifestIdentityUsesSameUniqueWorkName() {
        val first = OfflinePackageDownloadWorkRequest(
            incidentId = INCIDENT_ID,
            policePhoneId = POLICE_PHONE_ID,
            manifestId = MANIFEST_ID,
            apiBaseUrl = "https://suri-map.internal"
        )
        val second = first.copy(apiBaseUrl = "https://suri-map.backup")

        assertEquals(first.uniqueWorkName, second.uniqueWorkName)
    }

    private data class EnqueuedWork(
        val name: String,
        val policy: ExistingWorkPolicy,
        val request: OneTimeWorkRequest
    )

    private companion object {
        const val INCIDENT_ID = "inc-precinct-first-001"
        const val POLICE_PHONE_ID = "phone-precinct-001"
        const val MANIFEST_ID = "pkg-precinct-first-rev-18"
    }
}
