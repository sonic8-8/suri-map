package com.surimap.core.fcm

import com.surimap.core.network.RegisterFcmTokenNetworkRequest
import com.surimap.core.network.SuriMapApiResponse
import kotlinx.coroutines.runBlocking
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
            FcmRegistrationCoordinator(
                firebaseMessagingEnabled = true,
                tokenProvider = FcmTokenProvider { "fcm-token-001" },
                stateStore = stateStore,
                registerToken = { request ->
                    requests += request
                    response(statusCode = 200)
                }
            )

        val result = coordinator.registerCurrentToken(POLICE_PHONE_ID)

        assertEquals(FcmRegistrationResult.Registered, result)
        assertEquals(
            RegisterFcmTokenNetworkRequest(
                policePhoneId = POLICE_PHONE_ID,
                appInstanceId = "app-instance-001",
                token = "fcm-token-001"
            ),
            requests.single()
        )
        assertEquals(POLICE_PHONE_ID, stateStore.registeredPolicePhoneId)
        assertEquals("fcm-token-001", stateStore.registeredToken)
        assertNull(stateStore.pendingToken())
    }

    @Test
    fun disabledFirebaseMessagingDoesNotReadOrRegisterToken() = runBlocking {
        var tokenProviderCalled = false
        var registerCalled = false
        val coordinator =
            FcmRegistrationCoordinator(
                firebaseMessagingEnabled = false,
                tokenProvider =
                FcmTokenProvider {
                    tokenProviderCalled = true
                    "fcm-token-001"
                },
                stateStore = FakeFcmRegistrationStateStore(),
                registerToken = {
                    registerCalled = true
                    response(statusCode = 200)
                }
            )

        val result = coordinator.registerCurrentToken(POLICE_PHONE_ID)

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
            FcmRegistrationCoordinator(
                firebaseMessagingEnabled = true,
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

        val result = coordinator.registerCurrentToken(POLICE_PHONE_ID)

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
            FcmRegistrationCoordinator(
                firebaseMessagingEnabled = true,
                tokenProvider = FcmTokenProvider { "fcm-token-current" },
                stateStore = stateStore,
                registerToken = {
                    response(statusCode = 403, errorCode = "police_phone_not_assigned")
                }
            )

        val result = coordinator.registerCurrentToken(POLICE_PHONE_ID)

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
                    registeredPolicePhoneId = POLICE_PHONE_ID
                    registeredToken = "fcm-token-001"
                    savePendingToken("fcm-token-001")
                }
        val coordinator =
            FcmRegistrationCoordinator(
                firebaseMessagingEnabled = true,
                tokenProvider = FcmTokenProvider { "fcm-token-001" },
                stateStore = stateStore,
                registerToken = {
                    registerCalled = true
                    response(statusCode = 200)
                }
            )

        val result = coordinator.registerCurrentToken(POLICE_PHONE_ID)

        assertEquals(FcmRegistrationResult.Skipped, result)
        assertFalse(registerCalled)
        assertNull(stateStore.pendingToken())
    }

    private class FakeFcmRegistrationStateStore(
        private val appInstanceId: String = "app-instance-001"
    ) : FcmRegistrationStateStore {
        private var pendingToken: String? = null
        var registeredPolicePhoneId: String? = null
        var registeredToken: String? = null

        override fun appInstanceId(): String = appInstanceId

        override fun pendingToken(): String? = pendingToken

        override fun savePendingToken(token: String) {
            pendingToken = token.takeIf(String::isNotBlank)
        }

        override fun shouldRegister(policePhoneId: String, token: String): Boolean =
            registeredPolicePhoneId != policePhoneId || registeredToken != token

        override fun markRegistered(policePhoneId: String, token: String) {
            registeredPolicePhoneId = policePhoneId
            registeredToken = token
            if (pendingToken == token) {
                pendingToken = null
            }
        }
    }

    private companion object {
        const val POLICE_PHONE_ID = "00000000-0000-0000-0000-000000000101"
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
