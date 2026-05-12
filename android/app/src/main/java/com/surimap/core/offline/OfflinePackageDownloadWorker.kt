package com.surimap.core.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

data class OfflinePackageWorkerInstallRequest(
    val incidentId: String,
    val policePhoneId: String,
    val manifestId: String
)

fun interface OfflinePackageWorkerInstaller {
    suspend fun install(request: OfflinePackageWorkerInstallRequest)
}

object OfflinePackageDownloadRuntime {
    var installer: OfflinePackageWorkerInstaller? = null
}

class OfflinePackageDownloadWorker(appContext: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val incidentId = inputData.getString("incidentId")?.takeIf(String::isNotBlank)
        val policePhoneId = inputData.getString("policePhoneId")?.takeIf(String::isNotBlank)
        val manifestId = inputData.getString("manifestId")?.takeIf(String::isNotBlank)
        if (incidentId == null || policePhoneId == null || manifestId == null) {
            return Result.failure()
        }
        OfflinePackageDownloadRuntime.installer?.install(
            OfflinePackageWorkerInstallRequest(
                incidentId = incidentId,
                policePhoneId = policePhoneId,
                manifestId = manifestId
            )
        )
        return Result.success()
    }
}
