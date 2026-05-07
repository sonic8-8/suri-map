package com.surimap.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncStatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncStatusEntity)

    @Query("SELECT * FROM android_sync_status WHERE incident_id = :incidentId")
    suspend fun findByIncidentId(incidentId: String): List<SyncStatusEntity>
}
