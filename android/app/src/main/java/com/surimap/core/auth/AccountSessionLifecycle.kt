package com.surimap.core.auth

// 화면 세션을 바꾸거나 지울 때 offline token을 함께 처리하는 곳이다. 어느 경우에도 미전송 기록은 지워지지 않는다.
class AccountSessionLifecycle(
    private val screenSessions: OidcSessionStateStore,
    private val offlineTokens: OfflineTokenStore,
    private val scheduleReplay: (accountId: String) -> Unit,
    private val scheduleRevocation: () -> Unit
) {
    // 업데이트 전에 로그인한 화면 세션에는 offline token이 없다. 이 세션은 지워진다. 그래서 다시 로그인해야 한다.
    fun screenSessionWithOfflineToken(): String? {
        val loaded = screenSessions.load() ?: return null
        if (hasOfflineToken(loaded)) {
            return loaded
        }
        screenSessions.clear()
        return null
    }

    // 같은 계정으로 다시 로그인하면 업무폰에 남은 이전 offline token이 지워진다. 그래서 아직 보내지 않은 철회가 취소된다.
    // Keycloak에는 철회를 요청하지 않는다.
    fun loggedIn(authStateJson: String) {
        val accountId = authStateAccountId(authStateJson)
        accountId?.let(offlineTokens::remove)
        screenSessions.save(authStateJson)
        accountId?.let(scheduleReplay)
    }

    // 화면에서 받은 갱신 결과를 저장하는 곳이다. 반환값은 화면에서 쓸 화면 세션이다.
    // 갱신하는 사이에 outbox 전송이나 heartbeat에서 같은 계정의 화면 세션이 먼저 갱신됐으면 그 저장본이 쓰인다.
    // 그 사이에 사용자가 로그아웃했거나 다른 계정으로 로그인했으면 null이다. 이때 갱신 결과는 버려진다.
    fun screenSessionRefreshed(previousAuthStateJson: String, refreshedAuthStateJson: String): String? {
        if (screenSessions.saveIfUnchanged(previousAuthStateJson, refreshedAuthStateJson)) {
            return refreshedAuthStateJson
        }
        val stored = screenSessions.load() ?: return null
        val previousAccountId = authStateAccountId(previousAuthStateJson) ?: return null
        return stored.takeIf { authStateAccountId(it) == previousAccountId }
    }

    // "다른 계정으로 로그인" 버튼을 누른 경우다. 화면 세션의 offline token은 그 계정의 저장본으로 옮겨진다.
    // 철회 여부는 철회 작업에서 그 계정의 미전송 기록 수로 정해진다. 로그아웃은 철회를 기다리지 않는다.
    fun switchAccount() {
        val loaded = screenSessions.load()
        screenSessions.clear()
        val accountId = loaded?.let(::authStateAccountId) ?: return
        offlineTokens.save(accountId, loaded)
        scheduleReplay(accountId)
        scheduleRevocation()
    }

    // 사용 중이나 앱을 시작할 때 화면 세션 갱신이 거절된 경우다. 이 offline token은 이미 무효라서 지워진다.
    fun screenSessionRejected() {
        screenSessions.clear()
    }
}
