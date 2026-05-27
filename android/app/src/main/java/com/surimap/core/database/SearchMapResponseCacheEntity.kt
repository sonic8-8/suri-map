package com.surimap.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "search_map_response_cache",
    primaryKeys = ["incident_id", "op_id", "police_phone_id", "source"],
    indices = [
        Index(
            value = ["incident_id", "op_id", "police_phone_id"],
            name = "idx_search_map_response_cache_context"
        )
    ]
)
data class SearchMapResponseCacheEntity(
    @ColumnInfo(name = "incident_id")
    val incidentId: String,
    @ColumnInfo(name = "op_id")
    val opId: String,
    @ColumnInfo(name = "police_phone_id")
    val policePhoneId: String,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "body_hash")
    val bodyHash: String,
    @ColumnInfo(name = "body_json")
    val bodyJson: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
