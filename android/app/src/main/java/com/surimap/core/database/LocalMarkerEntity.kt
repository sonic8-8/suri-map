package com.surimap.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_marker",
    indices = [
        Index(
            value = ["incident_id", "police_phone_id", "sync_status"],
            name = "idx_local_marker_pending"
        ),
        Index(
            value = ["operation_id"],
            unique = true,
            name = "ux_local_marker_operation"
        )
    ]
)
data class LocalMarkerEntity(
    @PrimaryKey
    @ColumnInfo(name = "local_marker_id")
    val localMarkerId: String,
    @ColumnInfo(name = "outbox_id")
    val outboxId: String,
    @ColumnInfo(name = "operation_id")
    val operationId: String,
    @ColumnInfo(name = "incident_id")
    val incidentId: String,
    @ColumnInfo(name = "op_id")
    val opId: String,
    @ColumnInfo(name = "police_phone_id")
    val policePhoneId: String,
    val type: String,
    @ColumnInfo(name = "support_request_type")
    val supportRequestType: String? = null,
    val memo: String? = null,
    val lon: Double,
    val lat: Double,
    @ColumnInfo(name = "sync_status")
    val syncStatus: String,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis")
    val updatedAtMillis: Long
)
