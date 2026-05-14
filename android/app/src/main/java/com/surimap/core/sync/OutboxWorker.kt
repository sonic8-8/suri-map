package com.surimap.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.surimap.BuildConfig
import com.surimap.core.database.SuriMapDatabaseProvider
import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.AndroidNetworkFactory
import java.util.concurrent.TimeUnit

data class OutboxReplayWorkRequest(
    val incidentId: String,
    val policePhoneId: String,
    val apiBaseUrl: String = BuildConfig.SURI_MAP_API_BASE_URL,
    val accessToken: String? = null
) {
    val uniqueWorkName: String = "outbox-replay-$incidentId-$policePhoneId"

    fun toWorkRequest(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<OutboxWorker>()
            .setInputData(
                workDataOf(
                    OutboxWorker.KEY_INCIDENT_ID to incidentId,
                    OutboxWorker.KEY_POLICE_PHONE_ID to policePhoneId,
                    OutboxWorker.KEY_API_BASE_URL to apiBaseUrl,
                    OutboxWorker.KEY_ACCESS_TOKEN to accessToken
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.SECONDS
            )
            .build()
}

class OutboxReplayScheduler(
    private val enqueueUniqueWork: (String, ExistingWorkPolicy, OneTimeWorkRequest) -> Unit
) {
    constructor(workManager: WorkManager) : this(
        { name, policy, request -> workManager.enqueueUniqueWork(name, policy, request) }
    )

    fun schedule(request: OutboxReplayWorkRequest) {
        if (
            request.incidentId.isBlank() ||
            request.policePhoneId.isBlank() ||
            request.apiBaseUrl.isBlank()
        ) {
            return
        }
        enqueueUniqueWork(request.uniqueWorkName, ExistingWorkPolicy.APPEND_OR_REPLACE, request.toWorkRequest())
    }
}

class SchedulingSyncClient(
    private val delegate: SyncClient,
    private val scheduleReplay: (OutboxReplayWorkRequest) -> Unit,
    private val apiBaseUrl: String,
    private val accessToken: String? = null
) : SyncClient {
    override suspend fun enqueue(writeOperation: LocalWriteOperation): EnqueueResult {
        val result = delegate.enqueue(writeOperation)
        if (result.shouldScheduleReplay()) {
            scheduleReplay(
                OutboxReplayWorkRequest(
                    incidentId = writeOperation.incidentId,
                    policePhoneId = writeOperation.policePhoneId,
                    apiBaseUrl = apiBaseUrl,
                    accessToken = accessToken
                )
            )
        }
        return result
    }

    private fun EnqueueResult.shouldScheduleReplay(): Boolean =
        status in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED_RETRYABLE) &&
            harnessStatus == HarnessSyncStatus.PENDING_SEND
}

fun interface OutboxReplayProvider {
    fun create(context: Context, apiBaseUrl: String, accessToken: String?): OutboxReplay
}

object LocalSyncRuntime {
    var outboxReplay: OutboxReplay? = null
    var outboxReplayProvider: OutboxReplayProvider? = null
    var outboxReplayScheduler: ((OutboxReplayWorkRequest) -> Unit)? = null
}

class OutboxWorker(appContext: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val incidentId = inputData.getString(KEY_INCIDENT_ID)?.takeIf(String::isNotBlank)
        val policePhoneId = inputData.getString(KEY_POLICE_PHONE_ID)?.takeIf(String::isNotBlank)
        val apiBaseUrl = inputData.getString(KEY_API_BASE_URL)
            ?.takeIf(String::isNotBlank)
            ?: BuildConfig.SURI_MAP_API_BASE_URL
        val accessToken = inputData.getString(KEY_ACCESS_TOKEN)?.takeIf(String::isNotBlank)
        if (incidentId == null || policePhoneId == null) {
            return Result.failure()
        }
        val replay = LocalSyncRuntime.outboxReplay
            ?: LocalSyncRuntime.outboxReplayProvider?.create(applicationContext, apiBaseUrl, accessToken)
            ?: createRoomOutboxReplay(applicationContext, apiBaseUrl, accessToken)
        val replayResult = replay.flushPending(
            policePhoneId = policePhoneId,
            incidentId = incidentId
        )
        return if (replayResult.shouldRetry) Result.retry() else Result.success()
    }

    private fun createRoomOutboxReplay(
        context: Context,
        apiBaseUrl: String,
        accessToken: String?
    ): OutboxReplay {
        val database = SuriMapDatabaseProvider.database(context)
        return RoomOutboxReplay(
            outboxDao = database.outboxDao(),
            sender =
            AndroidNetworkFactory.createOutboxSender(
                baseUrl = apiBaseUrl,
                accessTokenProvider = AccessTokenProvider { accessToken }
            ),
            enableRetryJitter = !BuildConfig.DEBUG
        )
    }

    companion object {
        const val KEY_INCIDENT_ID = "incidentId"
        const val KEY_POLICE_PHONE_ID = "policePhoneId"
        const val KEY_API_BASE_URL = "apiBaseUrl"
        const val KEY_ACCESS_TOKEN = "accessToken"
    }
}
