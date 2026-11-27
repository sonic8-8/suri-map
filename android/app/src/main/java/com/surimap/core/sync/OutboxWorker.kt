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
import com.surimap.core.auth.AuthorAccessToken
import com.surimap.core.auth.KeycloakTokenClient
import com.surimap.core.auth.OfflineTokenRevocationWorker
import com.surimap.core.auth.OfflineTokenStore
import com.surimap.core.auth.OidcAccessTokenProvider
import com.surimap.core.auth.OidcSessionStateStore
import com.surimap.core.auth.OutboxAccessTokens
import com.surimap.core.database.SuriMapDatabaseProvider
import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.AndroidNetworkFactory
import java.util.concurrent.TimeUnit

data class OutboxReplayWorkRequest(
    val incidentId: String,
    val policePhoneId: String,
    val apiBaseUrl: String = BuildConfig.SURI_MAP_API_BASE_URL
) {
    val uniqueWorkName: String = "outbox-replay-$incidentId-$policePhoneId"

    fun toWorkRequest(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<OutboxWorker>()
            .setInputData(
                workDataOf(
                    OutboxWorker.KEY_INCIDENT_ID to incidentId,
                    OutboxWorker.KEY_POLICE_PHONE_ID to policePhoneId,
                    OutboxWorker.KEY_API_BASE_URL to apiBaseUrl
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
    private val apiBaseUrl: () -> String
) : SyncClient {
    constructor(
        delegate: SyncClient,
        scheduleReplay: (OutboxReplayWorkRequest) -> Unit,
        apiBaseUrl: String
    ) : this(delegate, scheduleReplay, { apiBaseUrl })

    override suspend fun enqueue(writeOperation: LocalWriteOperation): EnqueueResult {
        val result = delegate.enqueue(writeOperation)
        if (result.shouldScheduleReplay()) {
            scheduleReplay(
                OutboxReplayWorkRequest(
                    incidentId = writeOperation.incidentId,
                    policePhoneId = writeOperation.policePhoneId,
                    apiBaseUrl = apiBaseUrl()
                )
            )
        }
        return result
    }

    private fun EnqueueResult.shouldScheduleReplay(): Boolean =
        status in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED_RETRYABLE) &&
            harnessStatus == HarnessSyncStatus.PENDING_SEND
}

// 기록마다 작성 계정의 토큰으로 전송하는 replay다. 작성 계정이 로그인해 있으면 화면 세션의 토큰이 쓰인다.
// 작성 계정이 로그아웃했으면 그 계정의 offline token이 쓰인다.
fun createAuthorAwareOutboxReplay(context: Context, apiBaseUrl: String): RoomOutboxReplay {
    val accessTokens =
        OutboxAccessTokens(
            screenSessions = OidcSessionStateStore(context),
            offlineTokens = OfflineTokenStore(context),
            keycloak = KeycloakTokenClient()
        )
    return RoomOutboxReplay(
        outboxDao = SuriMapDatabaseProvider.database(context).outboxDao(),
        senderForAuthor =
            authorOutboxSenders(accessTokens::accessTokenFor) { accessToken ->
                AndroidNetworkFactory.createOutboxSender(
                    baseUrl = apiBaseUrl,
                    accessTokenProvider = AccessTokenProvider { accessToken }
                )
            },
        accessRepairAvailable = { accessTokens.screenAccountId() != null },
        enableRetryJitter = !BuildConfig.DEBUG
    )
}

fun authorOutboxSenders(
    accessTokenFor: suspend (authorAccountId: String?) -> AuthorAccessToken,
    senderForAccessToken: (accessToken: String) -> OutboxSender
): suspend (String?) -> AuthorOutboxSender = { authorAccountId ->
    when (val token = accessTokenFor(authorAccountId)) {
        is AuthorAccessToken.Available -> AuthorOutboxSender.Ready(senderForAccessToken(token.accessToken))
        AuthorAccessToken.Missing -> AuthorOutboxSender.NoToken
        AuthorAccessToken.Unavailable -> AuthorOutboxSender.TokenUnavailable
    }
}

fun interface OutboxReplayProvider {
    fun create(context: Context, apiBaseUrl: String, accessToken: String?): OutboxReplay
}

object LocalSyncRuntime {
    var outboxReplay: OutboxReplay? = null
    var outboxReplayProvider: OutboxReplayProvider? = null
    var outboxReplayScheduler: ((OutboxReplayWorkRequest) -> Unit)? = null
    var accessTokenProvider: AccessTokenProvider? = null
}

class OutboxWorker(appContext: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val incidentId = inputData.getString(KEY_INCIDENT_ID)?.takeIf(String::isNotBlank)
        val policePhoneId = inputData.getString(KEY_POLICE_PHONE_ID)?.takeIf(String::isNotBlank)
        val apiBaseUrl = inputData.getString(KEY_API_BASE_URL)
            ?.takeIf(String::isNotBlank)
            ?: BuildConfig.SURI_MAP_API_BASE_URL
        if (incidentId == null || policePhoneId == null) {
            return Result.failure()
        }
        val runtimeReplay =
            LocalSyncRuntime.outboxReplay
                ?: LocalSyncRuntime.outboxReplayProvider?.let { provider ->
                    val accessToken =
                        (LocalSyncRuntime.accessTokenProvider ?: OidcAccessTokenProvider(applicationContext))
                            .accessToken()
                    if (accessToken.isNullOrBlank()) {
                        return Result.retry()
                    }
                    provider.create(applicationContext, apiBaseUrl, accessToken)
                }
        // 아무도 로그인하지 않았어도 로그아웃한 계정의 기록은 그 계정의 offline token으로 전송된다.
        val replay = runtimeReplay ?: createAuthorAwareOutboxReplay(applicationContext, apiBaseUrl)
        val replayResult = replay.flushPending(
            policePhoneId = policePhoneId,
            incidentId = incidentId
        )
        if (runtimeReplay == null) {
            OfflineTokenRevocationWorker.schedule(applicationContext)
        }
        return if (replayResult.shouldRetry) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_INCIDENT_ID = "incidentId"
        const val KEY_POLICE_PHONE_ID = "policePhoneId"
        const val KEY_API_BASE_URL = "apiBaseUrl"
    }
}
