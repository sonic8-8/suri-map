package com.surimap.core.auth

import com.surimap.core.database.SearchRecordingStateEntity
import com.surimap.core.sync.OutboxStatus
import com.surimap.feature.bootstrap.data.AndroidOidcLoginClient
import com.surimap.feature.bootstrap.data.OfflineStartupState
import com.surimap.feature.bootstrap.data.OfflineStartupStateLoader
import com.surimap.feature.bootstrap.data.OidcSessionRefreshResult
import com.surimap.feature.bootstrap.data.decideBootstrapOidcSession
import com.surimap.testing.AccountSessionHarness
import com.surimap.testing.AccountSessionHarness.Companion.ACCOUNT_A
import com.surimap.testing.AccountSessionHarness.Companion.ACCOUNT_B
import com.surimap.testing.AccountSessionHarness.Companion.INCIDENT_ID
import com.surimap.testing.AccountSessionHarness.Companion.POLICE_PHONE_ID
import com.surimap.testing.LEGACY_SCOPE
import com.surimap.testing.loggedInAuthStateJson
import com.surimap.testing.opIdFixture
import com.surimap.testing.pathIdFixture
import com.surimap.ui.navigation.PolicePhoneContext
import com.surimap.ui.navigation.offlineStartupAccountId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// 화면 세션을 쓸 수 없게 됐을 때 로그인 화면이 열리는지와 미전송 기록이 남는지 확인한다.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountSessionLifecycleTest {
    private val phone = AccountSessionHarness()

    @After
    fun tearDown() {
        phone.close()
    }

    @Test
    fun sessionWithoutOfflineTokenOpensLoginEvenOnDirectEntryRestartAndKeepsRecords() = runBlocking {
        phone.screenSessions.save(loggedInAuthStateJson(ACCOUNT_A, "legacy-a", phone.nowMillis, scope = LEGACY_SCOPE))
        phone.record(ACCOUNT_A, "a-1")
        phone.database.searchRecordingStateDao().upsert(recoverableRecordingOf(ACCOUNT_A))

        val restored = phone.accountSessions.screenSessionWithOfflineToken()

        assertNull(restored)
        assertNull(phone.screenSessions.load())
        assertEquals(OfflineStartupState.RequiresConnection, startupStateFor(restored))
        assertEquals(OutboxStatus.PENDING.name, phone.status("a-1"))
    }

    @Test
    fun sessionWithOfflineTokenStillEntersRecoveredSearchDirectly() = runBlocking {
        phone.logIn(ACCOUNT_A)
        phone.database.searchRecordingStateDao().upsert(recoverableRecordingOf(ACCOUNT_A))

        val restored = phone.accountSessions.screenSessionWithOfflineToken()

        assertTrue(startupStateFor(restored) is OfflineStartupState.SearchMap)
    }

    @Test
    fun rejectedRefreshAtStartupDeletesSessionAndKeepsRecords() = runBlocking {
        val tokenA = phone.logIn(ACCOUNT_A)
        phone.record(ACCOUNT_A, "a-1")
        phone.keycloak.invalidate(tokenA)

        rejectIfRefreshRequiresLogin(phone.screenSessions.load()!!)

        assertNull(phone.screenSessions.load())
        assertNull(phone.offlineTokens.load(ACCOUNT_A))
        assertEquals(OutboxStatus.PENDING.name, phone.status("a-1"))
    }

    @Test
    fun unavailableRefreshKeepsSession() = runBlocking {
        phone.logIn(ACCOUNT_A)
        val session = phone.screenSessions.load()!!
        phone.keycloak.networkDown = true

        val decision = rejectIfRefreshRequiresLogin(session)

        assertFalse(decision)
        assertEquals(session, phone.screenSessions.load())
    }

    @Test
    fun screenRefreshAfterBackgroundRefreshOfSameAccountUsesStoredSession() = runBlocking {
        phone.logIn(ACCOUNT_A)
        val sessionOnScreen = phone.screenSessions.load()!!
        phone.record(ACCOUNT_A, "a-1")
        phone.flush()
        val storedByBackground = phone.screenSessions.load()!!
        assertNotEquals(sessionOnScreen, storedByBackground)
        val screenRefresh = refreshedAuthStateJson(sessionOnScreen)

        val sessionToUse = phone.accountSessions.screenSessionRefreshed(sessionOnScreen, screenRefresh)

        assertEquals(storedByBackground, sessionToUse)
        assertEquals(storedByBackground, phone.screenSessions.load())
    }

    @Test
    fun screenRefreshAfterAnotherAccountLoggedInIsIgnored() = runBlocking {
        phone.logIn(ACCOUNT_A)
        val sessionOfA = phone.screenSessions.load()!!
        val screenRefresh = refreshedAuthStateJson(sessionOfA)
        phone.switchAccount()
        phone.logIn(ACCOUNT_B)
        val sessionOfB = phone.screenSessions.load()

        val sessionToUse = phone.accountSessions.screenSessionRefreshed(sessionOfA, screenRefresh)

        assertNull(sessionToUse)
        assertEquals(sessionOfB, phone.screenSessions.load())
    }

    @Test
    fun screenRefreshAfterLogoutIsIgnored() = runBlocking {
        phone.logIn(ACCOUNT_A)
        val sessionOfA = phone.screenSessions.load()!!
        val screenRefresh = refreshedAuthStateJson(sessionOfA)
        phone.switchAccount()

        assertNull(phone.accountSessions.screenSessionRefreshed(sessionOfA, screenRefresh))
        assertNull(phone.screenSessions.load())
    }

    @Test
    fun screenRefreshWithoutOtherChangeSavesRefreshedSession() = runBlocking {
        phone.logIn(ACCOUNT_A)
        val sessionOfA = phone.screenSessions.load()!!
        val screenRefresh = refreshedAuthStateJson(sessionOfA)

        assertEquals(screenRefresh, phone.accountSessions.screenSessionRefreshed(sessionOfA, screenRefresh))
        assertEquals(screenRefresh, phone.screenSessions.load())
    }

    @Test
    fun loggingInSchedulesSendingOfThatAccountRecords() {
        phone.logIn(ACCOUNT_A)
        phone.switchAccount()

        assertEquals(listOf(ACCOUNT_A, ACCOUNT_A), phone.replayScheduledFor)
    }

    private fun recoverableRecordingOf(accountId: String) =
        SearchRecordingStateEntity(
            accountId = accountId,
            incidentId = INCIDENT_ID,
            opId = opIdFixture("precinct-first-001"),
            searchPathId = pathIdFixture("precinct-first-001"),
            lifecycleStatus = "ACTIVE",
            activeStartedAt = phone.nowMillis,
            accumulatedElapsed = 0L,
            updatedAt = phone.nowMillis
        )

    // 앱을 다시 켤 때 화면 세션의 토큰으로 수색 화면에 바로 들어갈지 정하는 흐름이다.
    private suspend fun startupStateFor(restoredAuthStateJson: String?): OfflineStartupState {
        val context =
            PolicePhoneContext(
                policePhoneId = POLICE_PHONE_ID,
                apiBaseUrl = "https://suri-map.internal",
                tileBaseUrl = "https://suri-map.internal",
                objectStorageBaseUrl = "https://suri-map.internal",
                accessToken = AndroidOidcLoginClient.sessionFromAuthStateJson(restoredAuthStateJson)?.accessToken
            )
        return OfflineStartupStateLoader(
            incidentSummaryDao = phone.database.incidentSummaryDao(),
            searchRecordingStateDao = phone.database.searchRecordingStateDao()
        ).load(context.offlineStartupAccountId())
    }

    // 앱을 시작할 때와 사용 중에 화면 세션을 갱신하는 흐름과 같이 판단한다. 세션을 지웠으면 true다.
    private suspend fun rejectIfRefreshRequiresLogin(session: String): Boolean {
        val refreshResult = phone.keycloakClient().refresh(session)
        val decision =
            decideBootstrapOidcSession(AndroidOidcLoginClient.sessionFromAuthStateJson(session)!!, refreshResult)
        if (decision.endsRestoredSession) {
            phone.accountSessions.screenSessionRejected()
        }
        assertEquals(refreshResult == OidcSessionRefreshResult.AuthenticationRequired, decision.endsRestoredSession)
        return decision.endsRestoredSession
    }

    // 화면의 갱신 흐름에서 받은 갱신 응답이다. 저장소에는 저장하지 않는다.
    private suspend fun refreshedAuthStateJson(session: String): String =
        (phone.keycloakClient().refresh(session) as OidcSessionRefreshResult.Refreshed).session.authStateJson
}
