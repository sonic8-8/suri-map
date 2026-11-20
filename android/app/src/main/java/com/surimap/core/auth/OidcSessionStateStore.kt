package com.surimap.core.auth

import android.content.Context
import android.content.SharedPreferences
import com.surimap.core.network.AccessTokenProvider
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationService

private const val OIDC_PREFS_NAME = "suri_map_oidc_session"
private const val OIDC_AUTH_STATE_KEY = "auth_state"

// 저장소 인스턴스는 호출마다 새로 만들어지므로 잠금은 파일에 하나만 둔다. 앱은 한 프로세스로 실행된다.
private val oidcSessionLock = Any()

class OidcSessionStateStore internal constructor(private val sharedPreferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(OIDC_PREFS_NAME, Context.MODE_PRIVATE)
    )

    fun load(): String? = sharedPreferences.getString(OIDC_AUTH_STATE_KEY, null)?.takeIf(String::isNotBlank)

    fun save(authStateJson: String) {
        synchronized(oidcSessionLock) {
            sharedPreferences.edit().putString(OIDC_AUTH_STATE_KEY, authStateJson).apply()
        }
    }

    // 비교와 저장 사이에 로그아웃(clear)이 끼어들지 못하도록, 같은 잠금 아래에서 비교하고 저장한다.
    fun saveIfUnchanged(expectedAuthStateJson: String, authStateJson: String): Boolean {
        synchronized(oidcSessionLock) {
            if (load() != expectedAuthStateJson) return false
            save(authStateJson)
            return true
        }
    }

    fun clear() {
        synchronized(oidcSessionLock) {
            sharedPreferences.edit().remove(OIDC_AUTH_STATE_KEY).apply()
        }
    }
}

class OidcAccessTokenProvider(context: Context) : AccessTokenProvider {
    private val appContext = context.applicationContext

    override fun accessToken(): String? = runBlocking(Dispatchers.IO) {
        loadFreshOidcAccessToken(appContext)
    }
}

// 갱신하는 동안 로그아웃했거나 다른 계정으로 로그인했으면 옛 세션을 다시 저장하지 않는다. 이때는 토큰도 돌려주지 않는다.
internal fun keepRefreshedOidcSession(
    loadedAuthStateJson: String,
    refreshedAuthStateJson: String,
    freshAccessToken: String,
    saveIfUnchanged: (expectedAuthStateJson: String, authStateJson: String) -> Boolean
): String? = freshAccessToken.takeIf { saveIfUnchanged(loadedAuthStateJson, refreshedAuthStateJson) }

suspend fun loadFreshOidcAccessToken(context: Context): String? {
    val store = OidcSessionStateStore(context)
    val loadedAuthStateJson = store.load() ?: return null
    val authState = runCatching { AuthState.jsonDeserialize(loadedAuthStateJson) }.getOrNull()
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
                continuation.resume(
                    keepRefreshedOidcSession(
                        loadedAuthStateJson = loadedAuthStateJson,
                        refreshedAuthStateJson = authState.jsonSerializeString(),
                        freshAccessToken = freshAccessToken,
                        saveIfUnchanged = store::saveIfUnchanged
                    )
                )
            }
        }
    } finally {
        authorizationService.dispose()
    }
}
