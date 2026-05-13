package com.surimap.core.network

import com.surimap.core.database.OutboxEntity
import com.surimap.core.sync.SendResult
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.operationIdFixture
import com.surimap.testing.opIdFixture
import com.surimap.testing.policePhoneIdFixture
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.reflect.KClass

class HttpOutboxSenderTest {

    @Test
    fun sendMapsTwoHundredResponsesToAcked() = runBlocking {
        val sender = senderForStatus(201)

        assertEquals(SendResult.ACKED, sender.send(outboxRow()))
    }

    @Test
    fun sendMapsRetryableHttpStatusesToRetryableFailure() = runBlocking {
        val retryableStatuses = listOf(408, 429, 500, 503)

        retryableStatuses.forEach { statusCode ->
            val sender = senderForStatus(statusCode)
            assertEquals(SendResult.RETRYABLE_FAILURE, sender.send(outboxRow()))
        }
    }

    @Test
    fun sendMapsContractFourHundredsToFinalFailure() = runBlocking {
        val finalStatuses = listOf(400, 401, 403, 409, 422)

        finalStatuses.forEach { statusCode ->
            val sender = senderForStatus(statusCode)
            assertEquals(SendResult.FINAL_FAILURE, sender.send(outboxRow()))
        }
    }

    @Test
    fun sendMapsNetworkExceptionsToRetryableFailure() = runBlocking {
        val sender = HttpOutboxSender(
            apiClient = SuriMapApiClient(
                baseUrl = "https://suri-map.example.com",
                callFactory = StaticCallFactory(exception = java.io.IOException("offline"))
            )
        )

        assertEquals(SendResult.RETRYABLE_FAILURE, sender.send(outboxRow()))
    }

    @Test
    fun sendUsesOutboxRowHeadersAndPayload() = runBlocking {
        val callFactory = StaticCallFactory(response = response(200))
        val sender = HttpOutboxSender(
            apiClient = SuriMapApiClient(
                baseUrl = "https://suri-map.example.com",
                callFactory = callFactory
            ),
            accessTokenProvider = AccessTokenProvider { "token-1" }
        )

        sender.send(outboxRow())

        val request = callFactory.lastRequest!!
        assertEquals("POST", request.method)
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertEquals(policePhoneIdFixture("precinct-car-01"), request.header("X-PolicePhone-Id"))
        assertEquals("idem-outbox-001", request.header("Idempotency-Key"))
        assertEquals("https://suri-map.example.com/api/search-paths/batch", request.url.toString())
    }
}

private fun senderForStatus(statusCode: Int): HttpOutboxSender {
    return HttpOutboxSender(
        apiClient = SuriMapApiClient(
            baseUrl = "https://suri-map.example.com",
            callFactory = StaticCallFactory(response = response(statusCode))
        )
    )
}

private fun outboxRow(): OutboxEntity {
    return OutboxEntity(
        outboxId = "outbox-001",
        operationId = operationIdFixture("outbox-001"),
        incidentId = incidentIdFixture("precinct-first-001"),
        opId = opIdFixture("1"),
        policePhoneId = policePhoneIdFixture("precinct-car-01"),
        dependencyGroup = "PATH",
        parentOperationId = null,
        sequence = 1L,
        requestMethod = "POST",
        requestPath = "/api/search-paths/batch",
        payloadJson = """{"points":[]}""",
        requestBodyHash = "sha256:body",
        idempotencyKey = "idem-outbox-001",
        idempotencyStatus = "PENDING",
        localMirrorStatus = "PENDING_SEND",
        attemptCount = 0,
        firstAttemptAt = null,
        nextAttemptAt = null,
        clientRequestedAt = Instant.parse("2026-04-28T00:00:40Z").toEpochMilli(),
        clockOffsetMs = 0L,
        clockSyncedAt = Instant.parse("2026-04-28T00:00:35Z").toEpochMilli(),
        serverAckTs = null,
        incidentClosedAt = null,
        lastError = null
    )
}

private class StaticCallFactory(
    private val response: Response? = null,
    private val exception: java.io.IOException? = null
) : Call.Factory {
    var lastRequest: Request? = null

    override fun newCall(request: Request): Call {
        lastRequest = request
        return StaticCall(request, response, exception)
    }
}

private class StaticCall(
    private val request: Request,
    private val response: Response?,
    private val exception: java.io.IOException?
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
    override fun clone(): Call = StaticCall(request, response, exception)
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
