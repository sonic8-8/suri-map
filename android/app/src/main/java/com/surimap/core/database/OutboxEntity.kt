package com.surimap.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "android_outbox_row",
    indices = [
        Index(
            value = ["idempotency_status", "next_attempt_at"],
            name = "idx_android_outbox_status_next_attempt"
        ),
        Index(
            value = ["incident_id", "police_phone_id", "dependency_group", "sequence"],
            name = "idx_android_outbox_replay_order"
        ),
        Index(
            value = ["incident_id", "police_phone_id"],
            name = "idx_android_outbox_incident_police_phone"
        ),
        Index(
            value = ["idempotency_key"],
            unique = true,
            name = "ux_android_outbox_idempotency_key"
        )
    ]
)
data class OutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "outbox_id")
    val outboxId: String,
    @ColumnInfo(name = "client_operation_id")
    val operationId: String,
    @ColumnInfo(name = "incident_id")
    val incidentId: String? = null,
    @ColumnInfo(name = "op_id")
    val opId: String? = null,
    @ColumnInfo(name = "police_phone_id")
    val policePhoneId: String,
    @ColumnInfo(name = "dependency_group")
    val dependencyGroup: String,
    @ColumnInfo(name = "parent_client_operation_id")
    val parentOperationId: String? = null,
    val sequence: Long,
    @ColumnInfo(name = "request_method")
    val requestMethod: String,
    @ColumnInfo(name = "request_path")
    val requestPath: String,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "request_body_hash")
    val requestBodyHash: String,
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,
    @ColumnInfo(name = "idempotency_status")
    val idempotencyStatus: String,
    @ColumnInfo(name = "local_mirror_status")
    val localMirrorStatus: String,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int,
    @ColumnInfo(name = "first_attempt_at")
    val firstAttemptAt: Long? = null,
    @ColumnInfo(name = "next_attempt_at")
    val nextAttemptAt: Long? = null,
    @ColumnInfo(name = "client_requested_at")
    val clientRequestedAt: Long,
    @ColumnInfo(name = "clock_offset_ms")
    val clockOffsetMs: Long,
    @ColumnInfo(name = "clock_synced_at")
    val clockSyncedAt: Long,
    @ColumnInfo(name = "server_ack_ts")
    val serverAckTs: Long? = null,
    @ColumnInfo(name = "incident_closed_at")
    val incidentClosedAt: Long? = null,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null
)
