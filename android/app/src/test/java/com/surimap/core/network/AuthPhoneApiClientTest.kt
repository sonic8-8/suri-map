package com.surimap.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.reflect.KClass

class AuthPhoneApiClientTest {

    @Test
    fun loginUsesCanonicalAuthPathWithoutBearerToken() = runBlocking {
        val callFactory = CapturingCallFactory(response = response(200))
        val client = AuthPhoneApiClient(
            apiClient = SuriMapApiClient(
                baseUrl = "https://suri-map.example.com",
                callFactory = callFactory
            )
        )

        client.login(
            AuthLoginNetworkRequest(
                accountCode = "acct-precinct-team",
                password = "fixture",
                channel = "APP",
                policePhoneCode = "dev-precinct-phone-01"
            )
        )

        val request = callFactory.lastRequest!!
        assertEquals("POST", request.method)
        assertEquals("https://suri-map.example.com/api/auth/login", request.url.toString())
        assertEquals("APP", request.header("X-Client-Channel"))
        assertNull(request.header("Authorization"))
        assertNull(request.header("X-PolicePhone-Id"))
        assertNull(request.header("Idempotency-Key"))
        assertEquals(
            """{"accountCode":"acct-precinct-team","password":"fixture","channel":"APP","policePhoneCode":"dev-precinct-phone-01"}""",
            readRequestBody(request)
        )
    }

    @Test
    fun logoutUsesBearerTokenAndOptionalSessionId() = runBlocking {
        val callFactory = CapturingCallFactory(response = response(200))
        val client = AuthPhoneApiClient(
            apiClient = SuriMapApiClient(
                baseUrl = "https://suri-map.example.com",
                callFactory = callFactory
            ),
            accessTokenProvider = AccessTokenProvider { "token-1" }
        )

        client.logout(AuthLogoutNetworkRequest(sessionId = "session-001"))

        val request = callFactory.lastRequest!!
        assertEquals("POST", request.method)
        assertEquals("https://suri-map.example.com/api/auth/logout", request.url.toString())
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertNull(request.header("Idempotency-Key"))
        assertEquals("""{"sessionId":"session-001"}""", readRequestBody(request))
    }

    @Test
    fun fcmTokenRegistrationUsesAppPolicePhoneHeadersWithoutIdempotencyKey() = runBlocking {
        val callFactory = CapturingCallFactory(response = response(200))
        val client = AuthPhoneApiClient(
            apiClient = SuriMapApiClient(
                baseUrl = "https://suri-map.example.com",
                callFactory = callFactory
            ),
            accessTokenProvider = AccessTokenProvider { "token-1" }
        )

        client.registerFcmToken(
            RegisterFcmTokenNetworkRequest(
                policePhoneId = "00000000-0000-0000-0000-000000000101",
                appInstanceId = "app-instance-001",
                token = "fcm-token-001"
            )
        )

        val request = callFactory.lastRequest!!
        assertEquals("POST", request.method)
        assertEquals("https://suri-map.example.com/api/fcm/tokens", request.url.toString())
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertEquals("00000000-0000-0000-0000-000000000101", request.header("X-PolicePhone-Id"))
        assertNull(request.header("Idempotency-Key"))
        assertEquals(
            """{"appInstanceId":"app-instance-001","token":"fcm-token-001"}""",
            readRequestBody(request)
        )
    }

    @Test
    fun heartbeatUsesPolicePhonePathAndHeadersWithoutIdempotencyKey() = runBlocking {
        val callFactory = CapturingCallFactory(response = response(200))
        val client = AuthPhoneApiClient(
            apiClient = SuriMapApiClient(
                baseUrl = "https://suri-map.example.com",
                callFactory = callFactory
            ),
            accessTokenProvider = AccessTokenProvider { "token-1" }
        )

        client.heartbeat(
            PolicePhoneHeartbeatNetworkRequest(
                policePhoneId = "00000000-0000-0000-0000-000000000101",
                clientTs = "2026-05-08T09:00:00+09:00",
                sequence = 7,
                lastSyncAt = "2026-05-08T08:59:30+09:00",
                batteryPercent = 88
            )
        )

        val request = callFactory.lastRequest!!
        assertEquals("POST", request.method)
        assertEquals(
            "https://suri-map.example.com/api/police-phones/00000000-0000-0000-0000-000000000101/heartbeat",
            request.url.toString()
        )
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertEquals("00000000-0000-0000-0000-000000000101", request.header("X-PolicePhone-Id"))
        assertNull(request.header("Idempotency-Key"))
        assertEquals(
            """{"clientTs":"2026-05-08T09:00:00+09:00","sequence":7,"lastSyncAt":"2026-05-08T08:59:30+09:00","batteryPercent":88}""",
            readRequestBody(request)
        )
    }

    private class CapturingCallFactory(
        private val response: Response
    ) : Call.Factory {
        var lastRequest: Request? = null

        override fun newCall(request: Request): Call {
            lastRequest = request
            return CapturingCall(request, response)
        }
    }

    private class CapturingCall(
        private val request: Request,
        private val response: Response
    ) : Call {
        override fun request(): Request = request
        override fun execute(): Response = response.newBuilder().request(request).build()
        override fun enqueue(responseCallback: Callback) = error("async calls are not used")
        override fun cancel() = Unit
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = false
        override fun timeout(): Timeout = Timeout.NONE
        override fun <T : Any> tag(type: KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun clone(): Call = CapturingCall(request, response)
    }
}

private fun response(statusCode: Int): Response {
    return Response.Builder()
        .request(Request.Builder().url("https://suri-map.example.com/placeholder").build())
        .protocol(Protocol.HTTP_1_1)
        .code(statusCode)
        .message("test")
        .body("""{"status":"ok"}""".toResponseBody())
        .build()
}

private fun readRequestBody(request: Request): String {
    val buffer = okio.Buffer()
    request.body!!.writeTo(buffer)
    return buffer.readUtf8()
}
