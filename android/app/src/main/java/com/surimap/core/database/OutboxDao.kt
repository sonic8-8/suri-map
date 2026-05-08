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

    @Query("SELECT * FROM android_outbox_row WHERE idempotency_key = :idempotencyKey LIMIT 1")
    suspend fun findByIdempotencyKey(idempotencyKey: String): OutboxEntity?

    @Query("SELECT COUNT(*) FROM android_outbox_row WHERE idempotency_key = :idempotencyKey")
    suspend fun countByIdempotencyKey(idempotencyKey: String): Int

    @Query(
        """
        SELECT * FROM android_outbox_row
        WHERE incident_id = :incidentId
          AND police_phone_id = :policePhoneId
          AND idempotency_status IN ('PENDING', 'FAILED_RETRYABLE')
          AND local_mirror_status IN ('PENDING_SEND', 'FAILED')
          AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
          AND clock_synced_at >= :minClockSyncedAt
          AND (incident_closed_at IS NULL OR client_requested_at <= incident_closed_at)
        ORDER BY sequence ASC
        """
    )
    suspend fun findReplayCandidates(
        incidentId: String,
        policePhoneId: String,
        now: Long,
        minClockSyncedAt: Long
    ): List<OutboxEntity>

    @Query("SELECT * FROM android_outbox_row WHERE client_operation_id = :operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): OutboxEntity?

    @Query("SELECT COUNT(*) FROM android_outbox_row WHERE idempotency_status = :status")
    suspend fun countByStatus(status: String): Int
}
