package com.surimap.core.sync

import androidx.room.Room
import com.surimap.core.database.OutboxEntity
import com.surimap.core.database.SuriMapDatabase
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.operationIdFixture
import com.surimap.testing.policePhoneIdFixture
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IncidentLocalCleanupPolicyTest {
    private lateinit var database: SuriMapDatabase
    private lateinit var syncClient: RoomSyncClient
    private lateinit var replay: RoomOutboxReplay
    private lateinit var requeue: RoomOutboxRequeue
    private lateinit var sender: CapturingSender
    private lateinit var purgeHook: LocalSyncPurgeHook

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
        purgeHook = LocalSyncPurgeHookAdapter(
            outboxDao = database.outboxDao(),
            localWriteDraftDao = database.localWriteDraftDao(),
            closeDrainReplay = replay
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun preCloseEligibleRowGetsOneCloseDrainThenManualRequeueIsBlocked() = runBlocking {
        val closeCutoff = Instant.parse("2026-04-28T00:01:00Z")
        val operation = sampleOperation(
            operationId = operationIdFixture("pre-close-drain-001"),
            idempotencyKey = "idem-pre-close-drain-001",
            bodyHash = "sha256:pre-close-drain",
            clientTs = closeCutoff.minusMillis(5_000)
        )
        val enqueue = syncClient.enqueue(operation)
        sender.decisionByKey[operation.idempotencyKey] = SendResult.RETRYABLE_FAILURE

        val closeReport = purgeHook.handleIncidentClosed(
            incidentId = operation.incidentId,
            policePhoneId = operation.policePhoneId,
            closedAt = closeCutoff.toString(),
            purgeRunId = "purge-run-pre-close-drain"
        )

        assertEquals("WAITING_FOR_SYNC", closeReport.status)
        assertEquals(1, sender.sendCountByKey(operation.idempotencyKey))
        val retainedAfterDrain = database.outboxDao().findById(enqueue.outboxId)!!
        assertEquals(OutboxStatus.FAILED_RETRYABLE.name, retainedAfterDrain.idempotencyStatus)
        assertEquals("LOCAL_DELETE_PENDING", closeReport.retainedRows.single().localPurgeState)
        assertNull(closeReport.errorCode)

        replay.flushPending(policePhoneId = operation.policePhoneId, incidentId = operation.incidentId)
        assertEquals(1, sender.sendCountByKey(operation.idempotencyKey))

        requeue.requeue(operationId = operation.operationId, reason = "USER_RETRY_AFTER_CLOSE")
        replay.flushPending(policePhoneId = operation.policePhoneId, incidentId = operation.incidentId)

        val rowAfterManualRequeue = database.outboxDao().findById(enqueue.outboxId)!!
        assertEquals(OutboxStatus.FAILED_FINAL.name, rowAfterManualRequeue.idempotencyStatus)
        assertEquals("post_close_requeue_rejected", rowAfterManualRequeue.lastError)
        assertEquals(1, sender.sendCountByKey(operation.idempotencyKey))
        assertEquals(
            "CLOSED_NO_RETRY",
            requeue.diagnosticsForOperation(operation.operationId)!!.userSafeFailureCategory
        )
    }

    @Test
    fun preCloseRetryableDueRowGetsOneCloseDrainAndThenPurgesWhenAcked() = runBlocking {
        val closeCutoff = Instant.parse("2026-04-28T00:01:00Z")
        val operation = sampleOperation(
            operationId = operationIdFixture("pre-close-retryable-drain-001"),
            idempotencyKey = "idem-pre-close-retryable-drain-001",
            bodyHash = "sha256:pre-close-retryable-drain",
            clientTs = closeCutoff.minusMillis(5_000)
        )
        val enqueue = syncClient.enqueue(operation)
        val failedBeforeClose = database.outboxDao().findById(enqueue.outboxId)!!.copy(
            idempotencyStatus = OutboxStatus.FAILED_RETRYABLE.name,
            localMirrorStatus = HarnessSyncStatus.FAILED.name,
            nextAttemptAt = System.currentTimeMillis() - 1,
            lastError = "network_unavailable"
        )
        database.outboxDao().upsert(failedBeforeClose)

        val closeReport = purgeHook.handleIncidentClosed(
            incidentId = operation.incidentId,
            policePhoneId = operation.policePhoneId,
            closedAt = closeCutoff.toString(),
            purgeRunId = "purge-run-pre-close-retryable-drain"
        )

        assertEquals("SUCCEEDED", closeReport.status)
        assertEquals(1, closeReport.purgedCount)
        assertEquals(0, closeReport.retainedCount)
        assertNull(closeReport.errorCode)
        assertEquals(1, sender.sendCountByKey(operation.idempotencyKey))
        assertEquals(OutboxStatus.PURGED.name, database.outboxDao().findById(enqueue.outboxId)!!.idempotencyStatus)
        assertNull(database.localWriteDraftDao().findById(enqueue.outboxId))
    }

    @Test
    fun postCloseRowsAreRejectedWithoutSendAndExposeOnlyClosedNoRetryDiagnostics() = runBlocking {
        val closeCutoff = Instant.parse("2026-04-28T00:01:00Z")
        val operation = sampleOperation(
            operationId = operationIdFixture("post-close-001"),
            idempotencyKey = "idem-post-close-001",
            bodyHash = "sha256:post-close",
            clientTs = closeCutoff.plusMillis(1)
        )
        purgeHook.handleIncidentClosed(
            incidentId = operation.incidentId,
            policePhoneId = operation.policePhoneId,
            closedAt = closeCutoff.toString(),
            purgeRunId = "purge-run-post-close-write-guard"
        )

        val enqueue = syncClient.enqueue(operation)

        replay.flushPending(policePhoneId = operation.policePhoneId, incidentId = operation.incidentId)

        val row = database.outboxDao().findById(enqueue.outboxId)!!
        assertEquals(OutboxStatus.FAILED_FINAL, enqueue.status)
        assertEquals(HarnessSyncStatus.FAILED, enqueue.harnessStatus)
        assertEquals(0, sender.sendCountByKey(operation.idempotencyKey))
        assertEquals(OutboxStatus.FAILED_FINAL.name, row.idempotencyStatus)
        assertEquals("post_close_requeue_rejected", row.lastError)
        assertEquals("{}", row.payloadJson)
        assertNull(database.localWriteDraftDao().findById(enqueue.outboxId))

        val diagnostics = OutboxDiagnosticsClassifier.classify(row)
        assertEquals("CLOSED_NO_RETRY", diagnostics.userSafeFailureCategory)
        assertFalse(diagnostics.retryable)
        assertFalse(diagnostics.userSafeFailureCategory.contains("post_close_requeue_rejected"))
    }

    @Test
    fun purgeIsIncidentScopedAndDoesNotRemoveOtherIncidentOutboxRowsOrDrafts() = runBlocking {
        val targetAcked = enqueueAckedOperation(
            sampleOperation(
                operationId = operationIdFixture("target-acked-001"),
                idempotencyKey = "idem-target-acked-001",
                bodyHash = "sha256:target-acked",
                incidentId = incidentIdFixture("close-target-001")
            )
        )
        val otherAcked = enqueueAckedOperation(
            sampleOperation(
                operationId = operationIdFixture("other-acked-001"),
                idempotencyKey = "idem-other-acked-001",
                bodyHash = "sha256:other-acked",
                incidentId = incidentIdFixture("close-other-001")
            )
        )

        val result = purgeHook.purgeIncidentLocalSync(
            incidentId = incidentIdFixture("close-target-001"),
            purgeRunId = "purge-run-scoped-001",
            closedAt = "2026-04-28T00:01:00Z",
            purgeDeadlineTs = "2026-04-29T00:01:00Z"
        )
        val idempotentRetry = purgeHook.purgeIncidentLocalSync(
            incidentId = incidentIdFixture("close-target-001"),
            purgeRunId = "purge-run-scoped-001",
            closedAt = "2026-04-28T00:01:00Z",
            purgeDeadlineTs = "2026-04-29T00:01:00Z"
        )

        assertEquals("SUCCEEDED", result.status)
        assertEquals(1, result.purgedCount)
        assertEquals(0, result.retainedCount)
        assertNull(result.errorCode)
        assertEquals("SUCCEEDED", idempotentRetry.status)
        assertEquals(1, idempotentRetry.purgedCount)
        assertEquals(0, idempotentRetry.retainedCount)
        assertNull(idempotentRetry.errorCode)
        assertEquals(OutboxStatus.PURGED.name, database.outboxDao().findById(targetAcked.outboxId)!!.idempotencyStatus)
        assertNull(database.localWriteDraftDao().findById(targetAcked.outboxId))
        assertEquals(OutboxStatus.ACKED.name, database.outboxDao().findById(otherAcked.outboxId)!!.idempotencyStatus)
        assertNotNull(database.localWriteDraftDao().findById(otherAcked.outboxId))
    }

    @Test
    fun purgeDeletesOnlyAckedLocalMirrorsAndRetainsUnackedRowsAsLocalDeletePending() = runBlocking {
        val acked = enqueueAckedOperation(
            sampleOperation(
                operationId = operationIdFixture("acked-purge-001"),
                idempotencyKey = "idem-acked-purge-001",
                bodyHash = "sha256:acked-purge"
            )
        )
        val retryable = enqueueFailedOperation(
            sampleOperation(
                operationId = operationIdFixture("retryable-retained-001"),
                idempotencyKey = "idem-retryable-retained-001",
                bodyHash = "sha256:retryable-retained"
            ),
            OutboxStatus.FAILED_RETRYABLE,
            "network_unavailable"
        )
        val finalRejected = enqueueFailedOperation(
            sampleOperation(
                operationId = operationIdFixture("final-retained-001"),
                idempotencyKey = "idem-final-retained-001",
                bodyHash = "sha256:final-retained"
            ),
            OutboxStatus.FAILED_FINAL,
            "post_close_requeue_rejected"
        )

        val result = purgeHook.purgeIncidentLocalSync(
            incidentId = incidentIdFixture("precinct-first-001"),
            purgeRunId = "purge-run-acked-only-001",
            closedAt = "2026-04-28T00:01:00Z",
            purgeDeadlineTs = "2026-04-29T00:01:00Z"
        )

        assertEquals("WAITING_FOR_SYNC", result.status)
        assertEquals(1, result.purgedCount)
        assertEquals(2, result.retainedCount)
        assertNull(result.errorCode)
        assertEquals(OutboxStatus.PURGED.name, database.outboxDao().findById(acked.outboxId)!!.idempotencyStatus)
        assertNull(database.localWriteDraftDao().findById(acked.outboxId))

        val retryableRow = database.outboxDao().findById(retryable.outboxId)!!
        val finalRow = database.outboxDao().findById(finalRejected.outboxId)!!
        assertEquals(OutboxStatus.FAILED_RETRYABLE.name, retryableRow.idempotencyStatus)
        assertEquals(OutboxStatus.FAILED_FINAL.name, finalRow.idempotencyStatus)
        assertNotNull(database.localWriteDraftDao().findById(retryable.outboxId))
        assertNotNull(database.localWriteDraftDao().findById(finalRejected.outboxId))
        assertEquals(
            listOf("LOCAL_DELETE_PENDING", "LOCAL_DELETE_PENDING"),
            result.retainedRows.map { it.localPurgeState }
        )
    }

    @Test
    fun retainedTombstonesRemainCountableUntilExplicitRetentionResolution() = runBlocking {
        enqueueFailedOperation(
            sampleOperation(
                operationId = operationIdFixture("tombstone-final-001"),
                idempotencyKey = "idem-tombstone-final-001",
                bodyHash = "sha256:tombstone-final"
            ),
            OutboxStatus.FAILED_FINAL,
            "post_close_requeue_rejected"
        )

        val firstPass = purgeHook.purgeIncidentLocalSync(
            incidentId = incidentIdFixture("precinct-first-001"),
            purgeRunId = "purge-run-tombstone-001",
            closedAt = "2026-04-28T00:01:00Z",
            purgeDeadlineTs = "2026-04-29T00:01:00Z"
        )
        val secondPass = purgeHook.purgeIncidentLocalSync(
            incidentId = incidentIdFixture("precinct-first-001"),
            purgeRunId = "purge-run-tombstone-002",
            closedAt = "2026-04-28T00:01:00Z",
            purgeDeadlineTs = "2026-04-29T00:01:00Z"
        )

        assertEquals("WAITING_FOR_SYNC", firstPass.status)
        assertEquals(1, firstPass.retainedCount)
        assertNull(firstPass.errorCode)
        assertEquals("WAITING_FOR_SYNC", secondPass.status)
        assertEquals(1, secondPass.retainedCount)
        assertNull(secondPass.errorCode)
        assertEquals(1, database.outboxDao().countByStatus(OutboxStatus.FAILED_FINAL.name))
    }

    @Test
    fun cleanupReportPreservesAckedLocalSyncBeforePackageAndMissingPersonSteps() = runBlocking {
        enqueueAckedOperation(
            sampleOperation(
                operationId = operationIdFixture("order-acked-001"),
                idempotencyKey = "idem-order-acked-001",
                bodyHash = "sha256:order-acked"
            )
        )
        enqueueFailedOperation(
            sampleOperation(
                operationId = operationIdFixture("order-retained-001"),
                idempotencyKey = "idem-order-retained-001",
                bodyHash = "sha256:order-retained"
            ),
            OutboxStatus.FAILED_RETRYABLE,
            "network_unavailable"
        )

        val result = purgeHook.purgeIncidentLocalSync(
            incidentId = incidentIdFixture("precinct-first-001"),
            purgeRunId = "purge-run-order-001",
            closedAt = "2026-04-28T00:01:00Z",
            purgeDeadlineTs = "2026-04-29T00:01:00Z"
        )

        assertEquals("WAITING_FOR_SYNC", result.status)
        assertNull(result.errorCode)
        assertEquals(
            listOf(
                "ACKED_LOCAL_SYNC_CLEANUP",
                "RETAINED_ROWS_WAITING_FOR_SYNC",
                "PACKAGE_CLEANUP_AFTER_ACKED_LOCAL_SYNC",
                "MISSING_PERSON_CLEANUP_AFTER_ACKED_LOCAL_SYNC"
            ),
            result.orderedCleanupSteps.map { it.code }
        )
        assertEquals("WAITING_FOR_SYNC", result.retainedRows.single().retentionAccountingState)
    }

    private suspend fun enqueueAckedOperation(operation: LocalWriteOperation): OutboxEntity {
        val enqueue = syncClient.enqueue(operation)
        val row = database.outboxDao().findById(enqueue.outboxId)!!
        val acked = row.copy(
            idempotencyStatus = OutboxStatus.ACKED.name,
            localMirrorStatus = HarnessSyncStatus.SYNCED.name,
            serverAckTs = operation.clientTs.plusSeconds(1).toEpochMilli(),
            incidentClosedAt = operation.clientTs.plusSeconds(20).toEpochMilli()
        )
        database.outboxDao().upsert(acked)
        return acked
    }

    private suspend fun enqueueFailedOperation(
        operation: LocalWriteOperation,
        status: OutboxStatus,
        lastError: String
    ): OutboxEntity {
        val enqueue = syncClient.enqueue(operation)
        val row = database.outboxDao().findById(enqueue.outboxId)!!
        val failed = row.copy(
            idempotencyStatus = status.name,
            localMirrorStatus = HarnessSyncStatus.FAILED.name,
            lastError = lastError,
            incidentClosedAt = operation.clientTs.plusSeconds(20).toEpochMilli()
        )
        database.outboxDao().upsert(failed)
        return failed
    }

    private fun sampleOperation(
        operationId: String,
        idempotencyKey: String,
        bodyHash: String,
        incidentId: String = incidentIdFixture("precinct-first-001"),
        clientTs: Instant = Instant.parse("2026-04-28T00:00:40Z")
    ): LocalWriteOperation = LocalWriteOperation(
        operationId = operationId,
        incidentId = incidentId,
        policePhoneId = policePhoneIdFixture("precinct-car-01"),
        dependencyGroup = DependencyGroup.PATH,
        sequence = 502L,
        method = "POST",
        endpoint = "/api/search-paths/batch",
        payload = """{"points":[{"lat":37.0,"lng":127.0}]}""",
        bodyHash = bodyHash,
        idempotencyKey = idempotencyKey,
        clientTs = clientTs,
        clockOffsetMs = 0L,
        clockSyncedAt = clientTs.minusSeconds(5),
        entityType = "search_path",
        entityId = operationId
    )

    private class CapturingSender : OutboxSender {
        val decisionByKey: MutableMap<String, SendResult> = linkedMapOf()
        private val sentByKey: MutableMap<String, Int> = linkedMapOf()

        override suspend fun send(row: OutboxEntity): SendResult {
            sentByKey[row.idempotencyKey] = (sentByKey[row.idempotencyKey] ?: 0) + 1
            return decisionByKey[row.idempotencyKey] ?: SendResult.ACKED
        }

        fun sendCountByKey(idempotencyKey: String): Int = sentByKey[idempotencyKey] ?: 0
    }
}
