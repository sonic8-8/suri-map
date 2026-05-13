package com.surimap.feature.search

import com.surimap.core.path.PathPoint
import com.surimap.core.sync.DependencyGroup
import com.surimap.core.sync.EnqueueResult
import com.surimap.core.sync.HarnessSyncStatus
import com.surimap.core.sync.LocalWriteOperation
import com.surimap.core.sync.OutboxStatus
import com.surimap.core.sync.SyncClient
import com.surimap.feature.search.data.SearchPathLocalRecorder
import com.surimap.feature.search.data.SearchPathWriteContext
import com.surimap.feature.search.data.SearchPathWriteResult
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPathLocalRecorderTest {

    @Test
    fun startBatchAndEndEnqueuePathOperationsInOrder() = runBlocking {
        val syncClient = CapturingSyncClient()
        val recorder =
            SearchPathLocalRecorder(
                syncClient = syncClient,
                now = { CLIENT_TS },
                sequenceSource = sequenceSource(10),
                idFactory = idFactory()
            )

        val start = recorder.start(CONTEXT) as SearchPathWriteResult.Enqueued
        val batch = recorder.appendBatch(CONTEXT, PATH_ID, points()) as SearchPathWriteResult.Enqueued
        val end = recorder.end(CONTEXT, PATH_ID) as SearchPathWriteResult.Enqueued

        assertEquals(listOf(OP_START_ID, OP_BATCH_ID, OP_END_ID), syncClient.operations.map { it.operationId })
        assertEquals(listOf(10L, 11L, 12L), syncClient.operations.map { it.sequence })
        assertEquals(listOf("/api/search-paths", "/api/search-paths/batch", "/api/search-paths/$PATH_ID"), syncClient.operations.map { it.endpoint })
        assertTrue(syncClient.operations.all { it.dependencyGroup == DependencyGroup.PATH })
        assertEquals(OP_START_ID, start.operationId)
        assertEquals(OP_BATCH_ID, batch.operationId)
        assertEquals(OP_END_ID, end.operationId)
    }

    @Test
    fun missingCurrentContextDoesNotCreateOutboxOperation() = runBlocking {
        val syncClient = CapturingSyncClient()
        val recorder = SearchPathLocalRecorder(syncClient = syncClient)

        val result =
            recorder.start(
                SearchPathWriteContext(
                    incidentId = INCIDENT_ID,
                    opId = null,
                    policePhoneId = POLICE_PHONE_ID
                )
            )

        assertEquals(SearchPathWriteResult.Blocked, result)
        assertTrue(syncClient.operations.isEmpty())
    }

    @Test
    fun batchWithLessThanTwoPointsIsBlockedLocally() = runBlocking {
        val syncClient = CapturingSyncClient()
        val recorder = SearchPathLocalRecorder(syncClient = syncClient)

        val result = recorder.appendBatch(CONTEXT, PATH_ID, points().take(1))

        assertEquals(SearchPathWriteResult.Blocked, result)
        assertTrue(syncClient.operations.isEmpty())
    }

    @Test
    fun endWithoutKnownSearchPathIdIsBlockedLocally() = runBlocking {
        val syncClient = CapturingSyncClient()
        val recorder = SearchPathLocalRecorder(syncClient = syncClient)

        val result = recorder.end(CONTEXT, searchPathId = null)

        assertEquals(SearchPathWriteResult.Blocked, result)
        assertTrue(syncClient.operations.isEmpty())
    }

    @Test
    fun batchWithMoreThanOneHundredTwentyPointsIsBlockedLocally() = runBlocking {
        val syncClient = CapturingSyncClient()
        val recorder = SearchPathLocalRecorder(syncClient = syncClient)

        val result = recorder.appendBatch(CONTEXT, PATH_ID, List(121) { index -> pathPoint(index) })

        assertEquals(SearchPathWriteResult.Blocked, result)
        assertTrue(syncClient.operations.isEmpty())
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

    private fun points(): List<PathPoint> =
        listOf(
            pathPoint(0),
            pathPoint(1)
        )

    private fun pathPoint(index: Int): PathPoint =
        PathPoint(
            pointId = "pt-${(index + 1).toString().padStart(3, '0')}",
            lon = 126.969123 + (index * 0.0001),
            lat = 37.579123 + (index * 0.0001),
            speedMps = 1.4,
            horizontalAccuracyM = 5,
            clientTs = CLIENT_TS.plusSeconds(index * 5L)
        )

    private fun sequenceSource(first: Long): () -> Long {
        var next = first
        return { next++ }
    }

    private fun idFactory(): (String) -> String {
        val ids = listOf(OP_START_ID, OP_BATCH_ID, OP_END_ID)
        var next = 0
        return { ids[next++] }
    }

    private companion object {
        const val INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"
        const val OP_ID = "88888888-8888-8888-8888-888888880001"
        const val PATH_ID = "ffffffff-ffff-ffff-ffff-ffffffff0001"
        const val POLICE_PHONE_ID = "50000000-0000-0000-0000-000000000001"
        const val OP_START_ID = "11111111-1111-4111-8111-111111111001"
        const val OP_BATCH_ID = "11111111-1111-4111-8111-111111111002"
        const val OP_END_ID = "11111111-1111-4111-8111-111111111003"
        val CLIENT_TS: Instant = Instant.parse("2026-05-11T06:00:00Z")
        val CONTEXT =
            SearchPathWriteContext(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                policePhoneId = POLICE_PHONE_ID
            )
    }
}
