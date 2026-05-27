package com.surimap.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SearchMapResponseCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchMapResponseCacheEntity)

    @Query(
        """
        SELECT * FROM search_map_response_cache
        WHERE incident_id = :incidentId
          AND op_id = :opId
          AND police_phone_id = :policePhoneId
          AND source = :source
        LIMIT 1
        """
    )
    suspend fun find(
        incidentId: String,
        opId: String,
        policePhoneId: String,
        source: String
    ): SearchMapResponseCacheEntity?

    @Query(
        """
        SELECT * FROM search_map_response_cache
        WHERE incident_id = :incidentId
          AND op_id = :opId
          AND police_phone_id = :policePhoneId
        """
    )
    suspend fun findByContext(
        incidentId: String,
        opId: String,
        policePhoneId: String
    ): List<SearchMapResponseCacheEntity>
}
