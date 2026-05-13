package com.surimap.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocalMarkerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalMarkerEntity)

    @Query("SELECT * FROM local_marker WHERE outbox_id = :outboxId LIMIT 1")
    suspend fun findByOutboxId(outboxId: String): LocalMarkerEntity?

    @Query(
        """
        SELECT local_marker.* FROM local_marker
        INNER JOIN android_outbox_row
          ON android_outbox_row.outbox_id = local_marker.outbox_id
        WHERE local_marker.incident_id = :incidentId
          AND local_marker.police_phone_id = :policePhoneId
          AND android_outbox_row.idempotency_status NOT IN ('ACKED', 'PURGED')
          AND android_outbox_row.local_mirror_status IN ('PENDING_LOCAL', 'PENDING_SEND', 'SENDING', 'FAILED')
        ORDER BY local_marker.created_at_millis ASC, local_marker.local_marker_id ASC
        """
    )
    suspend fun findPendingByIncidentAndPolicePhone(
        incidentId: String,
        policePhoneId: String
    ): List<LocalMarkerEntity>

    @Query("DELETE FROM local_marker WHERE local_marker_id = :localMarkerId")
    suspend fun deleteById(localMarkerId: String): Int
}
