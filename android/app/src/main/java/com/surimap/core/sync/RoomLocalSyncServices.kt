package com.surimap.core.sync

import com.surimap.core.database.LocalWriteDraftDao
import com.surimap.core.database.LocalWriteDraftEntity
import com.surimap.core.database.OutboxDao
import com.surimap.core.database.OutboxEntity
import java.time.Instant
import java.util.UUID

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
            payloadJson = writeOperation.payload,
            requestBodyHash = writeOperation.bodyHash,
            idempotencyKey = writeOperation.idempotencyKey,
            idempotencyStatus = OutboxStatus.PENDING.name,
            localMirrorStatus = initialHarnessStatus.name,
            attemptCount = 0,
            firstAttemptAt = null,
            nextAttemptAt = now,
            clientRequestedAt = writeOperation.clientTs.toEpochMilli(),
            clockOffsetMs = writeOperation.clockOffsetMs ?: 0L,
            clockSyncedAt = (writeOperation.clockSyncedAt ?: Instant.EPOCH).toEpochMilli(),
            serverAckTs = null,
            incidentClosedAt = null,
            lastError = null
        )
        outboxDao.upsert(entity)

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

        return EnqueueResult(
            outboxId = outboxId,
            operationId = writeOperation.operationId,
            status = OutboxStatus.PENDING,
            harnessStatus = initialHarnessStatus
        )
    }
}

class RoomOutboxReplay(
    private val outboxDao: OutboxDao,
    private val sender: OutboxSender = NoopOutboxSender,
    private val idempotencyReplayGate: InMemoryIdempotencyReplayGate = InMemoryIdempotencyReplayGate()
) : OutboxReplay {
    override suspend fun flushPending(policePhoneId: String, incidentId: String) {
        val candidates = outboxDao.findReplayCandidates(incidentId, policePhoneId)
        val now = System.currentTimeMillis()
        for (row in candidates) {
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
                                nextAttemptAt = now + 10_000L,
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
    override suspend fun requeue(operationId: String, reason: String) {
        val row = outboxDao.findByOperationId(operationId) ?: return
        if (row.idempotencyStatus != OutboxStatus.FAILED_RETRYABLE.name) {
            return
        }
        outboxDao.upsert(
            row.copy(
                idempotencyStatus = OutboxStatus.PENDING.name,
                localMirrorStatus = HarnessSyncStatus.PENDING_SEND.name,
                nextAttemptAt = System.currentTimeMillis(),
                lastError = reason
            )
        )
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
