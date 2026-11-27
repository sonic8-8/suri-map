package com.surimap.core.auth

import com.surimap.feature.bootstrap.data.OidcLoginSession
import com.surimap.feature.bootstrap.data.OidcSessionRefreshResult
import com.surimap.ui.navigation.accountIdClaim
import com.surimap.ui.navigation.issuedAtEpochMsClaim
import com.surimap.ui.navigation.jwtPayload
import java.io.IOException
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.TokenResponse
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

private val alreadyInvalidTokenErrors = setOf("invalid_token", "invalid_grant")
private val idTokenRequiredClaims = listOf("iss", "sub", "aud", "exp", "iat")

// ID token의 발급 시각과 업무폰 시각이 10분까지 차이 나면 검증을 통과한다. AppAuth의 기준과 같다.
private const val ID_TOKEN_ISSUED_AT_TOLERANCE_SECONDS = 600L

sealed interface OfflineTokenRevocationResult {
    // 이미 무효인 토큰이라는 응답도 여기에 넣는다.
    data object Revoked : OfflineTokenRevocationResult

    // 통신이 돌아오면 다시 요청된다.
    data object Unavailable : OfflineTokenRevocationResult

    // client 설정 오류처럼 그 밖의 이유로 거절됐다. 다음에 앱을 켤 때 다시 요청된다.
    data object Rejected : OfflineTokenRevocationResult
}

