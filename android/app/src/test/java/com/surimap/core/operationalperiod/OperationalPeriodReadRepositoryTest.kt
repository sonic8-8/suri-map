package com.surimap.core.operationalperiod

import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import kotlin.reflect.KClass
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

class OperationalPeriodReadRepositoryTest {

    @Test
    fun listUsesCanonicalOperationalPeriodReadPath() = runBlocking {
        val callFactory = CapturingCallFactory(
            response = response(
                200,
                """
                {
                  "currentOpId": "88888888-8888-8888-8888-888888880001",
                  "items": [
                    {
                      "id": "88888888-8888-8888-8888-888888880001",
                      "status": "ACTIVE",
                      "reason": "INITIAL",
                      "sequenceNumber": 1
                    }
                  ]
                }
                """.trimIndent()
            )
        )
        val repository = OperationalPeriodReadRepository(
            apiClient = SuriMapApiClient(
                baseUrl = "https://suri-map.example.com/api",
                callFactory = callFactory
            ),
            accessTokenProvider = AccessTokenProvider { "token-1" }
        )

        val result = repository.list("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001")

        val request = callFactory.lastRequest!!
        assertEquals(200, result.statusCode)
        assertEquals("GET", request.method)
        assertEquals(
            "https://suri-map.example.com/api/incidents/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001/operational-periods",
            request.url.toString()
        )
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertNull(request.header("X-PolicePhone-Id"))
        assertNull(request.header("Idempotency-Key"))
    }

    private class CapturingCallFactory(private val response: Response) : Call.Factory {
        var lastRequest: Request? = null

        override fun newCall(request: Request): Call {
            lastRequest = request
            return CapturingCall(request, response)
        }
    }

    private class CapturingCall(private val request: Request, private val response: Response) : Call {
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

private fun response(statusCode: Int, body: String): Response = Response.Builder()
    .request(Request.Builder().url("https://suri-map.example.com/placeholder").build())
    .protocol(Protocol.HTTP_1_1)
    .code(statusCode)
    .message("test")
    .body(body.toResponseBody())
    .build()
