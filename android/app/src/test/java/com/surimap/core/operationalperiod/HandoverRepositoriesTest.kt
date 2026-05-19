package com.surimap.core.operationalperiod

import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.sync.DependencyGroup
import com.surimap.core.sync.EnqueueResult
import com.surimap.core.sync.HarnessSyncStatus
import com.surimap.core.sync.LocalWriteOperation
import com.surimap.core.sync.OutboxStatus
import com.surimap.core.sync.SyncClient
import com.surimap.testing.dutyShiftIdFixture
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
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.reflect.KClass

class HandoverRepositoriesTest {

    @Test
    fun startAndEndDutyShiftEnqueueCanonicalOutboxOperations() = runBlocking {
        val syncClient = CapturingSyncClient()
        val repository = DutyShiftRepository(syncClient = syncClient)

        repository.startDutyShift(
            StartDutyShiftCommand(
                operationId = operationIdFixture("duty-start-001"),
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                policePhoneId = POLICE_PHONE_ID,
                idempotencyKey = "idem-duty-start-001",
                sequence = 40,
                clientTs = CLIENT_TS
            )
        )

        val start = syncClient.lastOperation!!
        assertEquals(DependencyGroup.DUTY_SHIFT, start.dependencyGroup)
        assertEquals("POST", start.method)
        assertEquals("/api/duty-shifts", start.endpoint)
        assertEquals(OP_ID, start.opId)
        assertEquals(POLICE_PHONE_ID, start.policePhoneId)
        assertEquals("duty_shift", start.entityType)
        assertEquals(
            """{"incidentId":"$INCIDENT_ID","opId":"$OP_ID","policePhoneId":"$POLICE_PHONE_ID","clientTs":"2026-05-11T06:00:00Z"}""",
            start.payload
        )

        repository.endDutyShift(
            EndDutyShiftCommand(
                operationId = operationIdFixture("duty-end-001"),
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                dutyShiftId = DUTY_SHIFT_ID,
                policePhoneId = POLICE_PHONE_ID,
                idempotencyKey = "idem-duty-end-001",
                sequence = 45,
                clientTs = CLIENT_TS,
                memo = "다음 근무자 인수인계"
            )
        )

        val end = syncClient.lastOperation!!
        assertEquals(DependencyGroup.DUTY_SHIFT, end.dependencyGroup)
        assertEquals("PATCH", end.method)
        assertEquals("/api/duty-shifts/$DUTY_SHIFT_ID", end.endpoint)
        assertEquals(DUTY_SHIFT_ID, end.entityId)
        assertEquals("duty_shift", end.entityType)
        assertEquals(
            """{"incidentId":"$INCIDENT_ID","opId":"$OP_ID","action":"END","clientTs":"2026-05-11T06:00:00Z","memo":"다음 근무자 인수인계"}""",
            end.payload
        )
    }

    @Test
    fun dutyShiftReadUsesCanonicalQueryEndpoint() = runBlocking {
        val callFactory = CapturingCallFactory(response = response(200, """{"items":[]}"""))
        val repository = DutyShiftRepository(
            apiClient = SuriMapApiClient(
                baseUrl = "https://suri-map.example.com",
                callFactory = callFactory
            ),
            accessTokenProvider = AccessTokenProvider { "token-1" }
        )

        val result = repository.listDutyShifts(
            DutyShiftQuery(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                policePhoneId = POLICE_PHONE_ID,
                status = "ACTIVE"
            )
        )

        val request = callFactory.lastRequest!!
        assertEquals(200, result.statusCode)
        assertEquals("GET", request.method)
        assertEquals(
            "https://suri-map.example.com/api/duty-shifts?incidentId=$INCIDENT_ID&opId=$OP_ID&policePhoneId=$POLICE_PHONE_ID&status=ACTIVE",
            request.url.toString()
        )
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertNull(request.header("Idempotency-Key"))
    }

