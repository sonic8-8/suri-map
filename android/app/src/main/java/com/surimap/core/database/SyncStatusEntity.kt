package com.surimap.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "android_sync_status",
    indices = [
        Index(
            value = ["incident_id"],
            name = "idx_android_sync_status_incident"
        )
    ]
)
data class SyncStatusEntity(
    @PrimaryKey
    @ColumnInfo(name = "police_phone_id")
    val policePhoneId: String,
    @ColumnInfo(name = "incident_id")
    val incidentId: String? = null,
    @ColumnInfo(name = "pending_count")
    val pendingCount: Int = 0,
    @ColumnInfo(name = "retryable_count")
    val retryableCount: Int = 0,
    @ColumnInfo(name = "final_failed_count")
    val finalFailedCount: Int = 0,
    @ColumnInfo(name = "last_successful_sync_at")
    val lastSuccessfulSyncAt: Long? = null,
    @ColumnInfo(name = "offline_since")
    val offlineSince: Long? = null,
    @ColumnInfo(name = "warning_codes")
    val warningCodes: String = ""
)
