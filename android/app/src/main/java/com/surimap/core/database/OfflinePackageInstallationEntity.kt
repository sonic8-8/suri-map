package com.surimap.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "offline_package_installation_status",
    primaryKeys = ["incident_id", "police_phone_id"]
)
data class OfflinePackageInstallationEntity(
    @ColumnInfo(name = "incident_id")
    val incidentId: String,
    @ColumnInfo(name = "police_phone_id")
    val policePhoneId: String,
    @ColumnInfo(name = "manifest_id")
    val manifestId: String,
    @ColumnInfo(name = "manifest_version")
    val manifestVersion: Int,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "total_items")
    val totalItems: Int,
    @ColumnInfo(name = "completed_items")
    val completedItems: Int,
    @ColumnInfo(name = "failed_items")
    val failedItems: Int,
    @ColumnInfo(name = "version")
    val version: Long,
    @ColumnInfo(name = "ready_for_offline_use")
    val readyForOfflineUse: Boolean,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
