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
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6
                )
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

    internal val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_marker` (
                        `local_marker_id` TEXT NOT NULL,
                        `outbox_id` TEXT NOT NULL,
                        `operation_id` TEXT NOT NULL,
                        `incident_id` TEXT NOT NULL,
                        `op_id` TEXT NOT NULL,
                        `police_phone_id` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `support_request_type` TEXT,
                        `memo` TEXT,
                        `lon` REAL NOT NULL,
                        `lat` REAL NOT NULL,
                        `sync_status` TEXT NOT NULL,
                        `created_at_millis` INTEGER NOT NULL,
                        `updated_at_millis` INTEGER NOT NULL,
                        PRIMARY KEY(`local_marker_id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `idx_local_marker_pending`
                    ON `local_marker` (`incident_id`, `police_phone_id`, `sync_status`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `ux_local_marker_operation`
                    ON `local_marker` (`operation_id`)
                    """.trimIndent()
                )
            }
        }

    internal val MIGRATION_4_5 =
        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `search_map_response_cache` (
                        `incident_id` TEXT NOT NULL,
                        `op_id` TEXT NOT NULL,
                        `police_phone_id` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `body_hash` TEXT NOT NULL,
                        `body_json` TEXT NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`incident_id`, `op_id`, `police_phone_id`, `source`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `idx_search_map_response_cache_context`
                    ON `search_map_response_cache` (`incident_id`, `op_id`, `police_phone_id`)
                    """.trimIndent()
                )
            }
        }

    internal val MIGRATION_5_6 =
        object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE `search_map_response_cache`
                    ADD COLUMN `source_revision` TEXT NOT NULL DEFAULT ''
                    """.trimIndent()
                )
            }
        }

    private const val DATABASE_NAME = "suri-map.db"
}
