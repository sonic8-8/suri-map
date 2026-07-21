package com.surimap.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface IncidentSummaryDao {
    @Query(
        """
        SELECT * FROM incident_summary
        WHERE account_id = :accountId
        ORDER BY display_order ASC
        """
    )
    suspend fun findByAccountId(accountId: String): List<IncidentSummaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(incidents: List<IncidentSummaryEntity>)

    @Query("DELETE FROM incident_summary WHERE account_id = :accountId")
    suspend fun deleteByAccountId(accountId: String)

    @Query("DELETE FROM incident_summary WHERE incident_id = :incidentId")
    suspend fun deleteByIncidentId(incidentId: String)

    @Transaction
    suspend fun replaceForAccount(accountId: String, incidents: List<IncidentSummaryEntity>) {
        deleteByAccountId(accountId)
        if (incidents.isNotEmpty()) {
            upsertAll(incidents)
        }
    }
}
