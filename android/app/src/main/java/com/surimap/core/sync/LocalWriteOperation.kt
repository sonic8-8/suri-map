package com.surimap.core.sync

import java.time.Instant

enum class DependencyGroup {
    SESSION,
    PATH,
    MARKER,
    PHOTO,
    PACKAGE_INSTALLATION,
    DUTY_SHIFT,
    HANDOVER_MEMO
}

enum class OutboxStatus {
    PENDING,
    SENDING,
    ACKED,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    PURGED
}

enum class HarnessSyncStatus {
    PENDING_LOCAL,
    PENDING_SEND,
    SENDING,
    SYNCED,
    FAILED,
    PURGED
}

data class LocalWriteOperation(
    val operationId: String,
    val incidentId: String,
    val policePhoneId: String,
    val dependencyGroup: DependencyGroup,
    val sequence: Long,
    val method: String,
    val endpoint: String,
    val payload: String,
    val bodyHash: String,
    val idempotencyKey: String,
    val clientTs: Instant,
    val clockOffsetMs: Long? = null,
    val clockSyncedAt: Instant? = null,
    val opId: String? = null,
    val entityId: String? = null,
    val entityType: String? = null,
    val parentOperationId: String? = null,
    val accountId: String? = null
)

data class EnqueueResult(
    val outboxId: String,
    val operationId: String,
    val status: OutboxStatus,
    val harnessStatus: HarnessSyncStatus
)
