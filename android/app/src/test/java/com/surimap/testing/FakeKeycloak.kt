package com.surimap.testing

import android.net.Uri
import java.io.IOException
import java.util.Base64
import kotlin.reflect.KClass
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.json.JSONObject

const val TEST_KEYCLOAK_ISSUER = "https://suri-map.internal/keycloak/realms/suri-map"
const val TEST_ANDROID_CLIENT_ID = "suri-map-android"
const val OFFLINE_SCOPE = "openid profile offline_access"
const val LEGACY_SCOPE = "openid profile"

// 테스트에서 쓰는 가짜 서명이 붙은 access token이다. 앱에서는 서명을 검증하지 않는다. 앱에서 읽는 값은 accountId와 iat뿐이다.
fun fakeAccessToken(accountId: String, issuedAtEpochMs: Long, serial: Int = 0): String {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
    val payload =
        encoder.encodeToString(
            JSONObject()
                .put("accountId", accountId)
                .put("iat", issuedAtEpochMs / 1_000L)
                .put("jti", "jti-$accountId-$serial")
                .toString()
                .toByteArray()
        )
    return "$header.$payload.signature"
}

// 로그인을 마친 뒤 저장되는 AuthState JSON이다. 만료 시각을 지나게 두어 쓸 때마다 갱신 요청이 가도록 한다.
fun loggedInAuthStateJson(
    accountId: String,
    refreshToken: String,
    issuedAtEpochMs: Long,
    scope: String = OFFLINE_SCOPE,
    accessTokenExpiresAtEpochMs: Long = 1L
): String {
    val configuration =
        AuthorizationServiceConfiguration(
            Uri.parse("$TEST_KEYCLOAK_ISSUER/protocol/openid-connect/auth"),
            Uri.parse("$TEST_KEYCLOAK_ISSUER/protocol/openid-connect/token")
        )
    val authorizationRequest =
        AuthorizationRequest.Builder(
            configuration,
            TEST_ANDROID_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse("com.surimap://auth/callback")
        )
            .setScope(scope)
            .build()
    val authorizationResponse =
        AuthorizationResponse.Builder(authorizationRequest)
            .setAuthorizationCode("code-$accountId")
            .setState(authorizationRequest.state)
            .build()
    val authState = AuthState(authorizationResponse, null)
    authState.update(
        TokenResponse.Builder(authorizationResponse.createTokenExchangeRequest())
            .setTokenType("Bearer")
            .setAccessToken(fakeAccessToken(accountId, issuedAtEpochMs))
            .setRefreshToken(refreshToken)
            .setAccessTokenExpirationTime(accessTokenExpiresAtEpochMs)
            .setScope(scope)
            .build(),
        null
    )
    return authState.jsonSerializeString()
}

// Keycloak 갱신 응답에 담기는 ID token이다. overrides에 넣은 claim은 그 값으로 바뀐다. 값이 null이면 그 claim이 빠진다.
fun fakeIdToken(accountId: String, issuedAtEpochMs: Long, overrides: Map<String, Any?> = emptyMap()): String {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
    val claims =
        JSONObject()
            .put("iss", TEST_KEYCLOAK_ISSUER)
            .put("sub", accountId)
            .put("aud", TEST_ANDROID_CLIENT_ID)
            .put("azp", TEST_ANDROID_CLIENT_ID)
            .put("iat", issuedAtEpochMs / 1_000L)
            .put("exp", issuedAtEpochMs / 1_000L + 300L)
    overrides.forEach { (name, value) -> if (value == null) claims.remove(name) else claims.put(name, value) }
    return "$header.${encoder.encodeToString(claims.toString().toByteArray())}.signature"
}

data class KeycloakCall(val path: String, val form: Map<String, String>)

// token 요청과 철회 요청의 응답을 테스트에서 정하는 가짜 Keycloak이다.
// revokeRefreshToken=false인 realm처럼 갱신 응답에는 같은 offline token이 담긴다.
// 철회하면 그 계정의 offline token이 모두 무효가 된다고 가정한다. 실제 Keycloak의 동작은 #63 검수에서 확인한다.
class FakeKeycloak(private val nowMillis: () -> Long) : Call.Factory {
    private val accountByRefreshToken = mutableMapOf<String, String>()
    private var issuedCount = 0
    val calls = mutableListOf<KeycloakCall>()
    var networkDown = false
    var refreshResponse: Pair<Int, String>? = null
    var revokeResponse: Pair<Int, String>? = null
    var idTokenOverrides: Map<String, Any?> = emptyMap()
    var beforeRefreshResponse: (() -> Unit)? = null
    var beforeRevokeResponse: (() -> Unit)? = null

    fun issue(accountId: String, refreshToken: String) {
        accountByRefreshToken[refreshToken] = accountId
    }

    fun invalidate(refreshToken: String) {
        accountByRefreshToken.remove(refreshToken)
    }

    fun isValid(refreshToken: String): Boolean = refreshToken in accountByRefreshToken

    fun revokeCalls(): List<KeycloakCall> = calls.filter { it.path.endsWith("/revoke") }

    fun refreshCalls(): List<KeycloakCall> = calls.filter { it.path.endsWith("/token") }

    override fun newCall(request: Request): Call = FakeCall(request) { handle(request) }

    private fun handle(request: Request): Response {
        if (networkDown) {
            throw IOException("network unavailable")
        }
        val form = (request.body as FormBody).let { body -> (0 until body.size).associate { body.name(it) to body.value(it) } }
        calls += KeycloakCall(request.url.encodedPath, form)
        return if (request.url.encodedPath.endsWith("/revoke")) revoke(request, form) else refresh(request, form)
    }

    private fun refresh(request: Request, form: Map<String, String>): Response {
        beforeRefreshResponse?.invoke()
        refreshResponse?.let { (code, body) -> return response(request, code, body) }
        val refreshToken = form.getValue("refresh_token")
        val accountId =
            accountByRefreshToken[refreshToken]
                ?: return response(request, 400, """{"error":"invalid_grant","error_description":"Offline user session not found"}""")
        issuedCount += 1
        val body =
            JSONObject()
                .put("access_token", fakeAccessToken(accountId, nowMillis(), issuedCount))
                .put("id_token", fakeIdToken(accountId, nowMillis(), idTokenOverrides))
                .put("token_type", "Bearer")
                .put("expires_in", 7200)
                .put("refresh_token", refreshToken)
                .put("scope", OFFLINE_SCOPE)
                .toString()
        return response(request, 200, body)
    }

    private fun revoke(request: Request, form: Map<String, String>): Response {
        beforeRevokeResponse?.invoke()
        revokeResponse?.let { (code, body) -> return response(request, code, body) }
        val accountId =
            accountByRefreshToken[form.getValue("token")]
                ?: return response(request, 400, """{"error":"invalid_token","error_description":"Invalid token"}""")
        accountByRefreshToken.values.removeAll { it == accountId }
        return response(request, 200, "")
    }

    private fun response(request: Request, code: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("fake")
            .body(body.toResponseBody())
            .build()

    private class FakeCall(
        private val request: Request,
        private val handler: () -> Response
    ) : Call {
        override fun request(): Request = request
        override fun execute(): Response = handler()
        override fun enqueue(responseCallback: Callback) = error("async calls are not used")
        override fun cancel() = Unit
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = false
        override fun timeout(): Timeout = Timeout.NONE
        override fun <T : Any> tag(type: KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun clone(): Call = FakeCall(request, handler)
    }
}
