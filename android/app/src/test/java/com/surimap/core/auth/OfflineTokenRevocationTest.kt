package com.surimap.core.auth

import com.surimap.core.sync.OutboxStatus
import com.surimap.feature.bootstrap.data.AndroidOidcLoginClient
import com.surimap.feature.bootstrap.data.OidcSessionRefreshResult
import com.surimap.feature.bootstrap.data.decideBootstrapOidcSession
import com.surimap.testing.AccountSessionHarness
import com.surimap.testing.AccountSessionHarness.Companion.ACCOUNT_A
import com.surimap.testing.AccountSessionHarness.Companion.OTHER_INCIDENT_ID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// 로그아웃한 계정의 offline token을 언제 철회하고 언제 업무폰에서만 지우는지 확인한다.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineTokenRevocationTest {
    private var phone = AccountSessionHarness()

    @After
    fun tearDown() {
        phone.close()
    }

    @Test
    fun switchingAccountWithUnsentRecordsKeepsOfflineToken() = runBlocking {
        phone.logIn(ACCOUNT_A)
        phone.record(ACCOUNT_A, "a-1")

        phone.switchAccount()
        phone.revokeTokensWithoutUnsentRecords()

        assertEquals(1, phone.revocationScheduledCount)
        assertTrue(phone.keycloak.revokeCalls().isEmpty())
        assertNotNull(phone.offlineTokens.load(ACCOUNT_A))
    }

    @Test
    fun switchingAccountWithoutUnsentRecordsRevokesAndDeletesOfflineToken() = runBlocking {
        val tokenA = phone.logIn(ACCOUNT_A)

        phone.switchAccount()
        val retryNeeded = phone.revokeTokensWithoutUnsentRecords()

        assertFalse(retryNeeded)
        assertEquals(tokenA, phone.keycloak.revokeCalls().single().form["token"])
        assertFalse(phone.keycloak.isValid(tokenA))
        assertNull(phone.offlineTokens.load(ACCOUNT_A))
    }

    @Test
    fun revocationIsRequestedAfterLastRecordIsSent() = runBlocking {
        phone.logIn(ACCOUNT_A)
        phone.record(ACCOUNT_A, "a-1")
        phone.switchAccount()
        phone.revokeTokensWithoutUnsentRecords()
        assertTrue(phone.keycloak.revokeCalls().isEmpty())

        phone.flush()
        phone.revokeTokensWithoutUnsentRecords()

        assertEquals(1, phone.keycloak.revokeCalls().size)
        assertNull(phone.offlineTokens.load(ACCOUNT_A))
    }

    @Test
    fun unsentRecordInAnotherIncidentKeepsOfflineToken() = runBlocking {
        phone.logIn(ACCOUNT_A)
        phone.record(ACCOUNT_A, "a-other-incident", incidentId = OTHER_INCIDENT_ID)
        phone.switchAccount()

        phone.revokeTokensWithoutUnsentRecords()

        assertTrue(phone.keycloak.revokeCalls().isEmpty())
        assertNotNull(phone.offlineTokens.load(ACCOUNT_A))
    }

    @Test
    fun sendingRetryWaitingAndFinallyFailedRecordsKeepOfflineToken() = runBlocking {
        val statuses =
            listOf(
                OutboxStatus.SENDING.name to null,
                OutboxStatus.FAILED_RETRYABLE.name to System.currentTimeMillis() + 3_600_000L,
                OutboxStatus.FAILED_FINAL.name to null
            )
        for ((status, nextAttemptAt) in statuses) {
            phone.close()
            phone = AccountSessionHarness()
            phone.logIn(ACCOUNT_A)
            phone.record(ACCOUNT_A, "a-1")
            phone.setStatus("a-1", status, nextAttemptAt)
            phone.switchAccount()

            phone.revokeTokensWithoutUnsentRecords()

            assertTrue(status, phone.keycloak.revokeCalls().isEmpty())
            assertNotNull(status, phone.offlineTokens.load(ACCOUNT_A))
        }
    }

    @Test
    fun loggedInAccountIsNotRevoked() = runBlocking {
        phone.logIn(ACCOUNT_A)
        phone.offlineTokens.save(ACCOUNT_A, phone.screenSessions.load()!!)

        phone.revokeTokensWithoutUnsentRecords()

        assertTrue(phone.keycloak.revokeCalls().isEmpty())
    }

    @Test
    fun revocationFailedByNetworkIsRetriedAfterRestartWhenNetworkReturns() = runBlocking {
        val tokenA = phone.logIn(ACCOUNT_A)
        phone.switchAccount()
        phone.keycloak.networkDown = true

        assertTrue(phone.revokeTokensWithoutUnsentRecords())
        assertNotNull(phone.offlineTokens.load(ACCOUNT_A))

        phone.restartApp()
        phone.keycloak.networkDown = false

        assertFalse(phone.revokeTokensWithoutUnsentRecords())
        assertFalse(phone.keycloak.isValid(tokenA))
        assertNull(phone.offlineTokens.load(ACCOUNT_A))
    }

    @Test
    fun alreadyInvalidResponseDeletesOfflineToken() = runBlocking {
        val tokenA = phone.logIn(ACCOUNT_A)
        phone.switchAccount()
        phone.keycloak.invalidate(tokenA)

        phone.revokeTokensWithoutUnsentRecords()

        assertEquals(1, phone.keycloak.revokeCalls().size)
        assertNull(phone.offlineTokens.load(ACCOUNT_A))
    }

    @Test
    fun otherRejectionKeepsTokenAndRetriesOnlyAtNextAppStart() = runBlocking {
        phone.logIn(ACCOUNT_A)
        phone.switchAccount()
        phone.keycloak.revokeResponse = 401 to """{"error":"unauthorized_client"}"""

        assertFalse(phone.revokeTokensWithoutUnsentRecords())
        phone.revokeTokensWithoutUnsentRecords()

        assertEquals(1, phone.keycloak.revokeCalls().size)
        assertNotNull(phone.offlineTokens.load(ACCOUNT_A))

        phone.restartApp()
        phone.keycloak.revokeResponse = null
        phone.revokeTokensWithoutUnsentRecords()

        assertEquals(2, phone.keycloak.revokeCalls().size)
        assertNull(phone.offlineTokens.load(ACCOUNT_A))
    }

    @Test
    fun loggingInAgainCancelsUnsentRevocationAndDeletesOldTokenOnlyOnPhone() = runBlocking {
        val firstTokenA = phone.logIn(ACCOUNT_A)
        phone.switchAccount()
        phone.keycloak.networkDown = true
        phone.revokeTokensWithoutUnsentRecords()
        phone.keycloak.networkDown = false

        phone.logIn(ACCOUNT_A)
        phone.revokeTokensWithoutUnsentRecords()

        assertNull(phone.offlineTokens.load(ACCOUNT_A))
        assertTrue(phone.keycloak.revokeCalls().isEmpty())
        assertTrue(phone.keycloak.isValid(firstTokenA))
    }

    @Test
    fun lateRevokeResponseKeepsNewSessionAndRejectedRefreshOpensLoginWithRecordsKept() = runBlocking {
        phone.logIn(ACCOUNT_A)
        phone.switchAccount()
        phone.keycloak.beforeRevokeResponse = {
            phone.keycloak.beforeRevokeResponse = null
            phone.logIn(ACCOUNT_A)
        }

        phone.revokeTokensWithoutUnsentRecords()

        val newSession = phone.screenSessions.load()!!
        assertEquals(ACCOUNT_A, authStateAccountId(newSession))
        assertNull(phone.offlineTokens.load(ACCOUNT_A))

        phone.record(ACCOUNT_A, "a-1")
        phone.flush()
        val refreshResult = phone.keycloakClient().refresh(newSession)
        val decision =
            decideBootstrapOidcSession(AndroidOidcLoginClient.sessionFromAuthStateJson(newSession)!!, refreshResult)
        if (decision.endsRestoredSession) {
            phone.accountSessions.screenSessionRejected()
        }

        assertEquals(OidcSessionRefreshResult.AuthenticationRequired, refreshResult)
        assertNull(phone.screenSessions.load())
        assertEquals(OutboxStatus.PENDING.name, phone.status("a-1"))
    }

    @Test
    fun offlineTokenWithoutRefreshForThirtyDaysIsDeletedWithoutRevocation() = runBlocking {
        val loggedOutAt = phone.nowMillis
        phone.logIn(ACCOUNT_A)
        phone.record(ACCOUNT_A, "a-1")
        phone.switchAccount()

        phone.nowMillis = loggedOutAt + THIRTY_DAYS_MS - 1_000L
        phone.revokeTokensWithoutUnsentRecords()
        assertNotNull(phone.offlineTokens.load(ACCOUNT_A))

        phone.nowMillis = loggedOutAt + THIRTY_DAYS_MS
        phone.revokeTokensWithoutUnsentRecords()

        assertNull(phone.offlineTokens.load(ACCOUNT_A))
        assertTrue(phone.keycloak.revokeCalls().isEmpty())
        assertEquals(OutboxStatus.PENDING.name, phone.status("a-1"))
    }

    private companion object {
        const val THIRTY_DAYS_MS = 2_592_000_000L
    }
}
