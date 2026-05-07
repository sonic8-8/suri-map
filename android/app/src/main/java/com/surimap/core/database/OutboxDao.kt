package com.surimap.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OutboxEntity)

    @Query("SELECT * FROM android_outbox_row WHERE outbox_id = :outboxId")
    suspend fun findById(outboxId: String): OutboxEntity?

    @Query("SELECT COUNT(*) FROM android_outbox_row WHERE idempotency_status = :status")
    suspend fun countByStatus(status: String): Int
}
