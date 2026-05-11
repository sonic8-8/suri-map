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
          AND (
            (
              incident_closed_at IS NULL
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
              AND clock_synced_at >= :minClockSyncedAt
            )
            OR (
              incident_closed_at IS NOT NULL
              AND client_requested_at <= incident_closed_at
              AND next_attempt_at IS NOT NULL
              AND next_attempt_at <= :now
            )
          )
        ORDER BY sequence ASC
        """
    )
    suspend fun findReplayCandidates(
        incidentId: String,
        policePhoneId: String,
        now: Long,
        minClockSyncedAt: Long
    ): List<OutboxEntity>

    @Query(
        """
        SELECT COUNT(*) FROM android_outbox_row
        WHERE incident_id = :incidentId
          AND police_phone_id = :policePhoneId
          AND (:opId IS NULL OR op_id = :opId)
          AND sequence < :sequence
          AND dependency_group IN ('PATH', 'MARKER', 'PHOTO', 'HANDOVER_MEMO')
          AND idempotency_status NOT IN ('ACKED', 'FAILED_FINAL', 'PURGED')
        """
    )
    suspend fun countUnresolvedLowerSequenceSourceRows(
        incidentId: String,
        policePhoneId: String,
        opId: String?,
        sequence: Long
    ): Int

    @Query("SELECT * FROM android_outbox_row WHERE client_operation_id = :operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): OutboxEntity?

    @Query("SELECT COUNT(*) FROM android_outbox_row WHERE idempotency_status = :status")
    suspend fun countByStatus(status: String): Int

    @Query(
        """
        SELECT * FROM android_outbox_row
        WHERE incident_id = :incidentId
        ORDER BY sequence ASC, outbox_id ASC
        """
    )
    suspend fun findByIncidentId(incidentId: String): List<OutboxEntity>

    @Query(
        """
        SELECT * FROM android_outbox_row
        WHERE incident_id = :incidentId
          AND idempotency_status IN ('ACKED', 'PURGED')
          AND request_path != '/_local/incident-closed'
        ORDER BY sequence ASC, outbox_id ASC
        """
    )
    suspend fun findPurgeAccountingRowsByIncidentId(incidentId: String): List<OutboxEntity>

    @Query(
        """
        SELECT MAX(incident_closed_at) FROM android_outbox_row
        WHERE incident_id = :incidentId
          AND police_phone_id = :policePhoneId
          AND incident_closed_at IS NOT NULL
        """
    )
    suspend fun findIncidentClosedAt(
        incidentId: String,
        policePhoneId: String
    ): Long?

    @Query(
        """
        UPDATE android_outbox_row
        SET idempotency_status = 'PURGED',
            local_mirror_status = 'PURGED'
        WHERE outbox_id = :outboxId
          AND idempotency_status = 'ACKED'
        """
    )
    suspend fun markAckedPurged(outboxId: String): Int

    @Query(
        """
        UPDATE android_outbox_row
        SET incident_closed_at = :closedAt
        WHERE incident_id = :incidentId
          AND police_phone_id = :policePhoneId
          AND idempotency_status != 'PURGED'
        """
    )
    suspend fun markIncidentClosed(
        incidentId: String,
        policePhoneId: String,
        closedAt: Long
    ): Int

    @Query(
        """
        UPDATE android_outbox_row
        SET idempotency_status = 'FAILED_FINAL',
            local_mirror_status = 'FAILED',
            next_attempt_at = NULL,
            last_error = 'post_close_requeue_rejected'
        WHERE incident_id = :incidentId
          AND police_phone_id = :policePhoneId
          AND incident_closed_at IS NOT NULL
          AND client_requested_at > incident_closed_at
          AND idempotency_status IN ('PENDING', 'SENDING', 'FAILED_RETRYABLE')
        """
    )
    suspend fun rejectPostCloseRows(
        incidentId: String,
        policePhoneId: String
    ): Int
}
