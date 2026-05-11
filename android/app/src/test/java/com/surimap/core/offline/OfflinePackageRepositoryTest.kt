package com.surimap.core.offline

import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.sync.DependencyGroup
import com.surimap.core.sync.EnqueueResult
import com.surimap.core.sync.HarnessSyncStatus
import com.surimap.core.sync.LocalWriteOperation
import com.surimap.core.sync.OutboxStatus
import com.surimap.core.sync.SyncClient
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

class OfflinePackageRepositoryTest {

    @Test
    fun manifestRequestsCanonicalOfflinePackageReadPath() = runBlocking {
        val callFactory = CapturingCallFactory(response = response(200, """{"manifestId":"pkg-001"}"""))
        val repository = OfflinePackageRepository(
            apiClient = SuriMapApiClient(
                baseUrl = "https://suri-map.example.com",
                callFactory = callFactory
            ),
            accessTokenProvider = AccessTokenProvider { "token-1" }
        )

        val result = repository.manifest(
            OfflinePackageManifestQuery(
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID,
                knownManifestRevision = 4
            )
        )

        val request = callFactory.lastRequest!!
        assertEquals(200, result.statusCode)
        assertEquals("GET", request.method)
        assertEquals(
            "https://suri-map.example.com/api/incidents/$INCIDENT_ID/offline-package/manifest?policePhoneId=$POLICE_PHONE_ID&knownManifestRevision=4",
            request.url.toString()
        )
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertNull(request.header("Idempotency-Key"))
    }

    @Test
    fun reportInstallationEnqueuesCanonicalOutboxOperation() = runBlocking {
        val syncClient = CapturingSyncClient()
        val repository = OfflinePackageRepository(syncClient = syncClient)

        repository.reportInstallation(
            OfflinePackageInstallationCommand(
                operationId = "op-package-install-001",
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID,
                idempotencyKey = "idem-package-install-001",
                sequence = 30,
                manifestId = "pkg-precinct-first-rev-4",
                manifestVersion = 4,
                status = "READY",
                totalItems = 120,
                completedItems = 120,
                failedItems = 0,
                version = 7,
                readyForOfflineUse = true,
                failedItemKeys = emptyList(),
                lastError = null,
                clientTs = CLIENT_TS,
                clockOffsetMs = 50,
                clockSyncedAt = CLOCK_SYNCED_AT
            )
        )

        val operation = syncClient.lastOperation!!
        assertEquals(DependencyGroup.PACKAGE_INSTALLATION, operation.dependencyGroup)
        assertEquals("POST", operation.method)
        assertEquals("/api/incidents/$INCIDENT_ID/offline-package/installations", operation.endpoint)
        assertEquals(POLICE_PHONE_ID, operation.policePhoneId)
        assertEquals("package_installation", operation.entityType)
        assertEquals(
            """{"policePhoneId":"$POLICE_PHONE_ID","manifestId":"pkg-precinct-first-rev-4","manifestVersion":4,"status":"READY","totalItems":120,"completedItems":120,"failedItems":0,"version":7,"clientTs":"2026-05-11T06:00:00Z","readyForOfflineUse":true,"failedItemKeys":[],"sequence":30,"clockOffsetMs":50}""",
            operation.payload
        )
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
        const val INCIDENT_ID = "inc-precinct-first-001"
        const val POLICE_PHONE_ID = "phone-precinct-001"
        val CLIENT_TS: Instant = Instant.parse("2026-05-11T06:00:00Z")
        val CLOCK_SYNCED_AT: Instant = Instant.parse("2026-05-11T05:59:30Z")
    }
}
