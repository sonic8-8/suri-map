package com.surimap.core.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.surimap.BuildConfig
import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.sync.LocalSyncRuntime
import com.surimap.core.sync.OutboxReplayScheduler
import com.surimap.core.sync.OutboxReplayWorkRequest

data class OfflinePackageWorkerInstallRequest(
    val incidentId: String,
    val policePhoneId: String,
    val manifestId: String,
    val apiBaseUrl: String = BuildConfig.SURI_MAP_API_BASE_URL,
    val accessToken: String? = null,
    val clockOffsetMs: Long? = null,
    val clockSyncedAt: String? = null
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
        val incidentId = inputData.getString(KEY_INCIDENT_ID)?.takeIf(String::isNotBlank)
        val policePhoneId = inputData.getString(KEY_POLICE_PHONE_ID)?.takeIf(String::isNotBlank)
        val manifestId = inputData.getString(KEY_MANIFEST_ID)?.takeIf(String::isNotBlank)
        val apiBaseUrl = inputData.getString(KEY_API_BASE_URL)
            ?.takeIf(String::isNotBlank)
            ?: BuildConfig.SURI_MAP_API_BASE_URL
        val accessToken = inputData.getString(KEY_ACCESS_TOKEN)?.takeIf(String::isNotBlank)
        val clockOffsetMs = inputData.keyValueMap[KEY_CLOCK_OFFSET_MS] as? Long
        val clockSyncedAt = inputData.getString(KEY_CLOCK_SYNCED_AT)?.takeIf(String::isNotBlank)
        if (incidentId == null || policePhoneId == null || manifestId == null) {
            return Result.failure()
        }
        val request =
            OfflinePackageWorkerInstallRequest(
                incidentId = incidentId,
                policePhoneId = policePhoneId,
                manifestId = manifestId,
                apiBaseUrl = apiBaseUrl,
                accessToken = accessToken,
                clockOffsetMs = clockOffsetMs,
                clockSyncedAt = clockSyncedAt
            )
        val installer = OfflinePackageDownloadRuntime.installer
            ?: RoomOfflinePackageWorkerInstaller.fromContext(
                context = applicationContext,
                apiBaseUrl = apiBaseUrl,
                accessTokenProvider = AccessTokenProvider { accessToken }
            )
        installer.install(request)
        val replayRequest =
            OutboxReplayWorkRequest(
                incidentId = incidentId,
                policePhoneId = policePhoneId,
                apiBaseUrl = apiBaseUrl,
                accessToken = accessToken
            )
        LocalSyncRuntime.outboxReplayScheduler?.invoke(replayRequest)
            ?: OutboxReplayScheduler(WorkManager.getInstance(applicationContext)).schedule(
                replayRequest
            )
        return Result.success()
    }

    companion object {
        const val KEY_INCIDENT_ID = "incidentId"
        const val KEY_POLICE_PHONE_ID = "policePhoneId"
        const val KEY_MANIFEST_ID = "manifestId"
        const val KEY_API_BASE_URL = "apiBaseUrl"
        const val KEY_ACCESS_TOKEN = "accessToken"
        const val KEY_CLOCK_OFFSET_MS = "clockOffsetMs"
        const val KEY_CLOCK_SYNCED_AT = "clockSyncedAt"
    }
}
