package com.surimap.core.sync

import com.surimap.core.database.LocalWriteDraftDao
import com.surimap.core.database.LocalWriteDraftEntity
import com.surimap.core.database.OutboxDao
import com.surimap.core.database.OutboxEntity
import java.time.Instant
import java.util.UUID

private const val POST_CLOSE_REQUEUE_REJECTED = "post_close_requeue_rejected"
private const val LOCAL_INCIDENT_CLOSED_PATH = "/_local/incident-closed"

class RoomSyncClient(
    private val outboxDao: OutboxDao,
    private val localWriteDraftDao: LocalWriteDraftDao
) : SyncClient {

    override suspend fun enqueue(writeOperation: LocalWriteOperation): EnqueueResult {
        val existing = outboxDao.findByIdempotencyKey(writeOperation.idempotencyKey)
        if (existing != null) {
            if (existing.requestBodyHash != writeOperation.bodyHash) {
                val failed = existing.copy(
                    idempotencyStatus = OutboxStatus.FAILED_FINAL.name,
                    localMirrorStatus = HarnessSyncStatus.FAILED.name,
                    lastError = "idempotency_mismatch"
                )
                outboxDao.upsert(failed)
                return EnqueueResult(
                    outboxId = failed.outboxId,
                    operationId = failed.operationId,
                    status = OutboxStatus.FAILED_FINAL,
                    harnessStatus = HarnessSyncStatus.FAILED
                )
            }
            return EnqueueResult(
                outboxId = existing.outboxId,
                operationId = existing.operationId,
                status = OutboxStatus.valueOf(existing.idempotencyStatus),
                harnessStatus = HarnessSyncStatus.valueOf(existing.localMirrorStatus)
            )
        }

        val knownIncidentClosedAt = outboxDao.findIncidentClosedAt(
            incidentId = writeOperation.incidentId,
            policePhoneId = writeOperation.policePhoneId
        )
        val isPostCloseWrite = knownIncidentClosedAt != null &&
            writeOperation.clientTs.toEpochMilli() > knownIncidentClosedAt

        val initialHarnessStatus = if (writeOperation.clockOffsetMs == null || writeOperation.clockSyncedAt == null) {
            HarnessSyncStatus.PENDING_LOCAL
        } else {
            HarnessSyncStatus.PENDING_SEND
        }

        val outboxId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val entity = OutboxEntity(
            outboxId = outboxId,
            operationId = writeOperation.operationId,
            incidentId = writeOperation.incidentId,
            opId = writeOperation.opId,
            policePhoneId = writeOperation.policePhoneId,
            dependencyGroup = writeOperation.dependencyGroup.name,
            parentOperationId = writeOperation.parentOperationId,
            sequence = writeOperation.sequence,
            requestMethod = writeOperation.method,
            requestPath = writeOperation.endpoint,
            payloadJson = if (isPostCloseWrite) "{}" else writeOperation.payload,
            requestBodyHash = writeOperation.bodyHash,
            idempotencyKey = writeOperation.idempotencyKey,
            idempotencyStatus = if (isPostCloseWrite) {
                OutboxStatus.FAILED_FINAL.name
            } else {
                OutboxStatus.PENDING.name
            },
            localMirrorStatus = if (isPostCloseWrite) {
                HarnessSyncStatus.FAILED.name
            } else {
                initialHarnessStatus.name
            },
            attemptCount = 0,
            firstAttemptAt = null,
            nextAttemptAt = if (isPostCloseWrite) null else now,
            clientRequestedAt = writeOperation.clientTs.toEpochMilli(),
            clockOffsetMs = writeOperation.clockOffsetMs ?: 0L,
            clockSyncedAt = (writeOperation.clockSyncedAt ?: Instant.EPOCH).toEpochMilli(),
            serverAckTs = null,
            incidentClosedAt = knownIncidentClosedAt,
            lastError = if (isPostCloseWrite) POST_CLOSE_REQUEUE_REJECTED else null
        )
        outboxDao.upsert(entity)

        if (!isPostCloseWrite) {
            localWriteDraftDao.upsert(
                LocalWriteDraftEntity(
                    draftId = outboxId,
                    incidentId = writeOperation.incidentId,
                    operationId = writeOperation.operationId,
                    entityType = writeOperation.entityType ?: "unknown",
                    entityId = writeOperation.entityId,
                    payload = writeOperation.payload,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
        }

        return EnqueueResult(
            outboxId = outboxId,
            operationId = writeOperation.operationId,
            status = if (isPostCloseWrite) OutboxStatus.FAILED_FINAL else OutboxStatus.PENDING,
            harnessStatus = if (isPostCloseWrite) HarnessSyncStatus.FAILED else initialHarnessStatus
        )
    }
}

class RoomOutboxReplay(
    private val outboxDao: OutboxDao,
    private val sender: OutboxSender = NoopOutboxSender,
    private val idempotencyReplayGate: InMemoryIdempotencyReplayGate = InMemoryIdempotencyReplayGate()
) : OutboxReplay {
    private val staleClockSyncAfterMs = 300_000L

    override suspend fun flushPending(policePhoneId: String, incidentId: String) {
        val now = System.currentTimeMillis()
        val minClockSyncedAt = now - staleClockSyncAfterMs
        outboxDao.rejectPostCloseRows(incidentId, policePhoneId)
        val candidates = outboxDao.findReplayCandidates(incidentId, policePhoneId, now, minClockSyncedAt)
        for (row in candidates) {
            if (row.incidentClosedAt != null && row.clientRequestedAt > row.incidentClosedAt) {
                outboxDao.upsert(
                    row.copy(
                        idempotencyStatus = OutboxStatus.FAILED_FINAL.name,
                        localMirrorStatus = HarnessSyncStatus.FAILED.name,
                        nextAttemptAt = null,
                        lastError = POST_CLOSE_REQUEUE_REJECTED
                    )
                )
                continue
            }

            val sending = row.copy(
                idempotencyStatus = OutboxStateMachine.transition(
                    OutboxStatus.valueOf(row.idempotencyStatus),
                    OutboxEvent.REPLAY_STARTED
                ).name,
                localMirrorStatus = HarnessSyncStatus.SENDING.name,
                firstAttemptAt = row.firstAttemptAt ?: now,
                nextAttemptAt = null,
                attemptCount = row.attemptCount + 1
            )
            outboxDao.upsert(sending)

            when (idempotencyReplayGate.reserve(sending.idempotencyKey, sending.requestBodyHash)) {
                IdempotencyReplayDecision.MISMATCH -> {
                    outboxDao.upsert(
                        sending.copy(
                            idempotencyStatus = OutboxStatus.FAILED_FINAL.name,
                            localMirrorStatus = HarnessSyncStatus.FAILED.name,
                            lastError = "idempotency_mismatch"
                        )
                    )
                }

                IdempotencyReplayDecision.REPLAYED -> {
                    outboxDao.upsert(
                        sending.copy(
                            idempotencyStatus = OutboxStatus.ACKED.name,
                            localMirrorStatus = HarnessSyncStatus.SYNCED.name,
                            serverAckTs = now,
                            lastError = null
                        )
                    )
                }

                IdempotencyReplayDecision.ACCEPTED -> {
                    when (sender.send(sending)) {
                        SendResult.ACKED -> outboxDao.upsert(
                            sending.copy(
                                idempotencyStatus = OutboxStatus.ACKED.name,
                                localMirrorStatus = HarnessSyncStatus.SYNCED.name,
                                serverAckTs = now,
                                lastError = null
                            )
                        )

                        SendResult.RETRYABLE_FAILURE -> outboxDao.upsert(
                            sending.copy(
                                idempotencyStatus = OutboxStatus.FAILED_RETRYABLE.name,
                                localMirrorStatus = HarnessSyncStatus.FAILED.name,
                                nextAttemptAt = if (sending.incidentClosedAt == null) now + 10_000L else null,
                                lastError = "network_unavailable"
                            )
                        )

                        SendResult.FINAL_FAILURE -> outboxDao.upsert(
                            sending.copy(
                                idempotencyStatus = OutboxStatus.FAILED_FINAL.name,
                                localMirrorStatus = HarnessSyncStatus.FAILED.name,
                                lastError = "invalid_payload"
                            )
                        )
                    }
                }
            }
        }
    }
}

class RoomOutboxRequeue(
    private val outboxDao: OutboxDao
) : OutboxRequeue {
    private val staleClockSyncAfterMs = 300_000L

    override suspend fun requeue(operationId: String, reason: String) {
        val row = outboxDao.findByOperationId(operationId) ?: return
        if (row.idempotencyStatus != OutboxStatus.FAILED_RETRYABLE.name) {
            return
        }
        val now = System.currentTimeMillis()

        if (isPostCloseRow(row)) {
            outboxDao.upsert(
                row.copy(
                    idempotencyStatus = OutboxStatus.FAILED_FINAL.name,
                    localMirrorStatus = HarnessSyncStatus.FAILED.name,
                    nextAttemptAt = null,
                    lastError = POST_CLOSE_REQUEUE_REJECTED
                )
            )
            return
        }

        if (isClockStale(row)) {
            outboxDao.upsert(
                row.copy(
                    idempotencyStatus = OutboxStatus.FAILED_RETRYABLE.name,
                    localMirrorStatus = HarnessSyncStatus.FAILED.name,
                    nextAttemptAt = now + 10_000L,
                    lastError = "clock_skew_exceeded_after_resync"
                )
            )
            return
        }

        outboxDao.upsert(
            row.copy(
                idempotencyStatus = OutboxStatus.PENDING.name,
                localMirrorStatus = HarnessSyncStatus.PENDING_SEND.name,
                nextAttemptAt = now,
                lastError = row.lastError ?: reason
            )
        )
    }

    suspend fun diagnosticsForOperation(operationId: String): OutboxDiagnosticsView? {
        val row = outboxDao.findByOperationId(operationId) ?: return null
        return OutboxDiagnosticsClassifier.classify(row)
    }

    private fun isClockStale(row: OutboxEntity): Boolean {
        val referenceTs = if (row.clientRequestedAt > 0L) row.clientRequestedAt else System.currentTimeMillis()
        return row.clockSyncedAt <= 0L || (referenceTs - row.clockSyncedAt) > staleClockSyncAfterMs
    }

    private fun isPostCloseRow(row: OutboxEntity): Boolean {
        return row.incidentClosedAt != null
    }
}

class LocalSyncPurgeHookAdapter(
    private val outboxDao: OutboxDao,
    private val localWriteDraftDao: LocalWriteDraftDao,
    private val closeDrainReplay: OutboxReplay
) : LocalSyncPurgeHook {

    override suspend fun purgeIncidentLocalSync(
        incidentId: String,
        purgeRunId: String,
        closedAt: String,
        purgeDeadlineTs: String
    ): LocalSyncPurgeResult {
        return try {
            val purgeRows = outboxDao.findPurgeAccountingRowsByIncidentId(incidentId)
            for (row in purgeRows) {
                if (row.idempotencyStatus == OutboxStatus.ACKED.name) {
                    outboxDao.markAckedPurged(row.outboxId)
                }
                localWriteDraftDao.deleteById(row.outboxId)
            }

            val retainedRows = retainedRowsForIncident(incidentId)
            LocalSyncPurgeResult(
                status = if (retainedRows.isEmpty()) "SUCCEEDED" else "WAITING_FOR_SYNC",
                purgedCount = purgeRows.size,
                retainedCount = retainedRows.size,
                retainedRows = retainedRows,
                orderedCleanupSteps = cleanupSteps(hasRetainedRows = retainedRows.isNotEmpty()),
                errorCode = null
            )
        } catch (exception: RuntimeException) {
            LocalSyncPurgeResult(
                status = "FAILED_RETRYABLE",
                purgedCount = 0,
                retainedCount = 0,
                retainedRows = emptyList(),
                orderedCleanupSteps = cleanupSteps(hasRetainedRows = false),
                errorCode = "local_delete_retryable"
            )
        }
    }

    override suspend fun handleIncidentClosed(
        incidentId: String,
        policePhoneId: String,
        closedAt: String,
        purgeRunId: String
    ): LocalSyncPurgeResult {
        val closedAtMillis = Instant.parse(closedAt).toEpochMilli()
        recordIncidentClosure(
            incidentId = incidentId,
            policePhoneId = policePhoneId,
            closedAtMillis = closedAtMillis,
            purgeRunId = purgeRunId
        )
        outboxDao.markIncidentClosed(
            incidentId = incidentId,
            policePhoneId = policePhoneId,
            closedAt = closedAtMillis
        )
        outboxDao.rejectPostCloseRows(incidentId, policePhoneId)
        closeDrainReplay.flushPending(policePhoneId = policePhoneId, incidentId = incidentId)
        outboxDao.rejectPostCloseRows(incidentId, policePhoneId)
        return purgeIncidentLocalSync(
            incidentId = incidentId,
            purgeRunId = purgeRunId,
            closedAt = closedAt,
            purgeDeadlineTs = closedAt
        )
    }

    private suspend fun recordIncidentClosure(
        incidentId: String,
        policePhoneId: String,
        closedAtMillis: Long,
        purgeRunId: String
    ) {
        val markerId = "incident-closure:$incidentId:$policePhoneId"
        outboxDao.upsert(
            OutboxEntity(
                outboxId = markerId,
                operationId = "$markerId:$purgeRunId",
                incidentId = incidentId,
                opId = null,
                policePhoneId = policePhoneId,
                dependencyGroup = DependencyGroup.SESSION.name,
                parentOperationId = null,
                sequence = Long.MAX_VALUE,
                requestMethod = "EVENT",
                requestPath = LOCAL_INCIDENT_CLOSED_PATH,
                payloadJson = "{}",
                requestBodyHash = "incident-closed:$closedAtMillis",
                idempotencyKey = markerId,
                idempotencyStatus = OutboxStatus.PURGED.name,
                localMirrorStatus = HarnessSyncStatus.PURGED.name,
                attemptCount = 0,
                firstAttemptAt = null,
                nextAttemptAt = null,
                clientRequestedAt = closedAtMillis,
                clockOffsetMs = 0L,
                clockSyncedAt = closedAtMillis,
                serverAckTs = closedAtMillis,
                incidentClosedAt = closedAtMillis,
                lastError = null
            )
        )
    }

    private suspend fun retainedRowsForIncident(incidentId: String): List<LocalSyncRetainedRow> {
        return outboxDao.findByIncidentId(incidentId)
            .filter { row ->
                row.idempotencyStatus in setOf(
                    OutboxStatus.PENDING.name,
                    OutboxStatus.SENDING.name,
                    OutboxStatus.FAILED_RETRYABLE.name,
                    OutboxStatus.FAILED_FINAL.name
                )
            }
            .map { row ->
                LocalSyncRetainedRow(
                    outboxId = row.outboxId,
                    operationId = row.operationId,
                    incidentId = row.incidentId,
                    idempotencyStatus = row.idempotencyStatus,
                    localPurgeState = "LOCAL_DELETE_PENDING",
                    retentionAccountingState = "WAITING_FOR_SYNC"
                )
            }
    }

    private fun cleanupSteps(hasRetainedRows: Boolean): List<LocalSyncCleanupStep> {
        val steps = mutableListOf(
            LocalSyncCleanupStep("ACKED_LOCAL_SYNC_CLEANUP")
        )
        if (hasRetainedRows) {
            steps += LocalSyncCleanupStep("RETAINED_ROWS_WAITING_FOR_SYNC")
        }
        steps += LocalSyncCleanupStep("PACKAGE_CLEANUP_AFTER_ACKED_LOCAL_SYNC")
        steps += LocalSyncCleanupStep("MISSING_PERSON_CLEANUP_AFTER_ACKED_LOCAL_SYNC")
        return steps
    }
}

enum class SendResult {
    ACKED,
    RETRYABLE_FAILURE,
    FINAL_FAILURE
}

fun interface OutboxSender {
    suspend fun send(row: OutboxEntity): SendResult
}

object NoopOutboxSender : OutboxSender {
    override suspend fun send(row: OutboxEntity): SendResult = SendResult.ACKED
}
