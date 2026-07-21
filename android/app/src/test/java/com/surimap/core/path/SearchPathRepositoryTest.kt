package com.surimap.core.path

import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.sync.DependencyGroup
import com.surimap.core.sync.EnqueueResult
import com.surimap.core.sync.HarnessSyncStatus
import com.surimap.core.sync.LocalWriteOperation
import com.surimap.core.sync.OutboxStatus
import com.surimap.core.sync.SyncClient
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.operationIdFixture
import com.surimap.testing.opIdFixture
import com.surimap.testing.pathIdFixture
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

class SearchPathRepositoryTest {

    @Test
    fun startSearchPathEnqueuesCanonicalOutboxOperation() = runBlocking {
        val syncClient = CapturingSyncClient()
        val repository = SearchPathRepository(syncClient = syncClient)

        repository.startSearchPath(
            StartSearchPathCommand(
                operationId = operationIdFixture("path-start-001"),
                searchPathId = PATH_ID,
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                accountId = ACCOUNT_ID,
                policePhoneId = POLICE_PHONE_ID,
                idempotencyKey = "idem-path-start-001",
                sequence = 10,
                clientTs = CLIENT_TS,
                clockOffsetMs = 120,
                clockSyncedAt = CLOCK_SYNCED_AT
            )
        )

        val operation = syncClient.lastOperation!!
        assertEquals(operationIdFixture("path-start-001"), operation.operationId)
        assertEquals(INCIDENT_ID, operation.incidentId)
        assertEquals(OP_ID, operation.opId)
        assertEquals(POLICE_PHONE_ID, operation.policePhoneId)
        assertEquals(DependencyGroup.PATH, operation.dependencyGroup)
        assertEquals(10, operation.sequence)
        assertEquals("POST", operation.method)
        assertEquals("/api/search-paths", operation.endpoint)
        assertEquals("idem-path-start-001", operation.idempotencyKey)
        assertEquals(CLIENT_TS, operation.clientTs)
        assertEquals(120L, operation.clockOffsetMs)
        assertEquals(CLOCK_SYNCED_AT, operation.clockSyncedAt)
        assertEquals("search_path", operation.entityType)
        assertEquals(PATH_ID, operation.entityId)
        assertEquals(
            """{"searchPathId":"$PATH_ID","incidentId":"$INCIDENT_ID","opId":"$OP_ID","clientTs":"2026-05-11T06:00:00Z","clockOffsetMs":120}""",
            operation.payload
        )
        assertTrue(operation.bodyHash.startsWith("sha256:"))
    }

    @Test
    fun endSearchPathEnqueuesPatchOperationForPathId() = runBlocking {
        val syncClient = CapturingSyncClient()
        val repository = SearchPathRepository(syncClient = syncClient)

        repository.endSearchPath(
            EndSearchPathCommand(
                operationId = operationIdFixture("path-end-001"),
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                accountId = ACCOUNT_ID,
                searchPathId = PATH_ID,
                policePhoneId = POLICE_PHONE_ID,
                idempotencyKey = "idem-path-end-001",
                sequence = 11,
                clientTs = CLIENT_TS
            )
        )

        val operation = syncClient.lastOperation!!
        assertEquals("PATCH", operation.method)
        assertEquals("/api/search-paths/$PATH_ID", operation.endpoint)
        assertEquals(DependencyGroup.PATH, operation.dependencyGroup)
        assertEquals(PATH_ID, operation.entityId)
        assertEquals(
            """{"action":"END","clientTs":"2026-05-11T06:00:00Z"}""",
            operation.payload
        )
    }

    @Test
    fun patchSearchPathEnqueuesPauseAndResumeActions() = runBlocking {
        val syncClient = CapturingSyncClient()
        val repository = SearchPathRepository(syncClient = syncClient)

        repository.patchSearchPath(
            PatchSearchPathCommand(
                operationId = operationIdFixture("path-pause-001"),
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                accountId = ACCOUNT_ID,
                searchPathId = PATH_ID,
                policePhoneId = POLICE_PHONE_ID,
                action = SearchPathLifecycleAction.PAUSE,
                idempotencyKey = "idem-path-pause-001",
                sequence = 11,
                clientTs = CLIENT_TS
            )
        )
        repository.patchSearchPath(
            PatchSearchPathCommand(
                operationId = operationIdFixture("path-resume-001"),
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                accountId = ACCOUNT_ID,
                searchPathId = PATH_ID,
                policePhoneId = POLICE_PHONE_ID,
                action = SearchPathLifecycleAction.RESUME,
                idempotencyKey = "idem-path-resume-001",
                sequence = 12,
                clientTs = CLIENT_TS.plusSeconds(60)
            )
        )

        val pause = syncClient.operations[0]
        val resume = syncClient.operations[1]
        assertEquals("/api/search-paths/$PATH_ID", pause.endpoint)
        assertEquals("""{"action":"PAUSE","clientTs":"2026-05-11T06:00:00Z"}""", pause.payload)
        assertEquals("""{"action":"RESUME","clientTs":"2026-05-11T06:01:00Z"}""", resume.payload)
        assertTrue(syncClient.operations.all { it.dependencyGroup == DependencyGroup.PATH })
    }

