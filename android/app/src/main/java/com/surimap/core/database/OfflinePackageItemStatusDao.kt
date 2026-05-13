package com.surimap.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface OfflinePackageItemStatusDao {
    @Upsert
    suspend fun upsertAll(entities: List<OfflinePackageItemStatusEntity>)

    @Query(
        """
        SELECT *
        FROM offline_package_item_status
        WHERE incident_id = :incidentId
          AND police_phone_id = :policePhoneId
          AND manifest_id = :manifestId
        ORDER BY
          CASE item_type
            WHEN 'INCIDENT_META' THEN 0
            WHEN 'MISSING_PERSON_CACHE' THEN 1
            WHEN 'OP_LIST' THEN 2
            WHEN 'ASSIGNED_AREA' THEN 3
            WHEN 'INITIAL_MARKER' THEN 4
            WHEN 'OVERALL_SEARCH_AREA' THEN 5
            WHEN 'TILE' THEN 6
            ELSE 7
          END,
          item_key
        """
    )
    suspend fun findByManifest(
        incidentId: String,
        policePhoneId: String,
        manifestId: String
    ): List<OfflinePackageItemStatusEntity>
}
