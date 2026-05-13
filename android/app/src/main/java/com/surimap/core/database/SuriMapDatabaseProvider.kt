package com.surimap.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object SuriMapDatabaseProvider {
    @Volatile
    private var instance: SuriMapDatabase? = null

    fun database(context: Context): SuriMapDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SuriMapDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { database ->
                instance = database
            }
        }
    }

    private val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `offline_package_installation_status` (
                        `incident_id` TEXT NOT NULL,
                        `police_phone_id` TEXT NOT NULL,
                        `manifest_id` TEXT NOT NULL,
                        `manifest_version` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `total_items` INTEGER NOT NULL,
                        `completed_items` INTEGER NOT NULL,
                        `failed_items` INTEGER NOT NULL,
                        `version` INTEGER NOT NULL,
                        `ready_for_offline_use` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`incident_id`, `police_phone_id`)
                    )
                    """.trimIndent()
                )
            }
        }

    private val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `offline_package_item_status` (
                        `incident_id` TEXT NOT NULL,
                        `police_phone_id` TEXT NOT NULL,
                        `manifest_id` TEXT NOT NULL,
                        `item_key` TEXT NOT NULL,
                        `manifest_version` INTEGER NOT NULL,
                        `item_type` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `source_version` INTEGER NOT NULL,
                        `source_hash` TEXT NOT NULL,
                        `bytes_total` INTEGER,
                        `bytes_downloaded` INTEGER,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`incident_id`, `police_phone_id`, `manifest_id`, `item_key`)
                    )
                    """.trimIndent()
                )
            }
        }

    private const val DATABASE_NAME = "suri-map.db"
}
