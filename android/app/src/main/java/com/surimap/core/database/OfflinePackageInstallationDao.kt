package com.surimap.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflinePackageInstallationDao {
    @Upsert
    suspend fun upsert(entity: OfflinePackageInstallationEntity)

    @Query(
        """
        SELECT *
        FROM offline_package_installation_status
        WHERE incident_id = :incidentId
          AND police_phone_id = :policePhoneId
        """
    )
    suspend fun find(incidentId: String, policePhoneId: String): OfflinePackageInstallationEntity?

    @Query(
        """
        SELECT *
        FROM offline_package_installation_status
        WHERE incident_id = :incidentId
          AND police_phone_id = :policePhoneId
        """
    )
    fun observe(incidentId: String, policePhoneId: String): Flow<OfflinePackageInstallationEntity?>
}
