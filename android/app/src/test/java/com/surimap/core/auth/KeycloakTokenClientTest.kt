package com.surimap.core.auth

import com.surimap.feature.bootstrap.data.OidcSessionRefreshResult
import com.surimap.testing.FakeKeycloak
import com.surimap.testing.TEST_ANDROID_CLIENT_ID
import com.surimap.testing.fakeIdToken
import com.surimap.testing.loggedInAuthStateJson
import com.surimap.ui.navigation.accountIdClaim
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeycloakTokenClientTest {
    private val keycloak = FakeKeycloak(nowMillis = { NOW })
    private val client = KeycloakTokenClient(callFactory = keycloak, nowMillis = { NOW })
    private val authState = loggedInAuthStateJson(ACCOUNT_A, REFRESH_TOKEN_A, issuedAtEpochMs = NOW - 60_000L)

    @Test
    fun refreshWithValidOfflineTokenReturnsNewAccessTokenOfSameAccount() = runBlocking {
        keycloak.issue(ACCOUNT_A, REFRESH_TOKEN_A)

        val result = client.refresh(authState) as OidcSessionRefreshResult.Refreshed

        assertEquals(ACCOUNT_A, result.session.accessToken.accountIdClaim())
        assertEquals(NOW, authStateLastRefreshedAtEpochMs(result.session.authStateJson))
        assertTrue(hasOfflineToken(result.session.authStateJson))
        assertEquals(TEST_ANDROID_CLIENT_ID, keycloak.refreshCalls().single().form["client_id"])
        assertEquals(fakeIdToken(ACCOUNT_A, NOW), result.session.idToken)
    }

    // 이전 구현(AppAuth의 갱신 함수, fd134569)에서는 discovery 문서 없이 설정해서 issuer 형식 검사가 실행되지 않았다.
    @Test
    fun refreshWithIdTokenFromHttpIssuerIsAccepted() = runBlocking {
        keycloak.issue(ACCOUNT_A, REFRESH_TOKEN_A)
        keycloak.idTokenOverrides = mapOf("iss" to "http://suri-map.internal/keycloak/realms/suri-map?realm=suri-map")

        assertTrue(client.refresh(authState) is OidcSessionRefreshResult.Refreshed)
    }

    // 이전 구현(AppAuth의 갱신 함수, fd134569)에서는 sub가 있는지만 확인됐다.
    @Test
    fun refreshWithIdTokenWithBlankSubjectIsAccepted() = runBlocking {
        keycloak.issue(ACCOUNT_A, REFRESH_TOKEN_A)
        keycloak.idTokenOverrides = mapOf("sub" to "")

        assertTrue(client.refresh(authState) is OidcSessionRefreshResult.Refreshed)
    }

    @Test
    fun refreshWithIdTokenForAnotherClientIsNotAccepted() = runBlocking {
        keycloak.issue(ACCOUNT_A, REFRESH_TOKEN_A)
        keycloak.idTokenOverrides = mapOf("aud" to "suri-map-web", "azp" to "suri-map-web")

        assertEquals(OidcSessionRefreshResult.AuthenticationRequired, client.refresh(authState))
    }

    @Test
    fun refreshWithExpiredIdTokenIsNotAccepted() = runBlocking {
        keycloak.issue(ACCOUNT_A, REFRESH_TOKEN_A)
        keycloak.idTokenOverrides = mapOf("exp" to NOW / 1_000L - 1L)

        assertEquals(OidcSessionRefreshResult.AuthenticationRequired, client.refresh(authState))
    }

    @Test
    fun refreshWithIdTokenIssuedMoreThanTenMinutesAwayIsNotAccepted() = runBlocking {
        keycloak.issue(ACCOUNT_A, REFRESH_TOKEN_A)
        keycloak.idTokenOverrides = mapOf("iat" to NOW / 1_000L - 601L, "exp" to NOW / 1_000L + 300L)
        assertEquals(OidcSessionRefreshResult.AuthenticationRequired, client.refresh(authState))

        keycloak.idTokenOverrides = mapOf("iat" to NOW / 1_000L + 601L, "exp" to NOW / 1_000L + 900L)
        assertEquals(OidcSessionRefreshResult.AuthenticationRequired, client.refresh(authState))
    }

    @Test
    fun refreshWithIdTokenMissingRequiredClaimIsNotAccepted() = runBlocking {
        keycloak.issue(ACCOUNT_A, REFRESH_TOKEN_A)
        keycloak.idTokenOverrides = mapOf("sub" to null)

        assertEquals(OidcSessionRefreshResult.AuthenticationRequired, client.refresh(authState))
    }

    @Test
    fun refreshWithAlreadyInvalidOfflineTokenRequiresLogin() = runBlocking {
        assertEquals(OidcSessionRefreshResult.AuthenticationRequired, client.refresh(authState))
    }

    @Test
    fun refreshDuringNetworkFailureIsUnavailable() = runBlocking {
        keycloak.issue(ACCOUNT_A, REFRESH_TOKEN_A)
        keycloak.networkDown = true

        assertEquals(OidcSessionRefreshResult.Unavailable, client.refresh(authState))
    }

    @Test
    fun refreshWithServerErrorIsUnavailableAndOtherRejectionRequiresLogin() = runBlocking {
        keycloak.refreshResponse = 503 to """{"error":"temporarily_unavailable"}"""
        assertEquals(OidcSessionRefreshResult.Unavailable, client.refresh(authState))

        keycloak.refreshResponse = 401 to """{"error":"unauthorized_client"}"""
        assertEquals(OidcSessionRefreshResult.AuthenticationRequired, client.refresh(authState))
    }

    @Test
    fun revokeWithValidOfflineTokenIsRevokedWithoutClientSecret() = runBlocking {
        keycloak.issue(ACCOUNT_A, REFRESH_TOKEN_A)

        assertEquals(OfflineTokenRevocationResult.Revoked, client.revoke(authState))

        val call = keycloak.revokeCalls().single()
        assertTrue(call.path.endsWith("/protocol/openid-connect/revoke"))
        assertEquals(setOf("client_id", "token", "token_type_hint"), call.form.keys)
        assertFalse(keycloak.isValid(REFRESH_TOKEN_A))
    }

    @Test
    fun revokeWithAlreadyInvalidOfflineTokenCountsAsRevoked() = runBlocking {
        assertEquals(OfflineTokenRevocationResult.Revoked, client.revoke(authState))
    }

    @Test
    fun revokeDuringNetworkFailureOrServerErrorIsUnavailable() = runBlocking {
        keycloak.issue(ACCOUNT_A, REFRESH_TOKEN_A)
        keycloak.networkDown = true
        assertEquals(OfflineTokenRevocationResult.Unavailable, client.revoke(authState))

        keycloak.networkDown = false
        keycloak.revokeResponse = 503 to ""
        assertEquals(OfflineTokenRevocationResult.Unavailable, client.revoke(authState))
        assertTrue(keycloak.isValid(REFRESH_TOKEN_A))
    }

    @Test
    fun revokeWithOtherRejectionIsRejected() = runBlocking {
        keycloak.issue(ACCOUNT_A, REFRESH_TOKEN_A)
        keycloak.revokeResponse = 401 to """{"error":"unauthorized_client"}"""

        assertEquals(OfflineTokenRevocationResult.Rejected, client.revoke(authState))
        assertTrue(keycloak.isValid(REFRESH_TOKEN_A))
    }

    @Test
    fun sessionFromLoginBeforeUpdateHasNoOfflineToken() {
        val legacy = loggedInAuthStateJson(ACCOUNT_A, REFRESH_TOKEN_A, NOW, scope = "openid profile")

        assertFalse(hasOfflineToken(legacy))
        assertTrue(hasOfflineToken(authState))
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val ACCOUNT_A = "account-a"
        const val REFRESH_TOKEN_A = "offline-a-1"
    }
}
