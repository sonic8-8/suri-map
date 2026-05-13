package com.surimap.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        OutboxEntity::class,
        SyncStatusEntity::class,
        LocalWriteDraftEntity::class,
        OfflinePackageInstallationEntity::class,
        OfflinePackageItemStatusEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class SuriMapDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao

    abstract fun syncStatusDao(): SyncStatusDao

    abstract fun localWriteDraftDao(): LocalWriteDraftDao

    abstract fun offlinePackageInstallationDao(): OfflinePackageInstallationDao

    abstract fun offlinePackageItemStatusDao(): OfflinePackageItemStatusDao
}
