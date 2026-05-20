package com.surimap.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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
          AND NOT (
            idempotency_status = 'FAILED_RETRYABLE'
            AND last_error IN ('police_phone_required', 'http_401')
          )
          AND (
            (
              incident_closed_at IS NULL
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
              AND clock_synced_at > 0
              AND ABS(client_requested_at - clock_synced_at) <= :maxClockSyncAgeMs
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
        maxClockSyncAgeMs: Long
    ): List<OutboxEntity>

    @Query(
        """
        UPDATE android_outbox_row
        SET idempotency_status = 'PENDING',
            local_mirror_status = 'PENDING_SEND',
            next_attempt_at = :now
        WHERE incident_id = :incidentId
          AND police_phone_id = :policePhoneId
          AND idempotency_status = 'FAILED_RETRYABLE'
          AND local_mirror_status = 'FAILED'
          AND last_error IN ('police_phone_required', 'http_401')
          AND incident_closed_at IS NULL
          AND clock_synced_at > 0
          AND ABS(client_requested_at - clock_synced_at) <= :maxClockSyncAgeMs
        """
    )
    suspend fun requeueAccessRepairRequiredRows(
        incidentId: String,
        policePhoneId: String,
        now: Long,
        maxClockSyncAgeMs: Long
    ): Int

    @Query(
        """
        UPDATE android_outbox_row
        SET idempotency_status = 'FAILED_RETRYABLE',
            local_mirror_status = 'FAILED',
            next_attempt_at = :now,
            last_error = COALESCE(last_error, 'worker_interrupted')
        WHERE incident_id = :incidentId
          AND police_phone_id = :policePhoneId
          AND idempotency_status = 'SENDING'
          AND local_mirror_status = 'SENDING'
          AND COALESCE(first_attempt_at, client_requested_at) <= :staleBefore
          AND (
            incident_closed_at IS NULL
            OR client_requested_at <= incident_closed_at
          )
        """
    )
    suspend fun requeueStaleSendingRows(
        incidentId: String,
        policePhoneId: String,
        now: Long,
        staleBefore: Long
    ): Int

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
        UPDATE local_marker
        SET sync_status = :syncStatus,
            updated_at_millis = :updatedAtMillis
        WHERE outbox_id = :outboxId
        """
    )
    suspend fun markLocalMarkerSyncStatusByOutboxId(
        outboxId: String,
        syncStatus: String,
        updatedAtMillis: Long
    ): Int

    @Query("DELETE FROM local_write_draft WHERE draftId = :outboxId")
    suspend fun deleteLocalWriteDraftByOutboxId(outboxId: String): Int

    @Query(
        """
        SELECT
          COALESCE(SUM(CASE WHEN idempotency_status IN ('PENDING', 'SENDING') THEN 1 ELSE 0 END), 0)
            AS pending_count,
          COALESCE(SUM(CASE WHEN idempotency_status = 'FAILED_RETRYABLE' THEN 1 ELSE 0 END), 0)
            AS retryable_count,
          COALESCE(SUM(CASE WHEN idempotency_status = 'FAILED_FINAL' THEN 1 ELSE 0 END), 0)
            AS final_failed_count,
          MIN(
            CASE
              WHEN idempotency_status IN ('PENDING', 'SENDING', 'FAILED_RETRYABLE')
              THEN client_requested_at
              ELSE NULL
            END
          ) AS oldest_pending_client_requested_at
        FROM android_outbox_row
        WHERE incident_id = :incidentId
          AND police_phone_id = :policePhoneId
          AND idempotency_status NOT IN ('ACKED', 'PURGED')
        """
    )
    suspend fun statusSummary(
        incidentId: String,
        policePhoneId: String
    ): OutboxStatusSummary

    @Query(
        """
        SELECT
          COALESCE(SUM(CASE WHEN idempotency_status IN ('PENDING', 'SENDING') THEN 1 ELSE 0 END), 0)
            AS pending_count,
          COALESCE(SUM(CASE WHEN idempotency_status = 'FAILED_RETRYABLE' THEN 1 ELSE 0 END), 0)
            AS retryable_count,
          COALESCE(SUM(CASE WHEN idempotency_status = 'FAILED_FINAL' THEN 1 ELSE 0 END), 0)
            AS final_failed_count,
          MIN(
            CASE
              WHEN idempotency_status IN ('PENDING', 'SENDING', 'FAILED_RETRYABLE')
              THEN client_requested_at
              ELSE NULL
            END
          ) AS oldest_pending_client_requested_at
        FROM android_outbox_row
        WHERE incident_id = :incidentId
          AND police_phone_id = :policePhoneId
          AND idempotency_status NOT IN ('ACKED', 'PURGED')
        """
    )
    fun observeStatusSummary(
        incidentId: String,
        policePhoneId: String
    ): Flow<OutboxStatusSummary>

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
