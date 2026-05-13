package com.surimap.core.offline

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

data class OfflinePackageDownloadWorkRequest(
    val incidentId: String,
    val policePhoneId: String,
    val manifestId: String,
    val apiBaseUrl: String,
    val accessToken: String? = null
) {
    val uniqueWorkName: String =
        "offline-package-download-$incidentId-$policePhoneId-$manifestId"

    fun toWorkRequest(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<OfflinePackageDownloadWorker>()
            .setInputData(
                workDataOf(
                    OfflinePackageDownloadWorker.KEY_INCIDENT_ID to incidentId,
                    OfflinePackageDownloadWorker.KEY_POLICE_PHONE_ID to policePhoneId,
                    OfflinePackageDownloadWorker.KEY_MANIFEST_ID to manifestId,
                    OfflinePackageDownloadWorker.KEY_API_BASE_URL to apiBaseUrl,
                    OfflinePackageDownloadWorker.KEY_ACCESS_TOKEN to accessToken
                )
            )
            .build()
}

class OfflinePackageDownloadScheduler(
    private val enqueueUniqueWork: (String, ExistingWorkPolicy, OneTimeWorkRequest) -> Unit
) {
    constructor(workManager: WorkManager) : this(
        { name, policy, request -> workManager.enqueueUniqueWork(name, policy, request) }
    )

    fun schedule(request: OfflinePackageDownloadWorkRequest) {
        if (
            request.incidentId.isBlank() ||
            request.policePhoneId.isBlank() ||
            request.manifestId.isBlank() ||
            request.apiBaseUrl.isBlank()
        ) {
            return
        }
        enqueueUniqueWork(request.uniqueWorkName, ExistingWorkPolicy.KEEP, request.toWorkRequest())
    }
}
