package com.surimap.core.sync

enum class OutboxEvent {
    REPLAY_STARTED,
    ACK_RECEIVED,
    RETRYABLE_FAILURE,
    FINAL_FAILURE,
    REQUEUE_ACCEPTED,
    PURGE_CONFIRMED
}

object OutboxStateMachine {
    fun transition(from: OutboxStatus, event: OutboxEvent): OutboxStatus = when (from) {
        OutboxStatus.PENDING -> when (event) {
            OutboxEvent.REPLAY_STARTED -> OutboxStatus.SENDING
            OutboxEvent.FINAL_FAILURE -> OutboxStatus.FAILED_FINAL
            else -> from
        }

        OutboxStatus.SENDING -> when (event) {
            OutboxEvent.ACK_RECEIVED -> OutboxStatus.ACKED
            OutboxEvent.RETRYABLE_FAILURE -> OutboxStatus.FAILED_RETRYABLE
            OutboxEvent.FINAL_FAILURE -> OutboxStatus.FAILED_FINAL
            else -> from
        }

        OutboxStatus.FAILED_RETRYABLE -> when (event) {
            OutboxEvent.REQUEUE_ACCEPTED -> OutboxStatus.PENDING
            OutboxEvent.FINAL_FAILURE -> OutboxStatus.FAILED_FINAL
            else -> from
        }

        OutboxStatus.ACKED -> when (event) {
            OutboxEvent.PURGE_CONFIRMED -> OutboxStatus.PURGED
            else -> from
        }

        OutboxStatus.FAILED_FINAL -> when (event) {
            OutboxEvent.PURGE_CONFIRMED -> OutboxStatus.PURGED
            else -> from
        }

        OutboxStatus.PURGED -> OutboxStatus.PURGED
    }
}

object HarnessStatusMapper {
    fun toOutboxStatus(harnessStatus: HarnessSyncStatus): String = when (harnessStatus) {
        HarnessSyncStatus.PENDING_LOCAL -> OutboxStatus.PENDING.name
        HarnessSyncStatus.PENDING_SEND -> OutboxStatus.PENDING.name
        HarnessSyncStatus.SENDING -> OutboxStatus.SENDING.name
        HarnessSyncStatus.SYNCED -> OutboxStatus.ACKED.name
        HarnessSyncStatus.FAILED -> "${OutboxStatus.FAILED_RETRYABLE.name}|${OutboxStatus.FAILED_FINAL.name}"
        HarnessSyncStatus.PURGED -> OutboxStatus.PURGED.name
    }
}

enum class IdempotencyReplayDecision {
    ACCEPTED,
    REPLAYED,
    MISMATCH
}

class InMemoryIdempotencyReplayGate {
    private val bodyHashByKey = linkedMapOf<String, String>()

    fun reserve(idempotencyKey: String, bodyHash: String): IdempotencyReplayDecision {
        val knownHash = bodyHashByKey[idempotencyKey]
        if (knownHash == null) {
            bodyHashByKey[idempotencyKey] = bodyHash
            return IdempotencyReplayDecision.ACCEPTED
        }
        return if (knownHash == bodyHash) {
            IdempotencyReplayDecision.REPLAYED
        } else {
            IdempotencyReplayDecision.MISMATCH
        }
    }
}
