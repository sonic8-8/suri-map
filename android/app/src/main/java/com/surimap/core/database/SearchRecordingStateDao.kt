package com.surimap.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SearchRecordingStateDao {
    @Query(
        """
        SELECT * FROM search_recording_state
        WHERE account_id = :accountId AND incident_id = :incidentId
        LIMIT 1
        """
    )
    suspend fun find(accountId: String, incidentId: String): SearchRecordingStateEntity?

    @Query(
        """
        SELECT * FROM search_recording_state
        WHERE account_id = :accountId
          AND lifecycle_status IN ('ACTIVE', 'PAUSED')
        ORDER BY updated_at DESC
        LIMIT 1
        """
    )
    suspend fun findRecoverableByAccountId(accountId: String): SearchRecordingStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SearchRecordingStateEntity)

    @Query("DELETE FROM search_recording_state WHERE account_id = :accountId")
    suspend fun deleteByAccountId(accountId: String)

    @Query(
        """
        DELETE FROM search_recording_state
        WHERE account_id = :accountId AND incident_id = :incidentId
        """
    )
    suspend fun delete(accountId: String, incidentId: String)

    @Query("DELETE FROM search_recording_state WHERE incident_id = :incidentId")
    suspend fun deleteByIncidentId(incidentId: String)
}
