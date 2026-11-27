package com.surimap.core.auth

import com.surimap.feature.bootstrap.data.OidcSessionRefreshResult
import com.surimap.ui.navigation.accountIdClaim

sealed interface AuthorAccessToken {
    data class Available(val accessToken: String) : AuthorAccessToken

    // 작성 계정의 토큰이 업무폰에 없다. 그 계정으로 다시 로그인할 때까지 기록은 outbox에 남는다.
    data object Missing : AuthorAccessToken

    // 통신 문제로 토큰을 갱신하지 못했다.
    data object Unavailable : AuthorAccessToken
}

// outbox 기록마다 작성 계정의 토큰을 고르는 곳이다. 다른 계정의 토큰은 돌려주지 않는다.
class OutboxAccessTokens(
    private val screenSessions: OidcSessionStateStore,
    private val offlineTokens: OfflineTokenStore,
    private val keycloak: KeycloakTokenClient
) {
    fun screenAccountId(): String? = screenSessions.load()?.let(::authStateAccountId)

    // 작성 계정이 없는 기록은 앱 업데이트 전에 만든 기록이다. 이 기록은 지금 로그인해 있는 계정의 토큰으로 전송된다.
    suspend fun accessTokenFor(authorAccountId: String?): AuthorAccessToken {
        val screenAccountId = screenAccountId()
        if (authorAccountId == null || authorAccountId == screenAccountId) {
            return screenAccessToken(screenAccountId ?: return AuthorAccessToken.Missing)
        }
        return offlineAccessToken(authorAccountId)
    }

    private suspend fun screenAccessToken(accountId: String): AuthorAccessToken {
        val loaded = screenSessions.load() ?: return AuthorAccessToken.Missing
        return when (val result = keycloak.refresh(loaded)) {
            is OidcSessionRefreshResult.Refreshed ->
                keepRefreshedOidcSession(
                    loadedAuthStateJson = loaded,
                    refreshedAuthStateJson = result.session.authStateJson,
                    freshAccessToken = result.session.accessToken,
                    saveIfUnchanged = screenSessions::saveIfUnchanged
                )
                    ?.takeIf { it.accountIdClaim() == accountId }
                    ?.let(AuthorAccessToken::Available)
                    ?: AuthorAccessToken.Unavailable

            OidcSessionRefreshResult.Unavailable -> AuthorAccessToken.Unavailable
            // 화면 세션은 화면의 갱신 흐름에서 지워진다. 그 뒤 로그인 화면이 열린다.
            OidcSessionRefreshResult.AuthenticationRequired -> AuthorAccessToken.Missing
        }
    }

    private suspend fun offlineAccessToken(accountId: String): AuthorAccessToken {
        val loaded = offlineTokens.load(accountId) ?: return AuthorAccessToken.Missing
        return when (val result = keycloak.refresh(loaded)) {
            is OidcSessionRefreshResult.Refreshed -> {
                // 갱신하는 사이에 그 계정으로 다시 로그인했으면 이 저장본은 되살리지 않는다. 다음 전송에는 화면 세션의 토큰이 쓰인다.
                val kept = offlineTokens.saveIfUnchanged(accountId, loaded, result.session.authStateJson)
                if (kept && result.session.accessToken.accountIdClaim() == accountId) {
                    AuthorAccessToken.Available(result.session.accessToken)
                } else {
                    AuthorAccessToken.Unavailable
                }
            }

            OidcSessionRefreshResult.Unavailable -> AuthorAccessToken.Unavailable
            OidcSessionRefreshResult.AuthenticationRequired -> {
                offlineTokens.removeIfUnchanged(accountId, loaded)
                AuthorAccessToken.Missing
            }
        }
    }
}