    @Test
    fun appendPathBatchEnqueuesBatchOperationWithPoints() = runBlocking {
        val syncClient = CapturingSyncClient()
        val repository = SearchPathRepository(syncClient = syncClient)

        repository.appendPathBatch(
            AppendPathBatchCommand(
                operationId = operationIdFixture("path-batch-001"),
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                accountId = ACCOUNT_ID,
                searchPathId = PATH_ID,
                policePhoneId = POLICE_PHONE_ID,
                idempotencyKey = "idem-path-batch-001",
                sequence = 12,
                points = listOf(
                    PathPoint(
                        pointId = "pt-001",
                        lon = 126.969123,
                        lat = 37.579123,
                        speedMps = 1.4,
                        horizontalAccuracyM = 5,
                        clientTs = Instant.parse("2026-05-11T06:00:00Z"),
                        locationProvider = "gps",
                        elapsedRealtimeNanos = 12_000_000_000L
                    ),
                    PathPoint(
                        pointId = "pt-002",
                        lon = 126.969223,
                        lat = 37.579223,
                        speedMps = 1.5,
                        horizontalAccuracyM = 6,
                        clientTs = Instant.parse("2026-05-11T06:00:05Z"),
                        locationProvider = "network",
                        elapsedRealtimeNanos = 17_000_000_000L
                    )
                ),
                clockOffsetMs = 120,
                clockSyncedAt = CLOCK_SYNCED_AT
            )
        )

        val operation = syncClient.lastOperation!!
        assertEquals("POST", operation.method)
        assertEquals("/api/search-paths/batch", operation.endpoint)
        assertEquals(PATH_ID, operation.entityId)
        assertEquals(
            """{"incidentId":"$INCIDENT_ID","opId":"$OP_ID","pathId":"$PATH_ID","points":[{"pointId":"pt-001","lon":126.969123,"lat":37.579123,"speedMps":1.4,"horizontalAccuracyM":5,"clientTs":"2026-05-11T06:00:00Z","locationProvider":"gps","elapsedRealtimeNanos":12000000000},{"pointId":"pt-002","lon":126.969223,"lat":37.579223,"speedMps":1.5,"horizontalAccuracyM":6,"clientTs":"2026-05-11T06:00:05Z","locationProvider":"network","elapsedRealtimeNanos":17000000000}],"clockOffsetMs":120}""",
            operation.payload
        )
    }

    @Test
    fun appendPathBatchNormalizesGpsCoordinatesToBackendPrecision() = runBlocking {
        val syncClient = CapturingSyncClient()
        val repository = SearchPathRepository(syncClient = syncClient)

        repository.appendPathBatch(
            AppendPathBatchCommand(
                operationId = operationIdFixture("path-batch-precision-001"),
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                accountId = ACCOUNT_ID,
                searchPathId = PATH_ID,
                policePhoneId = POLICE_PHONE_ID,
                idempotencyKey = "idem-path-batch-precision-001",
                sequence = 13,
                points = listOf(
                    PathPoint(
                        pointId = "pt-precision-001",
                        lon = 126.89209833333334,
                        lat = 35.17379833333333,
                        clientTs = Instant.parse("2026-05-11T06:00:00Z")
                    )
                )
            )
        )

        val operation = syncClient.lastOperation!!
        assertEquals(
            """{"incidentId":"$INCIDENT_ID","opId":"$OP_ID","pathId":"$PATH_ID","points":[{"pointId":"pt-precision-001","lon":126.892098,"lat":35.173798,"clientTs":"2026-05-11T06:00:00Z"}]}""",
            operation.payload
        )
        assertFalse(operation.payload.contains("333333"))
    }

    @Test
    fun listSearchPathsRequestsCanonicalReadPath() = runBlocking {
        val callFactory = CapturingCallFactory(response = response(200, """{"paths":[]}"""))
        val repository = SearchPathRepository(
            apiClient = SuriMapApiClient(
                baseUrl = "https://suri-map.example.com/api",
                callFactory = callFactory
            ),
            accessTokenProvider = AccessTokenProvider { "token-1" }
        )

        val result = repository.listSearchPaths(
            SearchPathQuery(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                includeGeometry = true,
                geometryMode = "RENDER_SIMPLIFIED",
                sinceVersion = 3,
                limit = 500,
                sort = "startedAtAsc",
                movementType = "FOOT"
            )
        )

        val request = callFactory.lastRequest!!
        assertEquals(200, result.statusCode)
        assertEquals("GET", request.method)
        assertEquals(
            "https://suri-map.example.com/api/search-paths?incidentId=$INCIDENT_ID&opId=$OP_ID&includeGeometry=true&geometryMode=RENDER_SIMPLIFIED&sinceVersion=3&limit=500&sort=startedAtAsc&movementType=FOOT",
            request.url.toString()
        )
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertNull(request.header("Idempotency-Key"))
    }

    private class CapturingSyncClient : SyncClient {
        var lastOperation: LocalWriteOperation? = null
        val operations = mutableListOf<LocalWriteOperation>()

        override suspend fun enqueue(writeOperation: LocalWriteOperation): EnqueueResult {
            lastOperation = writeOperation
            operations += writeOperation
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
        val PATH_ID = pathIdFixture("precinct-first-001")
        const val ACCOUNT_ID = "account-path-001"
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-001")
        val CLIENT_TS: Instant = Instant.parse("2026-05-11T06:00:00Z")
        val CLOCK_SYNCED_AT: Instant = Instant.parse("2026-05-11T05:59:30Z")
    }
}
