package com.surimap.core.auth

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.surimap.core.database.SuriMapDatabaseProvider
import java.util.Collections
import java.util.concurrent.TimeUnit

// 마지막 갱신 뒤 30일이 지난 offline token은 Keycloak에서 끝난다(offlineSessionIdleTimeout=2592000).
private const val OFFLINE_TOKEN_IDLE_LIMIT_MS = 2_592_000_000L

// 철회가 거절된 계정은 다음에 앱을 켤 때까지 다시 요청하지 않는다. 앱 프로세스가 새로 뜨면 비어 있다.
private val revocationRejectedInThisProcess: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

class OfflineTokenRevocation(
    private val offlineTokens: OfflineTokenStore,
    private val screenSessions: OidcSessionStateStore,
    private val unsentRecordCount: suspend (accountId: String) -> Int,
    private val keycloak: KeycloakTokenClient,
    private val revocationRejected: MutableSet<String> = revocationRejectedInThisProcess,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    // 로그아웃한 계정의 미전송 기록이 모든 사건에서 0건이면 그 계정의 offline token 철회를 요청한다.
    // 통신 문제로 철회하지 못한 토큰이 있으면 true다. 이때는 통신이 돌아오면 다시 실행된다.
    suspend fun revokeTokensWithoutUnsentRecords(): Boolean {
        val screenAccountId = screenSessions.load()?.let(::authStateAccountId)
        var retryNeeded = false
        for (accountId in offlineTokens.accountIds()) {
            val loaded = offlineTokens.load(accountId) ?: continue
            if (isIdleTooLong(loaded)) {
                // Keycloak에서 이미 끝난 토큰이라 철회는 요청하지 않는다. 그 계정의 기록은 남는다.
                offlineTokens.removeIfUnchanged(accountId, loaded)
                continue
            }
            if (accountId == screenAccountId || accountId in revocationRejected) {
                continue
            }
            if (unsentRecordCount(accountId) > 0) {
                continue
            }
            // 그 사이에 같은 계정으로 다시 로그인해 철회가 취소됐으면 보내지 않는다.
            if (offlineTokens.load(accountId) != loaded) {
                continue
            }
            when (keycloak.revoke(loaded)) {
                OfflineTokenRevocationResult.Revoked -> offlineTokens.removeIfUnchanged(accountId, loaded)
                OfflineTokenRevocationResult.Unavailable -> retryNeeded = true
                OfflineTokenRevocationResult.Rejected -> revocationRejected += accountId
            }
        }
        return retryNeeded
    }

    private fun isIdleTooLong(authStateJson: String): Boolean {
        val lastRefreshedAt = authStateLastRefreshedAtEpochMs(authStateJson) ?: return false
        return nowMillis() - lastRefreshedAt >= OFFLINE_TOKEN_IDLE_LIMIT_MS
    }
}

// 철회 작업이다. 통신이 끊긴 동안에는 실행되지 않는다. 철회하지 못한 토큰이 남으면 다시 실행된다.
class OfflineTokenRevocationWorker(appContext: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val retryNeeded =
            OfflineTokenRevocation(
                offlineTokens = OfflineTokenStore(applicationContext),
                screenSessions = OidcSessionStateStore(applicationContext),
                unsentRecordCount = SuriMapDatabaseProvider.database(applicationContext).outboxDao()::countUnsentRowsByAuthor,
                keycloak = KeycloakTokenClient()
            ).revokeTokensWithoutUnsentRecords()
        return if (retryNeeded) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "offline-token-revocation"

        fun request(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<OfflineTokenRevocationWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

        // 앱을 켤 때와 로그아웃할 때, outbox를 보낸 뒤에 예약된다. 실행 중인 작업은 새 요청으로 바뀐다.
        fun schedule(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request())
        }
    }
}
