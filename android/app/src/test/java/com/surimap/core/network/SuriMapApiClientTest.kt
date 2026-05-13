package com.surimap.core.network

import com.surimap.testing.policePhoneIdFixture
import java.io.IOException
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
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

class SuriMapApiClientTest {

    @Test
    fun executeAddsAppHeadersAndJsonBody() = runBlocking {
        val callFactory = CapturingCallFactory(
            response = response(statusCode = 200, body = """{"ok":true}""")
        )
        val client = SuriMapApiClient(
            baseUrl = "https://suri-map.example.com/api",
            callFactory = callFactory
        )

        val result = client.execute(
            SuriMapApiRequest(
                method = "POST",
                path = "/api/search-paths/batch",
                body = """{"points":[]}""",
                accessToken = "token-1",
                policePhoneId = POLICE_PHONE_ID,
                idempotencyKey = "idem-1"
            )
        )

        val request = callFactory.lastRequest!!
        assertEquals(200, result.statusCode)
        assertEquals("https://suri-map.example.com/api/search-paths/batch", request.url.toString())
        assertEquals("POST", request.method)
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertEquals(POLICE_PHONE_ID, request.header("X-PolicePhone-Id"))
        assertEquals("idem-1", request.header("Idempotency-Key"))
        assertEquals("""{"points":[]}""", readRequestBody(request))
    }

    @Test
    fun executeParsesBackendErrorCode() = runBlocking {
        val client = SuriMapApiClient(
            baseUrl = "https://suri-map.example.com",
            callFactory = CapturingCallFactory(
                response = response(statusCode = 403, body = """{"error":"incident_closed"}""")
            )
        )

        val result = client.execute(
            SuriMapApiRequest(method = "POST", path = "/api/markers", body = "{}")
        )

        assertEquals(403, result.statusCode)
        assertEquals("incident_closed", result.errorCode)
    }

    @Test
    fun executeWrapsIoFailureAsNetworkException() = runBlocking {
        val client = SuriMapApiClient(
            baseUrl = "https://suri-map.example.com",
            callFactory = CapturingCallFactory(exception = IOException("offline"))
        )

        try {
            client.execute(SuriMapApiRequest(method = "GET", path = "/api/incidents"))
            error("expected SuriMapNetworkException")
        } catch (exception: SuriMapNetworkException) {
            assertTrue(exception.cause is IOException)
        }
    }

    @Test
    fun executeReturnsNullBodyForBlankResponse() = runBlocking {
        val client = SuriMapApiClient(
            baseUrl = "https://suri-map.example.com",
            callFactory = CapturingCallFactory(response = response(statusCode = 204, body = ""))
        )

        val result = client.execute(SuriMapApiRequest(method = "GET", path = "/api/incidents"))

        assertEquals(204, result.statusCode)
        assertNull(result.body)
    }

    private class CapturingCallFactory(
        private val response: Response? = null,
        private val exception: IOException? = null
    ) : Call.Factory {
        var lastRequest: Request? = null

        override fun newCall(request: Request): Call {
            lastRequest = request
            return CapturingCall(request, response, exception)
        }
    }

    private class CapturingCall(
        private val request: Request,
        private val response: Response?,
        private val exception: IOException?
    ) : Call {
        override fun request(): Request = request

        override fun execute(): Response {
            exception?.let { throw it }
            return response!!.newBuilder().request(request).build()
        }

        override fun enqueue(responseCallback: Callback) = error("async calls are not used")
        override fun cancel() = Unit
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = false
        override fun timeout(): Timeout = Timeout.NONE
        override fun <T : Any> tag(type: KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun clone(): Call = CapturingCall(request, response, exception)
    }

    private companion object {
        val POLICE_PHONE_ID = policePhoneIdFixture("1")
    }
}

private fun response(statusCode: Int, body: String): Response {
    return Response.Builder()
        .request(Request.Builder().url("https://suri-map.example.com/placeholder").build())
        .protocol(Protocol.HTTP_1_1)
        .code(statusCode)
        .message("test")
        .body(body.toResponseBody())
        .build()
}

private fun readRequestBody(request: Request): String {
    val buffer = okio.Buffer()
    request.body!!.writeTo(buffer)
    return buffer.readUtf8()
}