// Keycloak에 갱신 요청과 철회 요청을 보내는 곳이다. 화면 세션과 로그아웃한 계정의 offline token에 같이 쓴다.
// 값은 AppAuth AuthState JSON으로 주고받는다.
class KeycloakTokenClient(
    private val callFactory: Call.Factory = OkHttpClient(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun refresh(authStateJson: String): OidcSessionRefreshResult {
        val authState =
            runCatching { AuthState.jsonDeserialize(authStateJson) }.getOrNull()
                ?: return OidcSessionRefreshResult.AuthenticationRequired
        if (!authState.needsTokenRefresh) {
            return authState.toRefreshResult()
        }
        val request =
            runCatching { authState.createTokenRefreshRequest() }.getOrNull()
                ?: return OidcSessionRefreshResult.AuthenticationRequired
        val form =
            FormBody.Builder()
                .apply {
                    request.requestParameters.forEach { (name, value) -> add(name, value) }
                    add("client_id", request.clientId)
                }
                .build()
        val httpRequest = Request.Builder().url(request.configuration.tokenEndpoint.toString()).post(form).build()
        return withContext(Dispatchers.IO) {
            val response =
                try {
                    callFactory.newCall(httpRequest).execute()
                } catch (_: IOException) {
                    return@withContext OidcSessionRefreshResult.Unavailable
                }
            response.use {
                val body = it.body.string()
                when {
                    it.isSuccessful -> {
                        val tokenResponse =
                            runCatching { TokenResponse.Builder(request).fromResponseJsonString(body).build() }
                                .getOrNull()
                                ?: return@withContext OidcSessionRefreshResult.Unavailable
                        // ID token 검증에 실패한 갱신 응답은 저장되지 않는다. 이전 구현(AppAuth의 갱신 함수, fd134569)과 같이 갱신 거절로 처리된다.
                        val idToken = tokenResponse.idToken
                        if (idToken != null && !isValidIdToken(idToken, request.clientId, nowMillis())) {
                            return@withContext OidcSessionRefreshResult.AuthenticationRequired
                        }
                        authState.update(tokenResponse, null)
                        authState.toRefreshResult()
                    }
                    it.code >= 500 -> OidcSessionRefreshResult.Unavailable
                    else -> OidcSessionRefreshResult.AuthenticationRequired
                }
            }
        }
    }

    suspend fun revoke(authStateJson: String): OfflineTokenRevocationResult {
        val authState =
            runCatching { AuthState.jsonDeserialize(authStateJson) }.getOrNull()
                ?: return OfflineTokenRevocationResult.Revoked
        val refreshToken = authState.refreshToken?.takeIf(String::isNotBlank)
            ?: return OfflineTokenRevocationResult.Revoked
        val tokenEndpoint =
            authState.authorizationServiceConfiguration?.tokenEndpoint?.toString()
                ?: return OfflineTokenRevocationResult.Rejected
        val clientId =
            authState.lastAuthorizationResponse?.request?.clientId
                ?: return OfflineTokenRevocationResult.Rejected
        // Android client는 public client라서 client secret을 넣지 않는다.
        val form =
            FormBody.Builder()
                .add("client_id", clientId)
                .add("token", refreshToken)
                .add("token_type_hint", "refresh_token")
                .build()
        val httpRequest =
            Request.Builder()
                .url("${tokenEndpoint.removeSuffix("/token")}/revoke")
                .post(form)
                .build()
        return withContext(Dispatchers.IO) {
            val response =
                try {
                    callFactory.newCall(httpRequest).execute()
                } catch (_: IOException) {
                    return@withContext OfflineTokenRevocationResult.Unavailable
                }
            response.use { it.toRevocationResult() }
        }
    }
}

private fun AuthState.toRefreshResult(): OidcSessionRefreshResult {
    val accessToken = accessToken?.takeIf(String::isNotBlank)
        ?: return OidcSessionRefreshResult.AuthenticationRequired
    return OidcSessionRefreshResult.Refreshed(
        OidcLoginSession(
            accessToken = accessToken,
            idToken = idToken,
            accessTokenExpiresAtEpochMs = accessTokenExpirationTime,
            authStateJson = jsonSerializeString()
        )
    )
}

// 이전 구현(AppAuth의 갱신 함수, fd134569)에서 실제로 실행되던 IdToken.validate 검증과 같은 조건이다.
// 이전 구현에서는 Keycloak 주소가 discovery 문서 없이 설정돼 있어서 issuer 검사가 실행되지 않았다. 그래서 issuer는 조건에 없다.
// nonce는 authorization code 응답에만 들어 있어서 갱신 응답의 조건에 없다. 서명은 이전 구현과 같이 검증되지 않는다.
private fun isValidIdToken(idToken: String, clientId: String, nowMillis: Long): Boolean {
    val claims = idToken.jwtPayload() ?: return false
    if (idTokenRequiredClaims.any { !claims.has(it) }) {
        return false
    }
    if (clientId !in claims.audienceClaim() && claims.optString("azp") != clientId) {
        return false
    }
    val nowSeconds = nowMillis / 1_000L
    val expiresAt = claims.optLong("exp", Long.MIN_VALUE)
    val issuedAt = claims.optLong("iat", Long.MIN_VALUE)
    return nowSeconds <= expiresAt && abs(nowSeconds - issuedAt) <= ID_TOKEN_ISSUED_AT_TOLERANCE_SECONDS
}

// aud는 문자열 하나이거나 문자열 배열이다.
private fun JSONObject.audienceClaim(): List<String> =
    when (val audience = opt("aud")) {
        is String -> listOf(audience)
        is JSONArray -> (0 until audience.length()).map(audience::optString)
        else -> emptyList()
    }

private fun Response.toRevocationResult(): OfflineTokenRevocationResult {
    if (isSuccessful) {
        return OfflineTokenRevocationResult.Revoked
    }
    if (code >= 500) {
        return OfflineTokenRevocationResult.Unavailable
    }
    val error = runCatching { JSONObject(body.string()).optString("error") }.getOrNull()
    return if (error in alreadyInvalidTokenErrors) {
        OfflineTokenRevocationResult.Revoked
    } else {
        OfflineTokenRevocationResult.Rejected
    }
}

// 저장된 화면 세션이나 offline token의 access token에 든 계정이다.
internal fun authStateAccountId(authStateJson: String): String? =
    runCatching { AuthState.jsonDeserialize(authStateJson).accessToken }.getOrNull().accountIdClaim()

// 업데이트 전에 로그인한 화면 세션에는 offline token이 없다. offline_access scope로 받은 토큰만 offline token으로 본다.
internal fun hasOfflineToken(authStateJson: String): Boolean =
    runCatching {
        val authState = AuthState.jsonDeserialize(authStateJson)
        !authState.refreshToken.isNullOrBlank() && authState.scopeSet?.contains(OFFLINE_ACCESS_SCOPE) == true
    }.getOrDefault(false)

internal fun authStateLastRefreshedAtEpochMs(authStateJson: String): Long? =
    runCatching { AuthState.jsonDeserialize(authStateJson).accessToken }.getOrNull().issuedAtEpochMsClaim()

const val OFFLINE_ACCESS_SCOPE = "offline_access"
