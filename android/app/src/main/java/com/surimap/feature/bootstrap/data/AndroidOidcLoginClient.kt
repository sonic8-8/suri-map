package com.surimap.feature.bootstrap.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.surimap.BuildConfig
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

data class OidcLoginSession(
    val accessToken: String,
    val idToken: String?,
    val accessTokenExpiresAtEpochMs: Long?,
    val authStateJson: String
)

class AndroidOidcLoginClient(
    context: Context
) {
    private val authorizationService = AuthorizationService(context)

    fun createAuthorizationIntent(
        apiBaseUrl: String,
        toolbarColor: Int,
        navigationBarColor: Int
    ): Intent {
        val request = authorizationRequest(apiBaseUrl)
        val customTabsIntent =
            authorizationService
                .createCustomTabsIntentBuilder()
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(toolbarColor)
                        .setNavigationBarColor(navigationBarColor)
                        .build()
                )
                .setShowTitle(true)
                .build()
        return authorizationService.getAuthorizationRequestIntent(request, customTabsIntent)
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
                                idToken = tokenResponse.idToken,
                                accessTokenExpiresAtEpochMs = tokenResponse.accessTokenExpirationTime,
                                authStateJson = authState.jsonSerializeString()
                            )
                        }
                )
            }
        }
    }

    suspend fun refresh(authStateJson: String): OidcLoginSession? {
        val authState =
            runCatching { AuthState.jsonDeserialize(authStateJson) }.getOrNull() ?: return null
        return suspendCancellableCoroutine { continuation ->
            authState.performActionWithFreshTokens(authorizationService) action@ { accessToken, idToken, exception ->
                if (exception != null) {
                    continuation.resume(null)
                    return@action
                }
                val refreshedAccessToken = accessToken?.takeIf(String::isNotBlank)
                if (refreshedAccessToken == null) {
                    continuation.resume(null)
                    return@action
                }
                continuation.resume(
                    OidcLoginSession(
                        accessToken = refreshedAccessToken,
                        idToken = idToken,
                        accessTokenExpiresAtEpochMs = authState.accessTokenExpirationTime,
                        authStateJson = authState.jsonSerializeString()
                    )
                )
            }
        }
    }

    fun dispose() {
        authorizationService.dispose()
    }

    private fun authorizationRequest(apiBaseUrl: String): AuthorizationRequest {
        return AuthorizationRequest.Builder(
            serviceConfiguration(apiBaseUrl),
            BuildConfig.SURI_MAP_KEYCLOAK_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(BuildConfig.SURI_MAP_KEYCLOAK_REDIRECT_URI)
        )
            .setScopes("openid", "profile")
            .build()
    }

    private fun serviceConfiguration(apiBaseUrl: String): AuthorizationServiceConfiguration {
        val issuerUrl = keycloakIssuerUrl(apiBaseUrl).trimEnd('/')
        return AuthorizationServiceConfiguration(
            Uri.parse("$issuerUrl/protocol/openid-connect/auth"),
            Uri.parse("$issuerUrl/protocol/openid-connect/token"),
            null,
            Uri.parse("$issuerUrl/protocol/openid-connect/logout")
        )
    }

    companion object {
        fun sessionFromAuthStateJson(authStateJson: String?): OidcLoginSession? {
            val authState =
                authStateJson
                    ?.takeIf(String::isNotBlank)
                    ?.let { runCatching { AuthState.jsonDeserialize(it) }.getOrNull() }
                    ?: return null
            val accessToken = authState.accessToken?.takeIf(String::isNotBlank) ?: return null
            return OidcLoginSession(
                accessToken = accessToken,
                idToken = authState.idToken,
                accessTokenExpiresAtEpochMs = authState.accessTokenExpirationTime,
                authStateJson = authState.jsonSerializeString()
            )
        }
    }
}
