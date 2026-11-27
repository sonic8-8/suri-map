package com.surimap.testing

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.surimap.core.auth.AccountSessionLifecycle
import com.surimap.core.auth.KeycloakTokenClient
import com.surimap.core.auth.OfflineTokenRevocation
import com.surimap.core.auth.OfflineTokenStore
import com.surimap.core.auth.OidcSessionStateStore
import com.surimap.core.auth.OutboxAccessTokens
import com.surimap.core.database.SuriMapDatabase
import com.surimap.core.sync.DependencyGroup
import com.surimap.core.sync.LocalWriteOperation
import com.surimap.core.sync.OutboxReplayResult
import com.surimap.core.sync.OutboxSender
import com.surimap.core.sync.RoomOutboxReplay
import com.surimap.core.sync.RoomSyncClient
import com.surimap.core.sync.SendResult
import com.surimap.core.sync.authorOutboxSenders
import com.surimap.ui.navigation.accountIdClaim
import java.time.Instant
import org.robolectric.RuntimeEnvironment

data class SentRecord(val idempotencyKey: String, val tokenAccountId: String?)

// 업무폰 한 대에서 여러 사람이 각자 계정으로 번갈아 로그인하는 흐름을 테스트에서 재현한다.
// 화면 세션과 offline token 저장본은 SharedPreferences에 남는다. 그래서 저장소를 새로 만들어도 앱을 다시 켠 것과 같은 값을 읽는다.
class AccountSessionHarness(
    val database: SuriMapDatabase =
        Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SuriMapDatabase::class.java).build()
) {
    private val application = RuntimeEnvironment.getApplication()
    private var tokenSerial = 0
    private var recordSequence = 1L
    var nowMillis = System.currentTimeMillis()
    val keycloak = FakeKeycloak(nowMillis = { nowMillis })
    val screenSessions = OidcSessionStateStore(preferences(SCREEN_SESSION_PREFS))
    val offlineTokens = OfflineTokenStore(preferences(OFFLINE_TOKEN_PREFS))
    val sent = mutableListOf<SentRecord>()
    val replayScheduledFor = mutableListOf<String>()
    var revocationScheduledCount = 0
    private val revocationRejected = mutableSetOf<String>()
    val accountSessions =
        AccountSessionLifecycle(
            screenSessions = screenSessions,
            offlineTokens = offlineTokens,
            scheduleReplay = { replayScheduledFor += it },
            scheduleRevocation = { revocationScheduledCount += 1 }
        )

    init {
        preferences(SCREEN_SESSION_PREFS).edit().clear().commit()
        preferences(OFFLINE_TOKEN_PREFS).edit().clear().commit()
    }

    // 로그인을 마친 것처럼 새 offline token을 받아 화면 세션으로 저장한다. 받은 offline token을 돌려준다.
    fun logIn(accountId: String): String {
        tokenSerial += 1
        val refreshToken = "offline-$accountId-$tokenSerial"
        keycloak.issue(accountId, refreshToken)
        accountSessions.loggedIn(loggedInAuthStateJson(accountId, refreshToken, issuedAtEpochMs = nowMillis))
        return refreshToken
    }

    fun switchAccount() = accountSessions.switchAccount()

    suspend fun record(
        authorAccountId: String?,
        idempotencyKey: String,
        incidentId: String = INCIDENT_ID
    ): String =
        enqueue(
            authorAccountId = authorAccountId,
            idempotencyKey = idempotencyKey,
            incidentId = incidentId,
            dependencyGroup = DependencyGroup.HANDOVER_MEMO,
            method = "POST",
            endpoint = "/api/handover-memos",
            payload = "{}"
        )

    suspend fun recordDutyShiftEnd(authorAccountId: String, idempotencyKey: String): String =
        enqueue(
            authorAccountId = authorAccountId,
            idempotencyKey = idempotencyKey,
            incidentId = INCIDENT_ID,
            dependencyGroup = DependencyGroup.DUTY_SHIFT,
            method = "PATCH",
            endpoint = "/api/duty-shifts/${dutyShiftIdFixture(authorAccountId)}",
            payload = """{"action":"END"}"""
        )

    private suspend fun enqueue(
        authorAccountId: String?,
        idempotencyKey: String,
        incidentId: String,
        dependencyGroup: DependencyGroup,
        method: String,
        endpoint: String,
        payload: String
    ): String {
        val clientTs = Instant.ofEpochMilli(System.currentTimeMillis())
        RoomSyncClient(database).enqueue(
            LocalWriteOperation(
                operationId = operationIdFixture(idempotencyKey),
                incidentId = incidentId,
                policePhoneId = POLICE_PHONE_ID,
                dependencyGroup = dependencyGroup,
                sequence = recordSequence++,
                method = method,
                endpoint = endpoint,
                payload = payload,
                bodyHash = "sha256:$idempotencyKey",
                idempotencyKey = idempotencyKey,
                clientTs = clientTs,
                clockOffsetMs = 0L,
                clockSyncedAt = clientTs,
                accountId = authorAccountId
            )
        )
        return idempotencyKey
    }

    suspend fun status(idempotencyKey: String): String =
        database.outboxDao().findByIdempotencyKey(idempotencyKey)!!.idempotencyStatus

    suspend fun setStatus(idempotencyKey: String, status: String, nextAttemptAt: Long? = null) {
        val row = database.outboxDao().findByIdempotencyKey(idempotencyKey)!!
        database.outboxDao().upsert(row.copy(idempotencyStatus = status, nextAttemptAt = nextAttemptAt))
    }

    // 실제 앱과 같이 전송할 때마다 저장소를 새로 읽는다. 토큰은 작성 계정별로 고른다.
    suspend fun flush(incidentId: String = INCIDENT_ID, policePhoneId: String = POLICE_PHONE_ID): OutboxReplayResult {
        val accessTokens =
            OutboxAccessTokens(
                screenSessions = OidcSessionStateStore(preferences(SCREEN_SESSION_PREFS)),
                offlineTokens = OfflineTokenStore(preferences(OFFLINE_TOKEN_PREFS)),
                keycloak = keycloakClient()
            )
        return RoomOutboxReplay(
            outboxDao = database.outboxDao(),
            senderForAuthor =
                authorOutboxSenders(accessTokens::accessTokenFor) { accessToken ->
                    OutboxSender { row ->
                        sent += SentRecord(row.idempotencyKey, accessToken.accountIdClaim())
                        SendResult.ACKED
                    }
                }
        ).flushPending(policePhoneId = policePhoneId, incidentId = incidentId)
    }

    // 가짜 Keycloak과 같은 시각으로 ID token의 만료 시각과 발급 시각을 검증한다.
    fun keycloakClient(): KeycloakTokenClient = KeycloakTokenClient(callFactory = keycloak, nowMillis = { nowMillis })

    suspend fun revokeTokensWithoutUnsentRecords(): Boolean =
        OfflineTokenRevocation(
            offlineTokens = OfflineTokenStore(preferences(OFFLINE_TOKEN_PREFS)),
            screenSessions = OidcSessionStateStore(preferences(SCREEN_SESSION_PREFS)),
            unsentRecordCount = database.outboxDao()::countUnsentRowsByAuthor,
            keycloak = keycloakClient(),
            revocationRejected = revocationRejected,
            nowMillis = { nowMillis }
        ).revokeTokensWithoutUnsentRecords()

    // 앱 프로세스를 다시 띄운 경우다. 철회가 거절된 계정 목록은 메모리에만 있어서 비워진다.
    fun restartApp() {
        revocationRejected.clear()
    }

    fun close() {
        database.close()
    }

    private fun preferences(name: String): SharedPreferences = application.getSharedPreferences(name, Context.MODE_PRIVATE)

    companion object {
        private const val SCREEN_SESSION_PREFS = "test_screen_session"
        private const val OFFLINE_TOKEN_PREFS = "test_offline_tokens"
        const val ACCOUNT_A = "account-a"
        const val ACCOUNT_B = "account-b"
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val OTHER_INCIDENT_ID = incidentIdFixture("precinct-second-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-car-01")
    }
}
