package com.surimap.core.sync

import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.network.SyncApiClient
import com.surimap.testing.incidentIdFixture
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.reflect.KClass

@RunWith(RobolectricTestRunner::class)
class ClockSyncStateTest {

    @Test
    fun syncClockStoresServerOffsetForLaterOutboxWrites() = runBlocking {
        val callFactory =
            CapturingCallFactory(
                response(
                    200,
                    """{"clientTs":"2026-05-11T06:00:00Z","serverTs":"2026-05-11T06:00:00.150Z","clockOffsetMs":150,"clockSyncedAt":"2026-05-11T06:00:00.150Z","maxAllowedSkewMs":300000}"""
                )
            )
        val state = ClockSyncState(now = { CLIENT_TS })
        val synced =
            state.sync(
                client = SyncApiClient(
                    apiClient = SuriMapApiClient(
                        baseUrl = "https://suri-map.example.com",
                        callFactory = callFactory
                    ),
                    accessTokenProvider = AccessTokenProvider { "token-1" }
                ),
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID
            )

        assertTrue(synced)
        assertEquals(150L, state.clockOffsetMs())
        assertEquals(Instant.parse("2026-05-11T06:00:00.150Z"), state.clockSyncedAt())
        assertEquals(
            "https://suri-map.example.com/api/sync/clock",
            callFactory.lastRequest!!.url.toString()
        )
    }

    @Test
    fun invalidOrRejectedSyncKeepsFallbackClockSnapshot() = runBlocking {
        val state = ClockSyncState(now = { CLIENT_TS })

        assertFalse(state.updateFromResponse("""{"serverTs":"2026-05-11T06:00:00Z"}"""))
        assertEquals(0L, state.clockOffsetMs())
        assertEquals(CLIENT_TS, state.clockSyncedAt())

        val rejected =
            state.sync(
                client = SyncApiClient(
                    apiClient = SuriMapApiClient(
                        baseUrl = "https://suri-map.example.com",
                        callFactory = CapturingCallFactory(response(409, """{"error":"clock_skew_exceeded"}"""))
                    )
                ),
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID
            )

        assertFalse(rejected)
        assertEquals(0L, state.clockOffsetMs())
        assertEquals(CLIENT_TS, state.clockSyncedAt())
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

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-001")
        val CLIENT_TS: Instant = Instant.parse("2026-05-11T06:00:00Z")
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
