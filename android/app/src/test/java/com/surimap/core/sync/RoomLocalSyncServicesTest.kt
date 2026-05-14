package com.surimap.core.sync

import androidx.room.Room
import com.surimap.core.database.SuriMapDatabase
import com.surimap.testing.dutyShiftIdFixture
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.operationIdFixture
import com.surimap.testing.opIdFixture
import com.surimap.testing.policePhoneIdFixture
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        syncClient.enqueue(op.copy(operationId = operationIdFixture("dup-2")))
        replay.flushPending(policePhoneId = op.policePhoneId, incidentId = op.incidentId)

        assertEquals(1, database.outboxDao().countByIdempotencyKey(op.idempotencyKey))
        assertEquals(1, sender.sendCountByKey(op.idempotencyKey))
    }

    @Test
    fun duplicateIdempotencyKeyWithDifferentBodyHashMarksFailedFinal() = runBlocking {
        val first = sampleOperation(idempotencyKey = "idem-conflict-001", bodyHash = "sha256:a")
        val second = sampleOperation(
            operationId = operationIdFixture("conflict-2"),
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
            operationId = operationIdFixture("closed-001"),
            idempotencyKey = "idem-closed-001",
            bodyHash = "sha256:closed"
        ).copy(incidentId = incidentIdFixture("precinct-closed-001"))

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
            operationId = operationIdFixture("stale-001"),
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
            operationId = operationIdFixture("local-only-001"),
            incidentId = incidentIdFixture("precinct-first-001"),
            policePhoneId = policePhoneIdFixture("precinct-car-01"),
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
    fun dutyShiftEndWaitsForLowerSequenceSourceRowsBeforeReplay() = runBlocking {
        val dutyOpId = opIdFixture("precinct-001")
        val dutyShiftId = dutyShiftIdFixture("001")
        val blocker = sampleOperation(
            operationId = operationIdFixture("path-before-duty-end-001"),
            idempotencyKey = "idem-path-before-duty-end-001",
            bodyHash = "sha256:path-before-duty-end"
        ).copy(
            opId = dutyOpId,
            sequence = 502L,
            clockOffsetMs = null,
            clockSyncedAt = null
        )
        val dutyEnd = sampleOperation(
            operationId = operationIdFixture("duty-end-boundary-001"),
            idempotencyKey = "idem-duty-end-boundary-001",
            bodyHash = "sha256:duty-end-boundary"
        ).copy(
            dependencyGroup = DependencyGroup.DUTY_SHIFT,
            sequence = 503L,
            method = "PATCH",
            endpoint = "/api/duty-shifts/$dutyShiftId",
            payload = """{"incidentId":"${blocker.incidentId}","opId":"$dutyOpId","action":"END"}""",
            opId = dutyOpId,
            entityId = dutyShiftId,
            entityType = "duty_shift"
        )

        val blockerEnqueue = syncClient.enqueue(blocker)
        syncClient.enqueue(dutyEnd)

        replay.flushPending(policePhoneId = dutyEnd.policePhoneId, incidentId = dutyEnd.incidentId)

        val blockedDutyEnd = database.outboxDao().findByIdempotencyKey(dutyEnd.idempotencyKey)!!
        assertEquals(OutboxStatus.PENDING.name, blockedDutyEnd.idempotencyStatus)
        assertEquals(HarnessSyncStatus.PENDING_SEND.name, blockedDutyEnd.localMirrorStatus)
        assertEquals(0, blockedDutyEnd.attemptCount)
        assertEquals(0, sender.sendCountByKey(dutyEnd.idempotencyKey))

        val sourceRow = database.outboxDao().findById(blockerEnqueue.outboxId)!!
        database.outboxDao().upsert(
            sourceRow.copy(
                idempotencyStatus = OutboxStatus.ACKED.name,
                localMirrorStatus = HarnessSyncStatus.SYNCED.name,
                serverAckTs = System.currentTimeMillis()
            )
        )

        replay.flushPending(policePhoneId = dutyEnd.policePhoneId, incidentId = dutyEnd.incidentId)

        val sentDutyEnd = database.outboxDao().findByIdempotencyKey(dutyEnd.idempotencyKey)!!
        assertEquals(OutboxStatus.ACKED.name, sentDutyEnd.idempotencyStatus)
        assertEquals(1, sender.sendCountByKey(dutyEnd.idempotencyKey))
    }

    @Test
    fun replayFailurePathsArePersistedToRetryableAndFinalStates() = runBlocking {
        val retryableOp = sampleOperation(
            operationId = operationIdFixture("retryable-001"),
            idempotencyKey = "idem-retryable-001",
            bodyHash = "sha256:retryable"
        )
        val finalOp = sampleOperation(
            operationId = operationIdFixture("final-001"),
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

    @Test
    fun replayPersistsRetryableFailureErrorCodeFromSender() = runBlocking {
        val retryableOp = sampleOperation(
            operationId = operationIdFixture("retryable-phone-required-001"),
            idempotencyKey = "idem-retryable-phone-required-001",
            bodyHash = "sha256:retryable-phone-required"
        )

        syncClient.enqueue(retryableOp)
        sender.decisionByKey[retryableOp.idempotencyKey] = SendResult.RETRYABLE_FAILURE
        sender.retryableFailureErrorByKey[retryableOp.idempotencyKey] = "police_phone_required"
        replay.flushPending(policePhoneId = retryableOp.policePhoneId, incidentId = retryableOp.incidentId)

        val row = database.outboxDao().findByIdempotencyKey(retryableOp.idempotencyKey)!!
        assertEquals(OutboxStatus.FAILED_RETRYABLE.name, row.idempotencyStatus)
        assertEquals(HarnessSyncStatus.FAILED.name, row.localMirrorStatus)
        assertNull(row.nextAttemptAt)
        assertEquals("police_phone_required", row.lastError)

        replay.flushPending(policePhoneId = retryableOp.policePhoneId, incidentId = retryableOp.incidentId)

        assertEquals(1, sender.sendCountByKey(retryableOp.idempotencyKey))
    }

    @Test
    fun replayRequeuesAccessRepairRowsWhenAuthenticatedReplayIsAvailable() = runBlocking {
        val retryableOp = sampleOperation(
            operationId = operationIdFixture("retryable-auth-repair-001"),
            idempotencyKey = "idem-retryable-auth-repair-001",
            bodyHash = "sha256:retryable-auth-repair"
        )
        syncClient.enqueue(retryableOp)
        sender.decisionByKey[retryableOp.idempotencyKey] = SendResult.RETRYABLE_FAILURE
        sender.retryableFailureErrorByKey[retryableOp.idempotencyKey] = "http_401"

        replay.flushPending(policePhoneId = retryableOp.policePhoneId, incidentId = retryableOp.incidentId)
        val blockedRow = database.outboxDao().findByIdempotencyKey(retryableOp.idempotencyKey)!!
        assertEquals(OutboxStatus.FAILED_RETRYABLE.name, blockedRow.idempotencyStatus)
        assertNull(blockedRow.nextAttemptAt)
        assertEquals("http_401", blockedRow.lastError)

        sender.decisionByKey.remove(retryableOp.idempotencyKey)
        sender.retryableFailureErrorByKey.remove(retryableOp.idempotencyKey)
        val authenticatedReplay = RoomOutboxReplay(
            outboxDao = database.outboxDao(),
            sender = sender,
            accessRepairAvailable = { true }
        )

        authenticatedReplay.flushPending(
            policePhoneId = retryableOp.policePhoneId,
            incidentId = retryableOp.incidentId
        )

        val repairedRow = database.outboxDao().findByIdempotencyKey(retryableOp.idempotencyKey)!!
        assertEquals(OutboxStatus.ACKED.name, repairedRow.idempotencyStatus)
        assertEquals(HarnessSyncStatus.SYNCED.name, repairedRow.localMirrorStatus)
        assertEquals(2, sender.sendCountByKey(retryableOp.idempotencyKey))
    }

    @Test
    fun replayAppliesExponentialBackoffAndRetryAfterForRetryableFailures() = runBlocking {
        val retryableOp = sampleOperation(
            operationId = operationIdFixture("retryable-backoff-001"),
            idempotencyKey = "idem-retryable-backoff-001",
            bodyHash = "sha256:retryable-backoff"
        )

        val enqueue = syncClient.enqueue(retryableOp)
        val current = database.outboxDao().findById(enqueue.outboxId)!!
        database.outboxDao().upsert(current.copy(attemptCount = 1))
        sender.decisionByKey[retryableOp.idempotencyKey] = SendResult.RETRYABLE_FAILURE
        sender.retryableFailureErrorByKey[retryableOp.idempotencyKey] = "http_429"
        sender.retryAfterDelayByKey[retryableOp.idempotencyKey] = 90_000L
        val before = System.currentTimeMillis()

        replay.flushPending(policePhoneId = retryableOp.policePhoneId, incidentId = retryableOp.incidentId)

        val row = database.outboxDao().findByIdempotencyKey(retryableOp.idempotencyKey)!!
        assertEquals(2, row.attemptCount)
        assertEquals(OutboxStatus.FAILED_RETRYABLE.name, row.idempotencyStatus)
        assertTrue(row.nextAttemptAt!! >= before + 90_000L)
    }

    @Test
    fun replayAppliesBaseMultiplierAndCapForRetryableFailures() = runBlocking {
        val firstAttempt = sampleOperation(
            operationId = operationIdFixture("retryable-base-001"),
            idempotencyKey = "idem-retryable-base-001",
            bodyHash = "sha256:retryable-base"
        )
        val secondAttempt = sampleOperation(
            operationId = operationIdFixture("retryable-multiplier-001"),
            idempotencyKey = "idem-retryable-multiplier-001",
            bodyHash = "sha256:retryable-multiplier"
        )
        val cappedAttempt = sampleOperation(
            operationId = operationIdFixture("retryable-cap-001"),
            idempotencyKey = "idem-retryable-cap-001",
            bodyHash = "sha256:retryable-cap"
        )

        val firstEnqueue = syncClient.enqueue(firstAttempt)
        val secondEnqueue = syncClient.enqueue(secondAttempt)
        val cappedEnqueue = syncClient.enqueue(cappedAttempt)
        database.outboxDao().upsert(database.outboxDao().findById(secondEnqueue.outboxId)!!.copy(attemptCount = 1))
        database.outboxDao().upsert(database.outboxDao().findById(cappedEnqueue.outboxId)!!.copy(attemptCount = 5))
        listOf(firstAttempt, secondAttempt, cappedAttempt).forEach { operation ->
            sender.decisionByKey[operation.idempotencyKey] = SendResult.RETRYABLE_FAILURE
            sender.retryableFailureErrorByKey[operation.idempotencyKey] = "http_503"
        }
        val before = System.currentTimeMillis()

        replay.flushPending(policePhoneId = firstAttempt.policePhoneId, incidentId = firstAttempt.incidentId)
        val after = System.currentTimeMillis()

        val firstRow = database.outboxDao().findById(firstEnqueue.outboxId)!!
        val secondRow = database.outboxDao().findById(secondEnqueue.outboxId)!!
        val cappedRow = database.outboxDao().findById(cappedEnqueue.outboxId)!!
        assertEquals(1, firstRow.attemptCount)
        assertDelayInRange(firstRow.nextAttemptAt!!, before, after, 10_000L)
        assertEquals(2, secondRow.attemptCount)
        assertDelayInRange(secondRow.nextAttemptAt!!, before, after, 20_000L)
        assertEquals(6, cappedRow.attemptCount)
        assertDelayInRange(cappedRow.nextAttemptAt!!, before, after, 300_000L)
    }

    @Test
    fun replayKeepsJitteredRetryDelayWithinCap() = runBlocking {
        val jitterReplay = RoomOutboxReplay(database.outboxDao(), sender, enableRetryJitter = true)
        val cappedAttempt = sampleOperation(
            operationId = operationIdWithPositiveRetryJitter(attemptCount = 6),
            idempotencyKey = "idem-retryable-jitter-cap-001",
            bodyHash = "sha256:retryable-jitter-cap"
        )

        val cappedEnqueue = syncClient.enqueue(cappedAttempt)
        database.outboxDao().upsert(database.outboxDao().findById(cappedEnqueue.outboxId)!!.copy(attemptCount = 5))
        sender.decisionByKey[cappedAttempt.idempotencyKey] = SendResult.RETRYABLE_FAILURE
        sender.retryableFailureErrorByKey[cappedAttempt.idempotencyKey] = "http_503"
        val before = System.currentTimeMillis()

        jitterReplay.flushPending(policePhoneId = cappedAttempt.policePhoneId, incidentId = cappedAttempt.incidentId)
        val after = System.currentTimeMillis()

        val cappedRow = database.outboxDao().findById(cappedEnqueue.outboxId)!!
        assertEquals(6, cappedRow.attemptCount)
        assertTrue(cappedRow.nextAttemptAt!! >= before + 240_000L)
        assertTrue(cappedRow.nextAttemptAt!! <= after + 300_000L)
    }

    @Test
    fun replayStopsRetryableNetworkFailuresAtMaxAttempts() = runBlocking {
        val retryableOp = sampleOperation(
            operationId = operationIdFixture("retryable-max-001"),
            idempotencyKey = "idem-retryable-max-001",
            bodyHash = "sha256:retryable-max"
        )

        val enqueue = syncClient.enqueue(retryableOp)
        val current = database.outboxDao().findById(enqueue.outboxId)!!
        database.outboxDao().upsert(current.copy(attemptCount = 7))
        sender.decisionByKey[retryableOp.idempotencyKey] = SendResult.RETRYABLE_FAILURE
        sender.retryableFailureErrorByKey[retryableOp.idempotencyKey] = "http_503"

        replay.flushPending(policePhoneId = retryableOp.policePhoneId, incidentId = retryableOp.incidentId)

        val row = database.outboxDao().findByIdempotencyKey(retryableOp.idempotencyKey)!!
        assertEquals(8, row.attemptCount)
        assertEquals(OutboxStatus.FAILED_FINAL.name, row.idempotencyStatus)
        assertEquals(HarnessSyncStatus.FAILED.name, row.localMirrorStatus)
        assertNull(row.nextAttemptAt)
        assertEquals("http_503", row.lastError)
    }

    @Test
    fun replayStopsRetryableNetworkFailuresAfterMaxElapsed() = runBlocking {
        val retryableOp = sampleOperation(
            operationId = operationIdFixture("retryable-aged-001"),
            idempotencyKey = "idem-retryable-aged-001",
            bodyHash = "sha256:retryable-aged"
        )

        val enqueue = syncClient.enqueue(retryableOp)
        val current = database.outboxDao().findById(enqueue.outboxId)!!
        database.outboxDao().upsert(
            current.copy(
                attemptCount = 1,
                firstAttemptAt = System.currentTimeMillis() - 86_400_001L
            )
        )
        sender.decisionByKey[retryableOp.idempotencyKey] = SendResult.RETRYABLE_FAILURE
        sender.retryableFailureErrorByKey[retryableOp.idempotencyKey] = "http_503"

        replay.flushPending(policePhoneId = retryableOp.policePhoneId, incidentId = retryableOp.incidentId)

        val row = database.outboxDao().findByIdempotencyKey(retryableOp.idempotencyKey)!!
        assertEquals(OutboxStatus.FAILED_FINAL.name, row.idempotencyStatus)
        assertNull(row.nextAttemptAt)
        assertEquals("http_503", row.lastError)
    }

    @Test
    fun replayPersistsFinalFailureErrorCodeFromSender() = runBlocking {
        val finalOp = sampleOperation(
            operationId = operationIdFixture("final-with-error-001"),
            idempotencyKey = "idem-final-with-error-001",
            bodyHash = "sha256:final-with-error"
        )

        syncClient.enqueue(finalOp)
        sender.decisionByKey[finalOp.idempotencyKey] = SendResult.FINAL_FAILURE
        sender.finalFailureErrorByKey[finalOp.idempotencyKey] = "incident_access_denied"

        replay.flushPending(policePhoneId = finalOp.policePhoneId, incidentId = finalOp.incidentId)

        val row = database.outboxDao().findByIdempotencyKey(finalOp.idempotencyKey)!!
        assertEquals(OutboxStatus.FAILED_FINAL.name, row.idempotencyStatus)
        assertEquals("incident_access_denied", row.lastError)
    }

    private fun sampleOperation(
        operationId: String = operationIdFixture("outbox-path-001"),
        idempotencyKey: String,
        bodyHash: String
    ): LocalWriteOperation {
        val clockSyncedAt = Instant.ofEpochMilli(System.currentTimeMillis())
        return LocalWriteOperation(
            operationId = operationId,
            incidentId = incidentIdFixture("precinct-first-001"),
            policePhoneId = policePhoneIdFixture("precinct-car-01"),
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

    private fun assertDelayInRange(actualNextAttemptAt: Long, before: Long, after: Long, delayMs: Long) {
        assertTrue(actualNextAttemptAt >= before + delayMs)
        assertTrue(actualNextAttemptAt <= after + delayMs)
    }

    private fun operationIdWithPositiveRetryJitter(attemptCount: Int): String {
        return (1..200)
            .map { operationIdFixture("retryable-jitter-cap-$it") }
            .first { operationId -> retryJitterBucket(operationId, attemptCount) > 0 }
    }

    private fun retryJitterBucket(operationId: String, attemptCount: Int): Int =
        kotlin.math.abs("$operationId:$attemptCount".hashCode() % 41) - 20

    private class CapturingSender : OutboxSender {
        val decisionByKey: MutableMap<String, SendResult> = linkedMapOf()
        val retryableFailureErrorByKey: MutableMap<String, String> = linkedMapOf()
        val retryAfterDelayByKey: MutableMap<String, Long> = linkedMapOf()
        val finalFailureErrorByKey: MutableMap<String, String> = linkedMapOf()
        private val sentByKey: MutableMap<String, Int> = linkedMapOf()
        private var lastRetryableError: String? = null
        private var lastRetryAfterDelay: Long? = null
        private var lastFailureError: String? = null

        override suspend fun send(row: com.surimap.core.database.OutboxEntity): SendResult {
            sentByKey[row.idempotencyKey] = (sentByKey[row.idempotencyKey] ?: 0) + 1
            lastRetryableError = retryableFailureErrorByKey[row.idempotencyKey]
            lastRetryAfterDelay = retryAfterDelayByKey[row.idempotencyKey]
            lastFailureError = finalFailureErrorByKey[row.idempotencyKey]
            return decisionByKey[row.idempotencyKey] ?: SendResult.ACKED
        }

        override fun retryableFailureErrorCode(): String? = lastRetryableError

        override fun retryAfterDelayMs(): Long? = lastRetryAfterDelay

        override fun finalFailureErrorCode(): String? = lastFailureError

        fun sendCountByKey(idempotencyKey: String): Int = sentByKey[idempotencyKey] ?: 0
    }
}
