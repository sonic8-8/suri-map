package com.surimap.core.database

import android.content.Context
import androidx.room.Room

object SuriMapDatabaseProvider {
    @Volatile
    private var instance: SuriMapDatabase? = null

    fun database(context: Context): SuriMapDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SuriMapDatabase::class.java,
                DATABASE_NAME
            ).build().also { database ->
                instance = database
            }
        }
    }

    private const val DATABASE_NAME = "suri-map.db"
}
