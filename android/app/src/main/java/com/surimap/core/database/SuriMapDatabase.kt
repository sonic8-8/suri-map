package com.surimap.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [OutboxEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SuriMapDatabase : RoomDatabase()
