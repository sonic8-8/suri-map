package com.surimap.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocalWriteDraftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalWriteDraftEntity)

    @Query("SELECT * FROM local_write_draft WHERE draftId = :draftId")
    suspend fun findById(draftId: String): LocalWriteDraftEntity?
}
