package com.surimap.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "incident_summary",
    primaryKeys = ["account_id", "incident_id"],
    indices = [
        Index(
            value = ["account_id", "display_order"],
            name = "idx_incident_summary_account_order"
        )
    ]
)
data class IncidentSummaryEntity(
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "incident_id")
    val incidentId: String,
    @ColumnInfo(name = "current_op_id")
    val currentOpId: String?,
    @ColumnInfo(name = "current_op_label")
    val currentOpLabel: String?,
    @ColumnInfo(name = "current_duty_shift_id")
    val currentDutyShiftId: String?,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "summary")
    val summary: String,
    @ColumnInfo(name = "package_status")
    val packageStatus: String,
    @ColumnInfo(name = "display_order")
    val displayOrder: Int,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
