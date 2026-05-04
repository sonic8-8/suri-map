package com.surimap.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_write_draft",
    indices = [
        Index(value = ["incidentId"]),
        Index(value = ["operationId"], unique = true),
        Index(value = ["entityType", "entityId"]),
    ],
)
data class LocalWriteDraftEntity(
    @PrimaryKey val draftId: String,
    val incidentId: String,
    val operationId: String,
    val entityType: String,
    val entityId: String? = null,
    val payload: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
