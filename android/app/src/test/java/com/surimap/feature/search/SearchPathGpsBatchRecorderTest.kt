package com.surimap.feature.search

import com.surimap.core.location.GpsLocationFix
import com.surimap.core.sync.DependencyGroup
import com.surimap.core.sync.EnqueueResult
import com.surimap.core.sync.HarnessSyncStatus
import com.surimap.core.sync.LocalWriteOperation
import com.surimap.core.sync.OutboxStatus
import com.surimap.core.sync.SyncClient
import com.surimap.feature.search.data.SearchPathGpsBatchRecorder
import com.surimap.feature.search.data.SearchPathLocalRecorder
import com.surimap.feature.search.data.SearchPathWriteContext
import com.surimap.feature.search.data.SearchPathWriteResult
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPathGpsBatchRecorderTest {

    @Test
    fun gpsFixesFlushAsPathBatchAfterTenSeconds() = runBlocking {
        val syncClient = CapturingSyncClient()
        val localRecorder =
            SearchPathLocalRecorder(
                syncClient = syncClient,
                sequenceSource = sequenceSource(20),
                idFactory = operationIdFactory()
            )
        val batchRecorder =
            SearchPathGpsBatchRecorder(
                localRecorder = localRecorder,
                pointIdFactory = pointIdFactory()
            )

        assertNull(batchRecorder.recordFix(CONTEXT, PATH_ID, fix(0)))
        assertNull(batchRecorder.recordFix(CONTEXT, PATH_ID, fix(5)))
        val result = batchRecorder.recordFix(CONTEXT, PATH_ID, fix(10))

        assertTrue(result is SearchPathWriteResult.Enqueued)
        assertEquals(1, syncClient.operations.size)
        val operation = syncClient.operations.single()
        assertEquals(DependencyGroup.PATH, operation.dependencyGroup)
        assertEquals("POST", operation.method)
        assertEquals("/api/search-paths/batch", operation.endpoint)
        assertEquals(PATH_ID, operation.entityId)
        assertEquals(0, batchRecorder.pendingPointCount())
        assertTrue(operation.payload.contains("pt-gps-001"))
        assertTrue(operation.payload.contains("pt-gps-002"))
        assertTrue(operation.payload.contains("pt-gps-003"))
    }

    @Test
    fun singleGpsFixIsKeptPendingUntilSecondPoint() = runBlocking {
        val syncClient = CapturingSyncClient()
        val batchRecorder =
            SearchPathGpsBatchRecorder(
                localRecorder = SearchPathLocalRecorder(syncClient = syncClient),
                pointIdFactory = pointIdFactory()
            )

        assertNull(batchRecorder.recordFix(CONTEXT, PATH_ID, fix(0)))
        assertNull(batchRecorder.flush(CONTEXT, PATH_ID))

        assertEquals(1, batchRecorder.pendingPointCount())
        assertTrue(syncClient.operations.isEmpty())

        batchRecorder.clear()

        assertEquals(0, batchRecorder.pendingPointCount())
        assertNull(batchRecorder.flush(CONTEXT, PATH_ID))
    }

    @Test
    fun pathChangeDiscardsPendingPointsFromPreviousPath() = runBlocking {
        val syncClient = CapturingSyncClient()
        val batchRecorder =
            SearchPathGpsBatchRecorder(
                localRecorder =
                    SearchPathLocalRecorder(
                        syncClient = syncClient,
                        sequenceSource = sequenceSource(30),
                        idFactory = operationIdFactory()
                    ),
                pointIdFactory = pointIdFactory()
            )

        assertNull(batchRecorder.recordFix(CONTEXT, PATH_ID, fix(0)))
        assertNull(batchRecorder.recordFix(CONTEXT, OTHER_PATH_ID, fix(5)))
        val result = batchRecorder.recordFix(CONTEXT, OTHER_PATH_ID, fix(15))

        assertTrue(result is SearchPathWriteResult.Enqueued)
        val operation = syncClient.operations.single()
        assertEquals(OTHER_PATH_ID, operation.entityId)
        assertFalse(operation.payload.contains("pt-gps-001"))
        assertTrue(operation.payload.contains("pt-gps-002"))
        assertTrue(operation.payload.contains("pt-gps-003"))
    }

    @Test
    fun missingLocationSpeedIsEstimatedBeforeEnqueue() = runBlocking {
        val syncClient = CapturingSyncClient()
        val batchRecorder =
            SearchPathGpsBatchRecorder(
                localRecorder = SearchPathLocalRecorder(syncClient = syncClient),
                pointIdFactory = pointIdFactory()
            )

        assertNull(batchRecorder.recordFix(CONTEXT, PATH_ID, fixWithoutSpeed(0)))
        assertNull(batchRecorder.recordFix(CONTEXT, PATH_ID, fixWithoutSpeed(5)))
        val result = batchRecorder.recordFix(CONTEXT, PATH_ID, fixWithoutSpeed(10))

        assertTrue(result is SearchPathWriteResult.Enqueued)
        val payload = syncClient.operations.single().payload
        assertEquals(3, Regex(""""speedMps":""").findAll(payload).count())
        assertFalse(payload.contains(""""speedMps":null"""))
    }

    @Test
    fun flushAllDrainsRemainingGpsBatchBeforeSearchStopClear() = runBlocking {
        val syncClient = CapturingSyncClient()
        val batchRecorder =
            SearchPathGpsBatchRecorder(
                localRecorder =
                    SearchPathLocalRecorder(
                        syncClient = syncClient,
                        sequenceSource = sequenceSource(40),
                        idFactory = operationIdFactory()
                    ),
                pointIdFactory = pointIdFactory()
            )

        val autoFlushResults = mutableListOf<SearchPathWriteResult>()
        repeat(239) {
            batchRecorder.recordFix(CONTEXT, PATH_ID, fix(0))?.let(autoFlushResults::add)
        }

        val results = batchRecorder.flushAll(CONTEXT, PATH_ID)

        assertEquals(1, autoFlushResults.size)
        assertEquals(1, results.size)
        assertEquals(2, syncClient.operations.size)
        assertEquals(listOf(120, 119), syncClient.operations.map { operation -> pointCount(operation.payload) })
        assertEquals(0, batchRecorder.pendingPointCount())
    }


    private class CapturingSyncClient : SyncClient {
        val operations = mutableListOf<LocalWriteOperation>()

        override suspend fun enqueue(writeOperation: LocalWriteOperation): EnqueueResult {
            operations += writeOperation
            return EnqueueResult(
                outboxId = "outbox-${operations.size}",
                operationId = writeOperation.operationId,
                status = OutboxStatus.PENDING,
                harnessStatus = HarnessSyncStatus.PENDING_SEND
            )
        }
    }

    private fun fix(second: Long): GpsLocationFix =
        GpsLocationFix(
            lon = 126.970000 + (second * 0.00001),
            lat = 37.580000 + (second * 0.00001),
            bearingDegrees = null,
            speedMps = 1.4,
            horizontalAccuracyM = 5,
            capturedAt = CLIENT_TS.plusSeconds(second)
        )

    private fun fixWithoutSpeed(second: Long): GpsLocationFix =
        fix(second).copy(speedMps = null)

    private fun sequenceSource(first: Long): () -> Long {
        var next = first
        return { next++ }
    }

    private fun operationIdFactory(): (String) -> String {
        var next = 1
        return { "op-gps-batch-${(next++).toString().padStart(3, '0')}" }
    }

    private fun pointIdFactory(): () -> String {
        var next = 1
        return { "pt-gps-${(next++).toString().padStart(3, '0')}" }
    }

    private fun pointCount(payload: String): Int =
        Regex(""""pointId":""").findAll(payload).count()

    private companion object {
        const val INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"
        const val OP_ID = "88888888-8888-8888-8888-888888880001"
        const val PATH_ID = "ffffffff-ffff-ffff-ffff-ffffffff0001"
        const val OTHER_PATH_ID = "ffffffff-ffff-ffff-ffff-ffffffff0002"
        const val ACCOUNT_ID = "account-path-001"
        const val POLICE_PHONE_ID = "50000000-0000-0000-0000-000000000001"
        val CLIENT_TS: Instant = Instant.parse("2026-05-11T06:00:00Z")
        val CONTEXT =
            SearchPathWriteContext(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                accountId = ACCOUNT_ID,
                policePhoneId = POLICE_PHONE_ID
            )
    }
}
