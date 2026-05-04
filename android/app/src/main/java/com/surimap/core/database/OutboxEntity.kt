package com.surimap.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "android_outbox_row",
    indices = [
        Index(value = ["operationId"], unique = true),
        Index(value = ["idempotencyKey"]),
        Index(value = ["incidentId", "deviceId", "status"]),
        Index(value = ["dependencyGroup", "sequence"]),
    ],
)
data class OutboxEntity(
    @PrimaryKey val outboxId: String,
    val operationId: String,
    val incidentId: String,
    val deviceId: String,
    val dependencyGroup: String,
    val sequence: Long,
    val method: String,
    val endpoint: String,
    val payload: String,
    val bodyHash: String,
    val idempotencyKey: String,
    val status: String,
    val harnessStatus: String,
    val attemptCount: Int,
    val createdAtMillis: Long,
    val nextAttemptAtMillis: Long? = null,
    val lastAttemptAtMillis: Long? = null,
    val serverAckAtMillis: Long? = null,
    val clockOffsetMs: Long? = null,
    val clockSyncedAtMillis: Long? = null,
    val parentOperationId: String? = null,
    val opId: String? = null,
    val entityId: String? = null,
    val entityType: String? = null,
    val lastError: String? = null,
    val userSafeFailureCategory: String? = null,
)

