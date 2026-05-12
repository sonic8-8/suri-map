package com.surimap.core.database

import androidx.room.ColumnInfo
import androidx.room.Ignore

data class OutboxStatusSummary(
    @ColumnInfo(name = "pending_count")
    val pendingCount: Int,
    @ColumnInfo(name = "retryable_count")
    val retryableCount: Int,
    @ColumnInfo(name = "final_failed_count")
    val finalFailedCount: Int,
    @ColumnInfo(name = "oldest_pending_client_requested_at")
    val oldestPendingClientRequestedAt: Long?
) {
    @Ignore
    val normalUnsentCount: Int = pendingCount + retryableCount
}
