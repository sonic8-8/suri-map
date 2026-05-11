package com.surimap.core.sync

import androidx.room.Room
import com.surimap.core.database.SuriMapDatabase
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomLocalSyncServicesTest {
    private lateinit var database: SuriMapDatabase
    private lateinit var syncClient: RoomSyncClient
    private lateinit var replay: RoomOutboxReplay
    private lateinit var requeue: RoomOutboxRequeue
    private lateinit var sender: CapturingSender

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            SuriMapDatabase::class.java
        ).build()
        sender = CapturingSender()
        syncClient = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao())
        replay = RoomOutboxReplay(database.outboxDao(), sender)
        requeue = RoomOutboxRequeue(database.outboxDao())
    }

    @After
    fun tearDown() {
        LocalSyncRuntime.outboxReplay = null
        database.close()
    }

    @Test
    fun enqueueThenReplayTransitionsPendingToAckedWithoutDuplicateRows() = runBlocking {
        val operation = sampleOperation(idempotencyKey = "idem-path-001", bodyHash = "sha256:path-001")

        val enqueue = syncClient.enqueue(operation)
        assertEquals(OutboxStatus.PENDING, enqueue.status)
        assertEquals(HarnessSyncStatus.PENDING_SEND, enqueue.harnessStatus)

        replay.flushPending(policePhoneId = operation.policePhoneId, incidentId = operation.incidentId)

        val row = database.outboxDao().findById(enqueue.outboxId)
        assertNotNull(row)
        assertEquals(OutboxStatus.ACKED.name, row!!.idempotencyStatus)
        assertEquals(HarnessSyncStatus.SYNCED.name, row.localMirrorStatus)
        assertNotNull(row.serverAckTs)
        assertEquals(1, database.outboxDao().countByIdempotencyKey(operation.idempotencyKey))
    }

    @Test
    fun duplicateIdempotencyKeyWithSameBodyHashDoesNotCreateExtraRow() = runBlocking {
        val op = sampleOperation(idempotencyKey = "idem-dup-001", bodyHash = "sha256:same")
        syncClient.enqueue(op)
        syncClient.enqueue(op.copy(operationId = "op-dup-2"))
        replay.flushPending(policePhoneId = op.policePhoneId, incidentId = op.incidentId)

        assertEquals(1, database.outboxDao().countByIdempotencyKey(op.idempotencyKey))
        assertEquals(1, sender.sendCountByKey(op.idempotencyKey))
    }

    @Test
    fun duplicateIdempotencyKeyWithDifferentBodyHashMarksFailedFinal() = runBlocking {
        val first = sampleOperation(idempotencyKey = "idem-conflict-001", bodyHash = "sha256:a")
        val second = sampleOperation(
            operationId = "op-conflict-2",
            idempotencyKey = "idem-conflict-001",
            bodyHash = "sha256:b"
        )

        val created = syncClient.enqueue(first)
        val mismatch = syncClient.enqueue(second)

        assertEquals(OutboxStatus.FAILED_FINAL, mismatch.status)
        val row = database.outboxDao().findById(created.outboxId)
        assertEquals(OutboxStatus.FAILED_FINAL.name, row!!.idempotencyStatus)
        assertEquals("idempotency_mismatch", row.lastError)
    }

    @Test
    fun workerInvokesReplayWhenInputProvided() = runBlocking {
        val operation = sampleOperation(idempotencyKey = "idem-worker-001", bodyHash = "sha256:worker")
        val enqueue = syncClient.enqueue(operation)
        val rowBefore = database.outboxDao().findById(enqueue.outboxId)
        assertEquals(OutboxStatus.PENDING.name, rowBefore!!.idempotencyStatus)

        LocalSyncRuntime.outboxReplay = replay
        val worker = androidx.work.testing.TestListenableWorkerBuilder<OutboxWorker>(
            RuntimeEnvironment.getApplication()
        )
            .setInputData(
                androidx.work.Data.Builder()
                    .putString("incidentId", operation.incidentId)
                    .putString("policePhoneId", operation.policePhoneId)
                    .build()
            )
            .build()

        assertEquals(androidx.work.ListenableWorker.Result.success(), worker.doWork())
        val rowAfter = database.outboxDao().findById(enqueue.outboxId)
        assertEquals(OutboxStatus.ACKED.name, rowAfter!!.idempotencyStatus)
        assertTrue(rowAfter.serverAckTs != null && rowAfter.serverAckTs > 0)
    }

    @Test
    fun requeueMovesFailedRetryableBackToPendingSend() = runBlocking {
        val operation = sampleOperation(idempotencyKey = "idem-requeue-001", bodyHash = "sha256:requeue")
        val enqueue = syncClient.enqueue(operation)
        val current = database.outboxDao().findById(enqueue.outboxId)!!
        database.outboxDao().upsert(
            current.copy(
                idempotencyStatus = OutboxStatus.FAILED_RETRYABLE.name,
                localMirrorStatus = HarnessSyncStatus.FAILED.name,
                lastError = "http_503"
            )
        )

        requeue.requeue(operationId = operation.operationId, reason = "NETWORK_RESTORED")

        val row = database.outboxDao().findById(enqueue.outboxId)!!
        assertEquals(OutboxStatus.PENDING.name, row.idempotencyStatus)
        assertEquals(HarnessSyncStatus.PENDING_SEND.name, row.localMirrorStatus)
    }

    @Test
    fun requeueDoesNotChangeNonRetryableStates() = runBlocking {
        val operation = sampleOperation(idempotencyKey = "idem-requeue-guard-001", bodyHash = "sha256:guard")
        val enqueue = syncClient.enqueue(operation)
        val base = database.outboxDao().findById(enqueue.outboxId)!!

        val states = listOf(OutboxStatus.FAILED_FINAL, OutboxStatus.ACKED, OutboxStatus.PENDING, OutboxStatus.PURGED)
        states.forEach { status ->
            database.outboxDao().upsert(
                base.copy(
                    idempotencyStatus = status.name,
                    localMirrorStatus = HarnessSyncStatus.FAILED.name,
                    lastError = "idempotency_mismatch"
                )
            )
            requeue.requeue(operationId = operation.operationId, reason = "NETWORK_RESTORED")
            val row = database.outboxDao().findById(enqueue.outboxId)!!
            assertEquals(status.name, row.idempotencyStatus)
        }
    }

    @Test
    fun postCloseRequeueIsRejectedAsFailedFinalAndDoesNotPromoteToPendingSend() = runBlocking {
        val operation = sampleOperation(
            operationId = "op-closed-001",
            idempotencyKey = "idem-closed-001",
            bodyHash = "sha256:closed"
        ).copy(incidentId = "inc-precinct-closed-001")

        val enqueue = syncClient.enqueue(operation)
        val current = database.outboxDao().findById(enqueue.outboxId)!!
        database.outboxDao().upsert(
            current.copy(
                idempotencyStatus = OutboxStatus.FAILED_RETRYABLE.name,
                localMirrorStatus = HarnessSyncStatus.FAILED.name,
                incidentClosedAt = current.clientRequestedAt - 1_000L,
                lastError = "network_unavailable"
            )
        )

        requeue.requeue(operationId = operation.operationId, reason = "USER_RETRY")

        val row = database.outboxDao().findById(enqueue.outboxId)!!
        assertEquals(OutboxStatus.FAILED_FINAL.name, row.idempotencyStatus)
        assertEquals(HarnessSyncStatus.FAILED.name, row.localMirrorStatus)
        assertEquals("post_close_requeue_rejected", row.lastError)
    }

    @Test
    fun staleClockRowStaysFailedRetryableUntilResynced() = runBlocking {
        val operation = sampleOperation(
            operationId = "op-stale-001",
            idempotencyKey = "idem-stale-001",
            bodyHash = "sha256:stale"
        )
        val enqueue = syncClient.enqueue(operation)
        val current = database.outboxDao().findById(enqueue.outboxId)!!
        database.outboxDao().upsert(
            current.copy(
                idempotencyStatus = OutboxStatus.FAILED_RETRYABLE.name,
                localMirrorStatus = HarnessSyncStatus.FAILED.name,
                clockSyncedAt = Instant.parse("2026-04-28T00:00:35Z").toEpochMilli(),
                lastError = "network_unavailable"
            )
        )

        requeue.requeue(operationId = operation.operationId, reason = "USER_RETRY")

        val row = database.outboxDao().findById(enqueue.outboxId)!!
        assertEquals(OutboxStatus.FAILED_RETRYABLE.name, row.idempotencyStatus)
        assertEquals(HarnessSyncStatus.FAILED.name, row.localMirrorStatus)
        assertEquals("clock_skew_exceeded_after_resync", row.lastError)
    }

    @Test
    fun pendingLocalRowIsNotReplayedUntilPromotedToPendingSend() = runBlocking {
        val localOnly = LocalWriteOperation(
            operationId = "op-local-only-001",
            incidentId = "inc-precinct-first-001",
            policePhoneId = "dev-precinct-car-01",
            dependencyGroup = DependencyGroup.PATH,
            sequence = 503L,
            method = "POST",
            endpoint = "/api/search-paths/batch",
            payload = """{"points":[{"lat":37.0,"lng":127.0}]}""",
            bodyHash = "sha256:local-only",
            idempotencyKey = "idem-local-only-001",
            clientTs = Instant.parse("2026-04-28T00:00:40Z"),
            clockOffsetMs = null,
            clockSyncedAt = null,
            entityType = "search_path"
        )

        val enqueue = syncClient.enqueue(localOnly)
        assertEquals(HarnessSyncStatus.PENDING_LOCAL, enqueue.harnessStatus)

        replay.flushPending(policePhoneId = localOnly.policePhoneId, incidentId = localOnly.incidentId)
        val row = database.outboxDao().findById(enqueue.outboxId)!!
        assertEquals(OutboxStatus.PENDING.name, row.idempotencyStatus)
        assertEquals(HarnessSyncStatus.PENDING_LOCAL.name, row.localMirrorStatus)
        assertEquals(0, sender.sendCountByKey(localOnly.idempotencyKey))
    }

    @Test
    fun replayFailurePathsArePersistedToRetryableAndFinalStates() = runBlocking {
        val retryableOp = sampleOperation(
            operationId = "op-retryable-001",
            idempotencyKey = "idem-retryable-001",
            bodyHash = "sha256:retryable"
        )
        val finalOp = sampleOperation(
            operationId = "op-final-001",
            idempotencyKey = "idem-final-001",
            bodyHash = "sha256:final"
        )

        syncClient.enqueue(retryableOp)
        syncClient.enqueue(finalOp)

        sender.decisionByKey[retryableOp.idempotencyKey] = SendResult.RETRYABLE_FAILURE
        sender.decisionByKey[finalOp.idempotencyKey] = SendResult.FINAL_FAILURE
        replay.flushPending(policePhoneId = retryableOp.policePhoneId, incidentId = retryableOp.incidentId)

        val retryableRow = database.outboxDao().findByIdempotencyKey(retryableOp.idempotencyKey)!!
        assertEquals(OutboxStatus.FAILED_RETRYABLE.name, retryableRow.idempotencyStatus)
        assertEquals(HarnessSyncStatus.FAILED.name, retryableRow.localMirrorStatus)

        val finalRow = database.outboxDao().findByIdempotencyKey(finalOp.idempotencyKey)!!
        assertEquals(OutboxStatus.FAILED_FINAL.name, finalRow.idempotencyStatus)
        assertEquals(HarnessSyncStatus.FAILED.name, finalRow.localMirrorStatus)
    }

    private fun sampleOperation(
        operationId: String = "op-outbox-path-001",
        idempotencyKey: String,
        bodyHash: String
    ): LocalWriteOperation {
        val clockSyncedAt = Instant.ofEpochMilli(System.currentTimeMillis())
        return LocalWriteOperation(
            operationId = operationId,
            incidentId = "inc-precinct-first-001",
            policePhoneId = "dev-precinct-car-01",
            dependencyGroup = DependencyGroup.PATH,
            sequence = 502L,
            method = "POST",
            endpoint = "/api/search-paths/batch",
            payload = """{"points":[{"lat":37.0,"lng":127.0}]}""",
            bodyHash = bodyHash,
            idempotencyKey = idempotencyKey,
            clientTs = clockSyncedAt.minusSeconds(5),
            clockOffsetMs = 0L,
            clockSyncedAt = clockSyncedAt,
            entityType = "search_path"
        )
    }

    private class CapturingSender : OutboxSender {
        val decisionByKey: MutableMap<String, SendResult> = linkedMapOf()
        private val sentByKey: MutableMap<String, Int> = linkedMapOf()

        override suspend fun send(row: com.surimap.core.database.OutboxEntity): SendResult {
            sentByKey[row.idempotencyKey] = (sentByKey[row.idempotencyKey] ?: 0) + 1
            return decisionByKey[row.idempotencyKey] ?: SendResult.ACKED
        }

        fun sendCountByKey(idempotencyKey: String): Int = sentByKey[idempotencyKey] ?: 0
    }
}
