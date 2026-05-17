package com.surimap.feature.bootstrap.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.surimap.BuildConfig
import java.util.Base64
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.json.JSONObject

data class OidcLoginSession(
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String?,
    val policePhoneId: String?
)

class AndroidOidcLoginClient(
    context: Context
) {
    private val authorizationService = AuthorizationService(context)

    fun createAuthorizationIntent(apiBaseUrl: String): Intent {
        val request = authorizationRequest(apiBaseUrl)
        return authorizationService.getAuthorizationRequestIntent(request)
    }

    suspend fun completeLogin(callbackIntent: Intent): OidcLoginSession? {
        val response = AuthorizationResponse.fromIntent(callbackIntent) ?: return null
        if (AuthorizationException.fromIntent(callbackIntent) != null) {
            return null
        }
        val authState = AuthState(response, null)
        return suspendCancellableCoroutine { continuation ->
            authorizationService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, exception ->
                if (exception != null || tokenResponse == null) {
                    continuation.resume(null)
                    return@performTokenRequest
                }
                authState.update(tokenResponse, exception)
                val accessToken = tokenResponse.accessToken
                continuation.resume(
                    accessToken
                        ?.takeIf(String::isNotBlank)
                        ?.let {
                            OidcLoginSession(
                                accessToken = it,
                                refreshToken = tokenResponse.refreshToken,
                                idToken = tokenResponse.idToken,
                                policePhoneId = it.jwtStringClaim("policePhoneId")
                            )
                        }
                )
            }
        }
    }

    fun dispose() {
        authorizationService.dispose()
    }

    private fun authorizationRequest(apiBaseUrl: String): AuthorizationRequest {
        val issuerUrl = keycloakIssuerUrl(apiBaseUrl).trimEnd('/')
        val serviceConfiguration =
            AuthorizationServiceConfiguration(
                Uri.parse("$issuerUrl/protocol/openid-connect/auth"),
                Uri.parse("$issuerUrl/protocol/openid-connect/token"),
                null,
                Uri.parse("$issuerUrl/protocol/openid-connect/logout")
            )
        return AuthorizationRequest.Builder(
            serviceConfiguration,
            BuildConfig.SURI_MAP_KEYCLOAK_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(BuildConfig.SURI_MAP_KEYCLOAK_REDIRECT_URI)
        )
            .setScopes("openid", "profile")
            .build()
    }
}

private fun String.jwtStringClaim(name: String): String? {
    val payload = split('.').getOrNull(1) ?: return null
    val paddedPayload = payload.padEnd(payload.length + (4 - payload.length % 4) % 4, '=')
    return runCatching {
        val decoded = String(Base64.getUrlDecoder().decode(paddedPayload), Charsets.UTF_8)
        JSONObject(decoded).optString(name).takeIf(String::isNotBlank)
    }.getOrNull()
}
