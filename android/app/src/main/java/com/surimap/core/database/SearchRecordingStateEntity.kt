package com.surimap.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "search_recording_state",
    primaryKeys = ["account_id", "incident_id"],
    indices = [
        Index(
            value = ["account_id", "lifecycle_status", "updated_at"],
            name = "idx_search_recording_state_recovery"
        )
    ]
)
data class SearchRecordingStateEntity(
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "incident_id")
    val incidentId: String,
    @ColumnInfo(name = "op_id")
    val opId: String,
    @ColumnInfo(name = "search_path_id")
    val searchPathId: String,
    @ColumnInfo(name = "lifecycle_status")
    val lifecycleStatus: String,
    @ColumnInfo(name = "active_started_at")
    val activeStartedAt: Long?,
    @ColumnInfo(name = "accumulated_elapsed")
    val accumulatedElapsed: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
