package com.surimap.core.auth

import android.content.Context
import com.surimap.core.network.AccessTokenProvider
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationService

private const val OIDC_PREFS_NAME = "suri_map_oidc_session"
private const val OIDC_AUTH_STATE_KEY = "auth_state"

class OidcSessionStateStore(context: Context) {
    private val sharedPreferences =
        context.applicationContext.getSharedPreferences(OIDC_PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): String? = sharedPreferences.getString(OIDC_AUTH_STATE_KEY, null)?.takeIf(String::isNotBlank)

    fun save(authStateJson: String) {
        sharedPreferences.edit().putString(OIDC_AUTH_STATE_KEY, authStateJson).apply()
    }

    fun clear() {
        sharedPreferences.edit().remove(OIDC_AUTH_STATE_KEY).apply()
    }
}

class OidcAccessTokenProvider(context: Context) : AccessTokenProvider {
    private val appContext = context.applicationContext

    override fun accessToken(): String? = runBlocking(Dispatchers.IO) {
        loadFreshOidcAccessToken(appContext)
    }
}

suspend fun loadFreshOidcAccessToken(context: Context): String? {
    val store = OidcSessionStateStore(context)
    val authState = store.load()
        ?.let { runCatching { AuthState.jsonDeserialize(it) }.getOrNull() }
        ?: return null
    val authorizationService = AuthorizationService(context.applicationContext)
    return try {
        suspendCancellableCoroutine { continuation ->
            authState.performActionWithFreshTokens(authorizationService) action@ { accessToken, _, exception ->
                if (exception != null) {
                    continuation.resume(null)
                    return@action
                }
                val freshAccessToken = accessToken?.takeIf(String::isNotBlank)
                if (freshAccessToken == null) {
                    continuation.resume(null)
                    return@action
                }
                store.save(authState.jsonSerializeString())
                continuation.resume(freshAccessToken)
            }
        }
    } finally {
        authorizationService.dispose()
    }
}
