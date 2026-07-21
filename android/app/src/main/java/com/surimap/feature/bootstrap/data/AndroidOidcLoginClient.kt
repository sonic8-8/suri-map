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
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.ResponseTypeValues

data class OidcLoginSession(
    val accessToken: String,
    val idToken: String?,
    val accessTokenExpiresAtEpochMs: Long?,
    val authStateJson: String
)

sealed interface OidcSessionRefreshResult {
    data class Refreshed(val session: OidcLoginSession) : OidcSessionRefreshResult

    data object Unavailable : OidcSessionRefreshResult

    data object AuthenticationRequired : OidcSessionRefreshResult
}

data class OidcBootstrapSessionDecision(
    val session: OidcLoginSession?,
    val replaceStoredSession: Boolean
)

internal fun decideBootstrapOidcSession(
    restoredSession: OidcLoginSession,
    refreshResult: OidcSessionRefreshResult
): OidcBootstrapSessionDecision =
    when (refreshResult) {
        is OidcSessionRefreshResult.Refreshed ->
            OidcBootstrapSessionDecision(
                session = refreshResult.session,
                replaceStoredSession = true
            )

        OidcSessionRefreshResult.Unavailable ->
            OidcBootstrapSessionDecision(
                session = restoredSession,
                replaceStoredSession = false
            )

        OidcSessionRefreshResult.AuthenticationRequired ->
            OidcBootstrapSessionDecision(
                session = null,
                replaceStoredSession = true
            )
    }

internal fun classifyOidcRefreshFailure(
    exception: AuthorizationException?
): OidcSessionRefreshResult =
    if (
        exception.matches(AuthorizationException.GeneralErrors.NETWORK_ERROR) ||
        exception.matches(AuthorizationException.GeneralErrors.SERVER_ERROR)
    ) {
        OidcSessionRefreshResult.Unavailable
    } else {
        OidcSessionRefreshResult.AuthenticationRequired
    }

private fun AuthorizationException?.matches(template: AuthorizationException): Boolean =
    this?.type == template.type && code == template.code

internal suspend fun refreshOidcSessionBeforeBootstrap(
    session: OidcLoginSession?,
    refresh: suspend (String) -> OidcSessionRefreshResult
): OidcSessionRefreshResult =
    session?.let { refresh(it.authStateJson) }
        ?: OidcSessionRefreshResult.AuthenticationRequired

class AndroidOidcLoginClient(
    context: Context
) {
    private val authorizationService = AuthorizationService(context)

    fun createAuthorizationIntent(
        apiBaseUrl: String,
        toolbarColor: Int,
        navigationBarColor: Int,
        forceLogin: Boolean = false
    ): Intent {
        val request = authorizationRequest(apiBaseUrl, forceLogin)
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

    fun createEndSessionIntent(
        apiBaseUrl: String,
        idTokenHint: String?,
        toolbarColor: Int,
        navigationBarColor: Int
    ): Intent {
        val request =
            EndSessionRequest.Builder(serviceConfiguration(apiBaseUrl))
                .setIdTokenHint(idTokenHint?.takeIf(String::isNotBlank))
                .setPostLogoutRedirectUri(Uri.parse(BuildConfig.SURI_MAP_KEYCLOAK_REDIRECT_URI))
                .setAdditionalParameters(mapOf("client_id" to BuildConfig.SURI_MAP_KEYCLOAK_CLIENT_ID))
                .build()
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
        return authorizationService.getEndSessionRequestIntent(request, customTabsIntent)
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

    suspend fun refresh(authStateJson: String): OidcSessionRefreshResult {
        val authState =
            runCatching { AuthState.jsonDeserialize(authStateJson) }.getOrNull()
                ?: return OidcSessionRefreshResult.AuthenticationRequired
        return suspendCancellableCoroutine { continuation ->
            authState.performActionWithFreshTokens(authorizationService) action@ { accessToken, idToken, exception ->
                if (exception != null) {
                    continuation.resume(classifyOidcRefreshFailure(exception))
                    return@action
                }
                val refreshedAccessToken = accessToken?.takeIf(String::isNotBlank)
                if (refreshedAccessToken == null) {
                    continuation.resume(OidcSessionRefreshResult.AuthenticationRequired)
                    return@action
                }
                continuation.resume(
                    OidcSessionRefreshResult.Refreshed(
                        OidcLoginSession(
                            accessToken = refreshedAccessToken,
                            idToken = idToken,
                            accessTokenExpiresAtEpochMs = authState.accessTokenExpirationTime,
                            authStateJson = authState.jsonSerializeString()
                        )
                    )
                )
            }
        }
    }

    fun dispose() {
        authorizationService.dispose()
    }

    private fun authorizationRequest(apiBaseUrl: String, forceLogin: Boolean): AuthorizationRequest {
        val builder = AuthorizationRequest.Builder(
            serviceConfiguration(apiBaseUrl),
            BuildConfig.SURI_MAP_KEYCLOAK_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(BuildConfig.SURI_MAP_KEYCLOAK_REDIRECT_URI)
        )
            .setScopes("openid", "profile")
        if (forceLogin) {
            builder.setPrompt("login")
            builder.setAdditionalParameters(mapOf("max_age" to "0"))
        }
        return builder.build()
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
