package com.surimap.core.fcm

import com.surimap.core.network.RegisterFcmTokenNetworkRequest
import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.network.SuriMapNetworkException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FcmRegistrationCoordinatorTest {

    @Test
    fun registersCurrentFirebaseTokenWithStableAppInstanceId() = runBlocking {
        val stateStore = FakeFcmRegistrationStateStore(appInstanceId = "app-instance-001")
        val requests = mutableListOf<RegisterFcmTokenNetworkRequest>()
        val coordinator =
            coordinator(
                tokenProvider = FcmTokenProvider { "fcm-token-001" },
                stateStore = stateStore,
                registerToken = { request ->
                    requests += request
                    response(statusCode = 200)
                }
            )

        val result = coordinator.registerCurrentToken(ACCOUNT_A_ID, POLICE_PHONE_ID)

        assertEquals(FcmRegistrationResult.Registered, result)
        assertEquals(
            RegisterFcmTokenNetworkRequest(
                policePhoneId = POLICE_PHONE_ID,
                appInstanceId = "app-instance-001",
                token = "fcm-token-001"
            ),
            requests.single()
        )
        assertEquals(ACCOUNT_A_ID, stateStore.registeredAccountId)
        assertEquals(POLICE_PHONE_ID, stateStore.registeredPolicePhoneId)
        assertEquals("fcm-token-001", stateStore.registeredToken)
        assertNull(stateStore.pendingToken())
    }

    @Test
    fun disabledFirebaseMessagingDoesNotReadOrRegisterToken() = runBlocking {
        var tokenProviderCalled = false
        var registerCalled = false
        val coordinator =
            coordinator(
                firebaseMessagingEnabled = false,
                tokenProvider =
                FcmTokenProvider {
                    tokenProviderCalled = true
                    "fcm-token-001"
                },
                registerToken = {
                    registerCalled = true
                    response(statusCode = 200)
                }
            )

        val result = coordinator.registerCurrentToken(ACCOUNT_A_ID, POLICE_PHONE_ID)

        assertEquals(FcmRegistrationResult.Disabled, result)
        assertFalse(tokenProviderCalled)
        assertFalse(registerCalled)
    }

    @Test
    fun pendingRefreshTokenIsRegisteredBeforeReadingCurrentToken() = runBlocking {
        var tokenProviderCalled = false
        val stateStore =
            FakeFcmRegistrationStateStore(appInstanceId = "app-instance-001")
                .apply { savePendingToken("fcm-token-refresh") }
        val requests = mutableListOf<RegisterFcmTokenNetworkRequest>()
        val coordinator =
            coordinator(
                tokenProvider =
                FcmTokenProvider {
                    tokenProviderCalled = true
                    "fcm-token-current"
                },
                stateStore = stateStore,
                registerToken = { request ->
                    requests += request
                    response(statusCode = 200)
                }
            )

        val result = coordinator.registerCurrentToken(ACCOUNT_A_ID, POLICE_PHONE_ID)

        assertEquals(FcmRegistrationResult.Registered, result)
        assertFalse(tokenProviderCalled)
        assertEquals("fcm-token-refresh", requests.single().token)
        assertNull(stateStore.pendingToken())
    }

    @Test
    fun serverFailureKeepsPendingTokenForNextBootstrap() = runBlocking {
        val stateStore =
            FakeFcmRegistrationStateStore(appInstanceId = "app-instance-001")
                .apply { savePendingToken("fcm-token-refresh") }
        val coordinator =
            coordinator(
                tokenProvider = FcmTokenProvider { "fcm-token-current" },
                stateStore = stateStore,
                registerToken = {
                    response(statusCode = 403, errorCode = "police_phone_not_assigned")
                }
            )

        val result = coordinator.registerCurrentToken(ACCOUNT_A_ID, POLICE_PHONE_ID)

        assertEquals(
            FcmRegistrationResult.Failed(
                statusCode = 403,
                errorCode = "police_phone_not_assigned"
            ),
            result
        )
        assertEquals("fcm-token-refresh", stateStore.pendingToken())
    }

    @Test
    fun alreadyRegisteredTokenClearsPendingDuplicateWithoutApiCall() = runBlocking {
        var registerCalled = false
        val stateStore =
            FakeFcmRegistrationStateStore(appInstanceId = "app-instance-001")
                .apply {
                    registeredAccountId = ACCOUNT_A_ID
                    registeredPolicePhoneId = POLICE_PHONE_ID
                    registeredToken = "fcm-token-001"
                    savePendingToken("fcm-token-001")
                }
        val coordinator =
            coordinator(
                tokenProvider = FcmTokenProvider { "fcm-token-001" },
                stateStore = stateStore,
                registerToken = {
                    registerCalled = true
                    response(statusCode = 200)
                }
            )

        val result = coordinator.registerCurrentToken(ACCOUNT_A_ID, POLICE_PHONE_ID)

        assertEquals(FcmRegistrationResult.Skipped, result)
        assertFalse(registerCalled)
        assertNull(stateStore.pendingToken())
    }

    @Test
    fun signedInAccountChangeRegistersAgainWithSamePolicePhoneAndToken() = runBlocking {
        // A로 등록해 둔 업무폰에서 B로 로그인했다. 업무폰 ID와 토큰은 그대로다.
        val stateStore =
            FakeFcmRegistrationStateStore(appInstanceId = "app-instance-001")
                .apply {
                    registeredAccountId = ACCOUNT_A_ID
                    registeredPolicePhoneId = POLICE_PHONE_ID
                    registeredToken = "fcm-token-001"
                }
        val requests = mutableListOf<RegisterFcmTokenNetworkRequest>()
        val coordinator =
            coordinator(
                tokenProvider = FcmTokenProvider { "fcm-token-001" },
                stateStore = stateStore,
                registerToken = { request ->
                    requests += request
                    response(statusCode = 200)
                }
            )

        val result = coordinator.registerCurrentToken(ACCOUNT_B_ID, POLICE_PHONE_ID)

        assertEquals(FcmRegistrationResult.Registered, result)
        assertEquals("fcm-token-001", requests.single().token)
        assertEquals(ACCOUNT_B_ID, stateStore.registeredAccountId)
    }

    @Test
    fun logoutRequestsRevokeBeforeDeletingToken() = runBlocking {
        val calls = mutableListOf<String>()
        val stateStore =
            FakeFcmRegistrationStateStore(calls = calls)
                .apply {
                    registeredAccountId = ACCOUNT_A_ID
                    registeredPolicePhoneId = POLICE_PHONE_ID
                    registeredToken = "fcm-token-001"
                }
        val coordinator =
            coordinator(
                tokenProvider = RecordingFcmTokenProvider(calls),
                stateStore = stateStore,
                revokeToken = { policePhoneId ->
                    calls += "revoke:$policePhoneId"
                    response(statusCode = 204)
                }
            )

        coordinator.revokeForLogout(POLICE_PHONE_ID)

        assertEquals(
            listOf("revoke:$POLICE_PHONE_ID", "deleteToken", "clearRegistration"),
            calls
        )
        assertNull(stateStore.registeredAccountId)
        assertNull(stateStore.registeredToken)
    }

    @Test
    fun logoutDeletesTokenEvenWhenRevokeRequestFails() = runBlocking {
        val calls = mutableListOf<String>()
        val coordinator =
            coordinator(
                tokenProvider = RecordingFcmTokenProvider(calls),
                stateStore = FakeFcmRegistrationStateStore(calls = calls),
                revokeToken = {
                    calls += "revoke"
                    throw SuriMapNetworkException(java.io.IOException("offline"))
                }
            )

        coordinator.revokeForLogout(POLICE_PHONE_ID)

        assertEquals(listOf("revoke", "deleteToken", "clearRegistration"), calls)
    }

    @Test
    fun logoutDeletesTokenEvenWhenServerRejectsRevoke() = runBlocking {
        val calls = mutableListOf<String>()
        val coordinator =
            coordinator(
                tokenProvider = RecordingFcmTokenProvider(calls),
                stateStore = FakeFcmRegistrationStateStore(calls = calls),
                revokeToken = {
                    calls += "revoke"
                    response(statusCode = 401, errorCode = "unauthorized")
                }
            )

        coordinator.revokeForLogout(POLICE_PHONE_ID)

        assertEquals(listOf("revoke", "deleteToken", "clearRegistration"), calls)
    }

    @Test
    fun logoutCleanupDeletesTokenEvenWhenCancelledRightAfterStart() = runBlocking {
        // 로그아웃을 누른 화면이 해제 응답 전에 사라진 경우다.
        val calls = mutableListOf<String>()
        val coordinator =
            coordinator(
                tokenProvider = RecordingFcmTokenProvider(calls),
                stateStore = FakeFcmRegistrationStateStore(calls = calls),
                revokeToken = {
                    calls += "revoke"
                    awaitCancellation()
                }
            )

        coordinator.startLogoutCleanup(this, POLICE_PHONE_ID).cancelAndJoin()

        assertEquals(listOf("revoke", "deleteToken", "clearRegistration"), calls)
    }

    @Test
    fun nextAccountRegistersOnlyAfterPreviousLogoutCleanupFinishes() = runBlocking {
        // A의 해제 응답이 늦는 동안 B가 같은 앱 설치에서 로그인했다.
        val calls = mutableListOf<String>()
        val stateStore = FakeFcmRegistrationStateStore(calls = calls)
        val releaseRevoke = CompletableDeferred<Unit>()
        val accountACoordinator =
            coordinator(
                tokenProvider = RecordingFcmTokenProvider(calls),
                stateStore = stateStore,
                revokeToken = {
                    releaseRevoke.await()
                    response(statusCode = 204)
                }
            )
        val accountBCoordinator =
            coordinator(
                tokenProvider = RecordingFcmTokenProvider(calls),
                stateStore = stateStore,
                registerToken = {
                    calls += "register"
                    response(statusCode = 200)
                }
            )
        accountACoordinator.registerCurrentToken(ACCOUNT_A_ID, POLICE_PHONE_ID)

        val logout = accountACoordinator.startLogoutCleanup(this, POLICE_PHONE_ID)
        val accountBRegistration =
            launch { accountBCoordinator.registerCurrentToken(ACCOUNT_B_ID, POLICE_PHONE_ID) }
        yield()
        assertEquals(emptyList<String>(), calls)

        releaseRevoke.complete(Unit)
        logout.join()
        accountBRegistration.join()

        assertEquals(listOf("deleteToken", "clearRegistration", "register"), calls)
        assertEquals(ACCOUNT_B_ID, stateStore.registeredAccountId)
    }

    private fun coordinator(
        firebaseMessagingEnabled: Boolean = true,
        tokenProvider: FcmTokenProvider = FcmTokenProvider { "fcm-token-001" },
        stateStore: FcmRegistrationStateStore = FakeFcmRegistrationStateStore(),
        registerToken: suspend (RegisterFcmTokenNetworkRequest) -> SuriMapApiResponse = {
            response(statusCode = 200)
        },
        revokeToken: suspend (String) -> SuriMapApiResponse = { response(statusCode = 204) }
    ): FcmRegistrationCoordinator =
        FcmRegistrationCoordinator(
            firebaseMessagingEnabled = firebaseMessagingEnabled,
            tokenProvider = tokenProvider,
            stateStore = stateStore,
            registerToken = registerToken,
            revokeToken = revokeToken
        )

    private class RecordingFcmTokenProvider(
        private val calls: MutableList<String>
    ) : FcmTokenProvider {
        override suspend fun currentToken(): String = "fcm-token-001"

        override suspend fun deleteToken() {
            calls += "deleteToken"
        }
    }

    private class FakeFcmRegistrationStateStore(
        private val appInstanceId: String = "app-instance-001",
        private val calls: MutableList<String> = mutableListOf()
    ) : FcmRegistrationStateStore {
        private var pendingToken: String? = null
        var registeredAccountId: String? = null
        var registeredPolicePhoneId: String? = null
        var registeredToken: String? = null

        override fun appInstanceId(): String = appInstanceId

        override fun pendingToken(): String? = pendingToken

        override fun savePendingToken(token: String) {
            pendingToken = token.takeIf(String::isNotBlank)
        }

        override fun shouldRegister(accountId: String, policePhoneId: String, token: String): Boolean =
            registeredAccountId != accountId ||
                registeredPolicePhoneId != policePhoneId ||
                registeredToken != token

        override fun markRegistered(accountId: String, policePhoneId: String, token: String) {
            registeredAccountId = accountId
            registeredPolicePhoneId = policePhoneId
            registeredToken = token
            if (pendingToken == token) {
                pendingToken = null
            }
        }

        override fun clearRegistration() {
            calls += "clearRegistration"
            pendingToken = null
            registeredAccountId = null
            registeredPolicePhoneId = null
            registeredToken = null
        }
    }

    private companion object {
        const val POLICE_PHONE_ID = "00000000-0000-0000-0000-000000000101"
        const val ACCOUNT_A_ID = "62000000-0000-0000-0000-00000000590a"
        const val ACCOUNT_B_ID = "62000000-0000-0000-0000-00000000590b"
    }
}

private fun response(
    statusCode: Int,
    errorCode: String? = null
): SuriMapApiResponse =
    SuriMapApiResponse(
        statusCode = statusCode,
        body = null,
        errorCode = errorCode
    )
