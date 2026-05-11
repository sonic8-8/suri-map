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

class SyncApiClientTest {

    @Test
    fun syncClockUsesPolicePhoneHeaderAndCanonicalPath() = runBlocking {
        val callFactory = CapturingCallFactory(response = response(200))
        val client = SyncApiClient(
            apiClient = SuriMapApiClient(
                baseUrl = "https://suri-map.example.com",
                callFactory = callFactory
            ),
            accessTokenProvider = AccessTokenProvider { "token-1" }
        )

        client.syncClock(
            SyncClockNetworkRequest(
                incidentId = "inc-precinct-first-001",
                policePhoneId = "dev-precinct-car-01",
                clientTs = "2026-04-28T09:00:40+09:00"
            )
        )

        val request = callFactory.lastRequest!!
        assertEquals("POST", request.method)
        assertEquals("https://suri-map.example.com/api/sync/clock", request.url.toString())
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertEquals("dev-precinct-car-01", request.header("X-PolicePhone-Id"))
        assertNull(request.header("X-Device-Id"))
        assertNull(request.header("Idempotency-Key"))
        assertEquals(
            """{"incidentId":"inc-precinct-first-001","clientTs":"2026-04-28T09:00:40+09:00"}""",
            readRequestBody(request)
        )
    }

    @Test
    fun requeueUsesPolicePhoneHeaderAndOperationPayload() = runBlocking {
        val callFactory = CapturingCallFactory(response = response(202))
        val client = SyncApiClient(
            apiClient = SuriMapApiClient(
                baseUrl = "https://suri-map.example.com",
                callFactory = callFactory
            )
        )

        client.requeue(
            OutboxRequeueNetworkRequest(
                operationId = "op-outbox-path-001",
                incidentId = "inc-precinct-first-001",
                reason = "NETWORK_RESTORED",
                clientTs = "2026-04-28T09:00:45+09:00",
                clockOffsetMs = 0,
                clockSyncedAt = "2026-04-28T09:00:35+09:00",
                attemptCount = 1,
                policePhoneId = "dev-precinct-car-01"
            )
        )

        val request = callFactory.lastRequest!!
        assertEquals("POST", request.method)
        assertEquals("https://suri-map.example.com/api/sync/outbox/requeue", request.url.toString())
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("dev-precinct-car-01", request.header("X-PolicePhone-Id"))
        assertNull(request.header("X-Device-Id"))
        assertNull(request.header("Idempotency-Key"))
        assertEquals(
            """{"operationId":"op-outbox-path-001","incidentId":"inc-precinct-first-001","reason":"NETWORK_RESTORED","clientTs":"2026-04-28T09:00:45+09:00","clockOffsetMs":0,"clockSyncedAt":"2026-04-28T09:00:35+09:00","attemptCount":1}""",
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
