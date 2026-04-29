package com.surimap.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val id: String,
    val type: String,
    val payload: String,
    val createdAtMillis: Long,
)

