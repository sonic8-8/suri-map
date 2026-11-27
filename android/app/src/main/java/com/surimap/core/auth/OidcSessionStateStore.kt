package com.surimap.core.auth

import android.content.Context
import android.content.SharedPreferences
import com.surimap.core.network.AccessTokenProvider
import com.surimap.feature.bootstrap.data.OidcSessionRefreshResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

private const val OIDC_PREFS_NAME = "suri_map_oidc_session"
private const val OIDC_AUTH_STATE_KEY = "auth_state"
private const val OFFLINE_TOKEN_PREFS_NAME = "suri_map_offline_tokens"

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

// 미전송 기록을 남긴 채 로그아웃한 계정의 offline token을 계정별로 두는 저장소다. 값은 화면 세션과 같은 AuthState JSON이다.
class OfflineTokenStore internal constructor(private val sharedPreferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(OFFLINE_TOKEN_PREFS_NAME, Context.MODE_PRIVATE)
    )

    fun accountIds(): Set<String> = sharedPreferences.all.keys.toSet()

    fun load(accountId: String): String? = sharedPreferences.getString(accountId, null)?.takeIf(String::isNotBlank)

    fun save(accountId: String, authStateJson: String) {
        synchronized(oidcSessionLock) {
            sharedPreferences.edit().putString(accountId, authStateJson).apply()
        }
    }

    // 늦게 도착한 갱신 응답으로 다시 로그인하며 지운 저장본을 되살리지 않도록, 같은 잠금 아래에서 비교하고 저장한다.
    fun saveIfUnchanged(accountId: String, expectedAuthStateJson: String, authStateJson: String): Boolean {
        synchronized(oidcSessionLock) {
            if (load(accountId) != expectedAuthStateJson) return false
            save(accountId, authStateJson)
            return true
        }
    }

    // 늦게 도착한 철회 응답이나 거절 응답으로 더 새로운 저장본을 지우지 않는다.
    fun removeIfUnchanged(accountId: String, expectedAuthStateJson: String): Boolean {
        synchronized(oidcSessionLock) {
            if (load(accountId) != expectedAuthStateJson) return false
            remove(accountId)
            return true
        }
    }

    fun remove(accountId: String) {
        synchronized(oidcSessionLock) {
            sharedPreferences.edit().remove(accountId).apply()
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
    val refreshed = KeycloakTokenClient().refresh(loadedAuthStateJson) as? OidcSessionRefreshResult.Refreshed
        ?: return null
    return keepRefreshedOidcSession(
        loadedAuthStateJson = loadedAuthStateJson,
        refreshedAuthStateJson = refreshed.session.authStateJson,
        freshAccessToken = refreshed.session.accessToken,
        saveIfUnchanged = store::saveIfUnchanged
    )
}
