package com.surimap.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "offline_package_item_status",
    primaryKeys = ["incident_id", "police_phone_id", "manifest_id", "item_key"]
)
data class OfflinePackageItemStatusEntity(
    @ColumnInfo(name = "incident_id")
    val incidentId: String,
    @ColumnInfo(name = "police_phone_id")
    val policePhoneId: String,
    @ColumnInfo(name = "manifest_id")
    val manifestId: String,
    @ColumnInfo(name = "item_key")
    val itemKey: String,
    @ColumnInfo(name = "manifest_version")
    val manifestVersion: Int,
    @ColumnInfo(name = "item_type")
    val itemType: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "source_version")
    val sourceVersion: Int,
    @ColumnInfo(name = "source_hash")
    val sourceHash: String,
    @ColumnInfo(name = "bytes_total")
    val bytesTotal: Long?,
    @ColumnInfo(name = "bytes_downloaded")
    val bytesDownloaded: Long?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
