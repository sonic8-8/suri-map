package com.surimap.core.auth

import com.surimap.core.sync.OutboxStatus
import com.surimap.testing.AccountSessionHarness
import com.surimap.testing.AccountSessionHarness.Companion.ACCOUNT_A
import com.surimap.testing.AccountSessionHarness.Companion.ACCOUNT_B
import com.surimap.testing.SentRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// 한 업무폰에 여러 계정의 미전송 기록이 남았을 때 기록마다 작성 계정의 토큰으로 보내는지 확인한다.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthorOutboxTokenTest {
    private val phone = AccountSessionHarness()

    @After
    fun tearDown() {
        phone.close()
    }

    @Test
    fun loggedOutAccountRecordsAreSentWithItsOfflineTokenWhileAnotherAccountIsLoggedIn() = runBlocking {
        phone.logIn(ACCOUNT_A)
        phone.record(ACCOUNT_A, "a-1")
        phone.switchAccount()
        phone.logIn(ACCOUNT_B)
        phone.record(ACCOUNT_B, "b-1")

        phone.flush()

        assertEquals(listOf(SentRecord("a-1", ACCOUNT_A), SentRecord("b-1", ACCOUNT_B)), phone.sent)
        assertEquals(OutboxStatus.ACKED.name, phone.status("a-1"))
        assertEquals(OutboxStatus.ACKED.name, phone.status("b-1"))
    }

    @Test
    fun recordsOfAccountWithoutTokenStayInOutboxAndAreNotSentWithAnotherAccountToken() = runBlocking {
        phone.record(ACCOUNT_A, "a-1")
        phone.logIn(ACCOUNT_B)
        phone.record(ACCOUNT_B, "b-1")

        phone.flush()

        assertEquals(listOf(SentRecord("b-1", ACCOUNT_B)), phone.sent)
        assertEquals(OutboxStatus.PENDING.name, phone.status("a-1"))
    }

    @Test
    fun rejectedOfflineTokenRefreshDeletesTokenAndKeepsRecords() = runBlocking {
        val tokenA = phone.logIn(ACCOUNT_A)
        phone.record(ACCOUNT_A, "a-1")
        phone.switchAccount()
        phone.logIn(ACCOUNT_B)
        phone.keycloak.invalidate(tokenA)

        phone.flush()

        assertNull(phone.offlineTokens.load(ACCOUNT_A))
        assertEquals(OutboxStatus.PENDING.name, phone.status("a-1"))
        assertTrue(phone.sent.isEmpty())
    }

    @Test
    fun offlineTokenRefreshWithInvalidIdTokenIsNotSavedAndRecordsStay() = runBlocking {
        phone.logIn(ACCOUNT_A)
        phone.record(ACCOUNT_A, "a-1")
        phone.switchAccount()
        phone.keycloak.idTokenOverrides = mapOf("aud" to "suri-map-web", "azp" to "suri-map-web")

        phone.flush()

        assertNull(phone.offlineTokens.load(ACCOUNT_A))
        assertEquals(OutboxStatus.PENDING.name, phone.status("a-1"))
        assertTrue(phone.sent.isEmpty())
    }

    @Test
    fun screenSessionRefreshWithInvalidIdTokenIsNotSaved() = runBlocking {
        phone.logIn(ACCOUNT_A)
        val screenSession = phone.screenSessions.load()
        phone.record(ACCOUNT_A, "a-1")
        phone.keycloak.idTokenOverrides = mapOf("exp" to phone.nowMillis / 1_000L - 1L)

        phone.flush()

        assertEquals(screenSession, phone.screenSessions.load())
        assertEquals(OutboxStatus.PENDING.name, phone.status("a-1"))
        assertTrue(phone.sent.isEmpty())
    }

    @Test
    fun loggedOutAccountRecordsAreSentWithNobodyLoggedInAndAfterAppRestart() = runBlocking {
        phone.logIn(ACCOUNT_A)
        phone.record(ACCOUNT_A, "a-1")
        phone.switchAccount()
        phone.keycloak.networkDown = true

        val offlineResult = phone.flush()

        assertTrue(offlineResult.shouldRetry)
        assertEquals(OutboxStatus.PENDING.name, phone.status("a-1"))

        phone.restartApp()
        phone.keycloak.networkDown = false
        phone.flush()

        assertEquals(listOf(SentRecord("a-1", ACCOUNT_A)), phone.sent)
    }

    @Test
    fun recordWithoutAuthorFromBeforeUpdateIsSentWithLoggedInAccountToken() = runBlocking {
        phone.logIn(ACCOUNT_B)
        phone.record(authorAccountId = null, idempotencyKey = "legacy-1")

        phone.flush()

        assertEquals(listOf(SentRecord("legacy-1", ACCOUNT_B)), phone.sent)
    }

    @Test
    fun lateScreenRefreshResponseAfterAnotherLoginDoesNotOverwriteNewSession() = runBlocking {
        phone.logIn(ACCOUNT_A)
        phone.record(ACCOUNT_A, "a-1")
        val sessionOfA = phone.screenSessions.load()
        phone.keycloak.beforeRefreshResponse = {
            phone.keycloak.beforeRefreshResponse = null
            phone.switchAccount()
            phone.logIn(ACCOUNT_B)
        }

        phone.flush()

        assertEquals(ACCOUNT_B, phone.screenSessions.load()?.let(::authStateAccountId))
        assertEquals(sessionOfA, phone.offlineTokens.load(ACCOUNT_A))
        assertEquals(OutboxStatus.PENDING.name, phone.status("a-1"))

        phone.flush()

        assertEquals(listOf(SentRecord("a-1", ACCOUNT_A)), phone.sent)
    }

    @Test
    fun lateOfflineRefreshResponseAfterSameAccountLogsInAgainDoesNotRestoreOldToken() = runBlocking {
        phone.logIn(ACCOUNT_A)
        phone.record(ACCOUNT_A, "a-1")
        phone.switchAccount()
        phone.logIn(ACCOUNT_B)
        phone.keycloak.beforeRefreshResponse = {
            phone.keycloak.beforeRefreshResponse = null
            phone.switchAccount()
            phone.logIn(ACCOUNT_A)
        }

        phone.flush()

        assertNull(phone.offlineTokens.load(ACCOUNT_A))
        assertEquals(ACCOUNT_A, phone.screenSessions.load()?.let(::authStateAccountId))
        assertEquals(OutboxStatus.PENDING.name, phone.status("a-1"))

        phone.flush()

        assertEquals(listOf(SentRecord("a-1", ACCOUNT_A)), phone.sent)
    }

    @Test
    fun accountsLoggingInAndOutInTurnSendEachRecordWithItsAuthorToken() = runBlocking {
        val firstTokenA = phone.logIn(ACCOUNT_A)
        phone.record(ACCOUNT_A, "a-1")
        phone.switchAccount()
        val tokenB = phone.logIn(ACCOUNT_B)
        phone.record(ACCOUNT_B, "b-1")
        phone.switchAccount()
        val secondTokenA = phone.logIn(ACCOUNT_A)

        phone.flush()

        assertEquals(listOf(SentRecord("a-1", ACCOUNT_A), SentRecord("b-1", ACCOUNT_B)), phone.sent)
        val refreshedTokens = phone.keycloak.refreshCalls().map { it.form["refresh_token"] }
        assertEquals(listOf(secondTokenA, tokenB), refreshedTokens)
        assertTrue(firstTokenA !in refreshedTokens)
    }

    @Test
    fun dutyShiftEndWaitsOnlyForEarlierRecordsOfSameAuthor() = runBlocking {
        phone.record(ACCOUNT_A, "a-1")
        phone.recordDutyShiftEnd(ACCOUNT_A, "a-end")
        phone.logIn(ACCOUNT_B)
        phone.recordDutyShiftEnd(ACCOUNT_B, "b-end")
        phone.record(ACCOUNT_B, "b-2")

        phone.flush()

        assertEquals(listOf("b-end", "b-2"), phone.sent.map { it.idempotencyKey })
        assertEquals(OutboxStatus.PENDING.name, phone.status("a-1"))
        assertEquals(OutboxStatus.PENDING.name, phone.status("a-end"))
    }

    @Test
    fun accountLoggingInAgainAfterRejectedTokenSendsRecordsWithNewToken() = runBlocking {
        val firstTokenA = phone.logIn(ACCOUNT_A)
        phone.record(ACCOUNT_A, "a-1")
        phone.switchAccount()
        phone.logIn(ACCOUNT_B)
        phone.keycloak.invalidate(firstTokenA)
        phone.flush()
        assertNull(phone.offlineTokens.load(ACCOUNT_A))

        phone.switchAccount()
        phone.logIn(ACCOUNT_A)
        phone.flush()

        assertEquals(listOf(SentRecord("a-1", ACCOUNT_A)), phone.sent)
    }
}
