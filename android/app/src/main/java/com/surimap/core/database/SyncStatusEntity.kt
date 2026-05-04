package com.surimap.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_status")
data class SyncStatusEntity(
    @PrimaryKey val incidentId: String,
    val pendingCount: Int = 0,
    val sendingCount: Int = 0,
    val failedCount: Int = 0,
    val warningCodes: String = "",
    val lastSuccessfulSyncAtMillis: Long? = null,
    val localDeletePending: Boolean = false,
)