    @Test
    fun handoverMemoCreateEnqueuesOutboxAndReadUsesCanonicalQueryEndpoint() = runBlocking {
        val syncClient = CapturingSyncClient()
        val repository = HandoverMemoRepository(syncClient = syncClient)

        repository.createHandoverMemo(
            CreateHandoverMemoCommand(
                operationId = operationIdFixture("memo-001"),
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                policePhoneId = POLICE_PHONE_ID,
                idempotencyKey = "idem-memo-001",
                sequence = 42,
                memoTargetType = "OPERATIONAL_PERIOD",
                memoTargetId = OP_ID,
                content = "OP 인수인계",
                clientTs = CLIENT_TS
            )
        )

        val operation = syncClient.lastOperation!!
        assertEquals(DependencyGroup.HANDOVER_MEMO, operation.dependencyGroup)
        assertEquals("POST", operation.method)
        assertEquals("/api/handover-memos", operation.endpoint)
        assertEquals(OP_ID, operation.opId)
        assertEquals("handover_memo", operation.entityType)
        assertEquals(
            """{"incidentId":"$INCIDENT_ID","opId":"$OP_ID","memoTargetType":"OPERATIONAL_PERIOD","memoTargetId":"$OP_ID","content":"OP 인수인계","clientTs":"2026-05-11T06:00:00Z"}""",
            operation.payload
        )

        val callFactory = CapturingCallFactory(response = response(200, """{"items":[]}"""))
        val readRepository = HandoverMemoRepository(
            apiClient = SuriMapApiClient(
                baseUrl = "https://suri-map.example.com",
                callFactory = callFactory
            ),
            accessTokenProvider = AccessTokenProvider { "token-1" }
        )

        readRepository.listHandoverMemos(
            HandoverMemoQuery(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                memoTargetType = "OPERATIONAL_PERIOD",
                memoTargetId = OP_ID
            )
        )

        val request = callFactory.lastRequest!!
        assertEquals("GET", request.method)
        assertEquals(
            "https://suri-map.example.com/api/handover-memos?incidentId=$INCIDENT_ID&opId=$OP_ID&memoTargetType=OPERATIONAL_PERIOD&memoTargetId=$OP_ID",
            request.url.toString()
        )
        assertNull(request.header("Idempotency-Key"))
    }

    @Test
    fun searchHistorySummaryReadIsReadOnly() = runBlocking {
        val callFactory = CapturingCallFactory(response = response(200, """{"items":[]}"""))
        val repository = SearchHistorySummaryReadRepository(
            apiClient = SuriMapApiClient(
                baseUrl = "https://suri-map.example.com",
                callFactory = callFactory
            ),
            accessTokenProvider = AccessTokenProvider { "token-1" }
        )

        repository.list(
            operationalPeriodId = OP_ID,
            query = SearchHistorySummaryQuery(
                incidentId = INCIDENT_ID,
                scopeType = "DUTY_SHIFT",
                scopeId = DUTY_SHIFT_ID,
                dutyShiftId = DUTY_SHIFT_ID,
                status = "READY"
            )
        )

        val request = callFactory.lastRequest!!
        assertEquals("GET", request.method)
        assertEquals(
            "https://suri-map.example.com/api/operational-periods/$OP_ID/search-history-summaries?incidentId=$INCIDENT_ID&scopeType=DUTY_SHIFT&scopeId=$DUTY_SHIFT_ID&dutyShiftId=$DUTY_SHIFT_ID&status=READY",
            request.url.toString()
        )
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertNull(request.header("Idempotency-Key"))
        assertNull(request.header("X-PolicePhone-Id"))
    }

    private class CapturingSyncClient : SyncClient {
        var lastOperation: LocalWriteOperation? = null

        override suspend fun enqueue(writeOperation: LocalWriteOperation): EnqueueResult {
            lastOperation = writeOperation
            return EnqueueResult(
                outboxId = "outbox-001",
                operationId = writeOperation.operationId,
                status = OutboxStatus.PENDING,
                harnessStatus = HarnessSyncStatus.PENDING_SEND
            )
        }
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

    private fun response(statusCode: Int, body: String): Response {
        return Response.Builder()
            .request(Request.Builder().url("https://suri-map.example.com/placeholder").build())
            .protocol(Protocol.HTTP_1_1)
            .code(statusCode)
            .message("test")
            .body(body.toResponseBody())
            .build()
    }

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val OP_ID = opIdFixture("precinct-first-001")
        val DUTY_SHIFT_ID = dutyShiftIdFixture("precinct-first-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-001")
        val CLIENT_TS: Instant = Instant.parse("2026-05-11T06:00:00Z")
    }
}
