package com.surimap.core.database

import androidx.room.Room
import com.surimap.core.sync.DependencyGroup
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SuriMapDatabaseTest {
    private lateinit var database: SuriMapDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            SuriMapDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun roomSchemaIncludesS6LocalTables() {
        val tableNames = mutableSetOf<String>()
        val cursor = database.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table'"
        )

        cursor.use {
            while (it.moveToNext()) {
                tableNames += it.getString(0)
            }
        }

        assertTrue(tableNames.contains("android_outbox_row"))
        assertTrue(tableNames.contains("android_sync_status"))
        assertTrue(tableNames.contains("local_write_draft"))
        assertFalse(tableNames.contains("sync_status"))
    }

    @Test
    fun outboxSchemaMatchesS6LocalContract() {
        val columns = tableColumns("android_outbox_row")

        assertEquals(
            listOf(
                ColumnSpec("outbox_id", nullable = false, primaryKey = true),
                ColumnSpec("client_operation_id", nullable = false),
                ColumnSpec("incident_id", nullable = true),
                ColumnSpec("op_id", nullable = true),
                ColumnSpec("police_phone_id", nullable = false),
                ColumnSpec("dependency_group", nullable = false),
                ColumnSpec("parent_client_operation_id", nullable = true),
                ColumnSpec("sequence", nullable = false),
                ColumnSpec("request_method", nullable = false),
                ColumnSpec("request_path", nullable = false),
                ColumnSpec("payload_json", nullable = false),
                ColumnSpec("request_body_hash", nullable = false),
                ColumnSpec("idempotency_key", nullable = false),
                ColumnSpec("idempotency_status", nullable = false),
                ColumnSpec("local_mirror_status", nullable = false),
                ColumnSpec("attempt_count", nullable = false),
                ColumnSpec("first_attempt_at", nullable = true),
                ColumnSpec("next_attempt_at", nullable = true),
                ColumnSpec("client_requested_at", nullable = false),
                ColumnSpec("clock_offset_ms", nullable = false),
                ColumnSpec("clock_synced_at", nullable = false),
                ColumnSpec("server_ack_ts", nullable = true),
                ColumnSpec("incident_closed_at", nullable = true),
                ColumnSpec("last_error", nullable = true)
            ),
            columns
        )

        assertFalse(columns.any { it.name == "policePhoneId" })
        assertFalse(columns.any { it.name == "operationId" })
        assertFalse(columns.any { it.name == "method" })
        assertFalse(columns.any { it.name == "endpoint" })
        assertFalse(columns.any { it.name == "bodyHash" })
        assertFalse(columns.any { it.name == "status" })
        assertFalse(columns.any { it.name == "harnessStatus" })
    }

    @Test
    fun outboxIndicesMatchS6LocalContract() {
        assertEquals(
            IndexSpec(unique = false, columns = listOf("idempotency_status", "next_attempt_at")),
            indexSpec("idx_android_outbox_status_next_attempt")
        )
        assertEquals(
            IndexSpec(
                unique = false,
                columns = listOf("incident_id", "police_phone_id", "dependency_group", "sequence")
            ),
            indexSpec("idx_android_outbox_replay_order")
        )
        assertEquals(
            IndexSpec(unique = false, columns = listOf("incident_id", "police_phone_id")),
            indexSpec("idx_android_outbox_incident_police_phone")
        )
        assertEquals(
            IndexSpec(unique = true, columns = listOf("idempotency_key")),
            indexSpec("ux_android_outbox_idempotency_key")
        )

        val indexNames = indexNames("android_outbox_row")
        assertFalse(indexNames.contains("index_android_outbox_row_operationId"))
        assertFalse(indexNames.contains("index_android_outbox_row_idempotencyKey"))
        assertFalse(
            indexNames.contains("index_android_outbox_row_incidentId_policePhoneId_status")
        )
        assertFalse(indexNames.contains("index_android_outbox_row_dependencyGroup_sequence"))
    }

    @Test
    fun syncStatusSchemaMatchesS6LocalContract() {
        assertEquals(
            listOf(
                ColumnSpec("police_phone_id", nullable = false, primaryKey = true),
                ColumnSpec("incident_id", nullable = true),
                ColumnSpec("pending_count", nullable = false),
                ColumnSpec("retryable_count", nullable = false),
                ColumnSpec("final_failed_count", nullable = false),
                ColumnSpec("last_successful_sync_at", nullable = true),
                ColumnSpec("offline_since", nullable = true),
                ColumnSpec("warning_codes", nullable = false)
            ),
            tableColumns("android_sync_status")
        )

        assertEquals(
            IndexSpec(unique = false, columns = listOf("incident_id")),
            indexSpec("android_sync_status", "idx_android_sync_status_incident")
        )
    }

    @Test
    fun syncStatusIsKeyedByPolicePhoneAndAllowsSharedIncident() = runBlocking {
        val incidentId = "inc-precinct-first-001"

        database.syncStatusDao().upsert(
            SyncStatusEntity(
                policePhoneId = "dev-precinct-phone-01",
                incidentId = incidentId,
                pendingCount = 1,
                retryableCount = 0,
                finalFailedCount = 0,
                warningCodes = "OFFLINE_RECORDING"
            )
        )
        database.syncStatusDao().upsert(
            SyncStatusEntity(
                policePhoneId = "dev-precinct-car-01",
                incidentId = incidentId,
                pendingCount = 2,
                retryableCount = 1,
                finalFailedCount = 0,
                warningCodes = "OUTBOX_BACKLOG"
            )
        )

        val statuses = database.syncStatusDao().findByIncidentId(incidentId)

        assertEquals(2, statuses.size)
        assertEquals(
            setOf("dev-precinct-phone-01", "dev-precinct-car-01"),
            statuses.map { it.policePhoneId }.toSet()
        )
    }

    @Test
    fun dependencyGroupsUseS6OrderingPolicyNames() {
        val names = DependencyGroup.entries.map { it.name }.toSet()

        assertTrue(names.contains("PACKAGE_INSTALLATION"))
        assertFalse(names.contains("PACKAGE_STATUS"))
    }

    @Test
    fun offlinePackageInstallationSchemaStoresKnownManifestRevisionPerPolicePhone() = runBlocking {
        assertEquals(
            listOf(
                ColumnSpec("incident_id", nullable = false, primaryKey = true),
                ColumnSpec("police_phone_id", nullable = false, primaryKey = true),
                ColumnSpec("manifest_id", nullable = false),
                ColumnSpec("manifest_version", nullable = false),
                ColumnSpec("status", nullable = false),
                ColumnSpec("total_items", nullable = false),
                ColumnSpec("completed_items", nullable = false),
                ColumnSpec("failed_items", nullable = false),
                ColumnSpec("version", nullable = false),
                ColumnSpec("ready_for_offline_use", nullable = false),
                ColumnSpec("updated_at", nullable = false)
            ),
            tableColumns("offline_package_installation_status")
        )

        database.offlinePackageInstallationDao().upsert(
            OfflinePackageInstallationEntity(
                incidentId = "inc-precinct-first-001",
                policePhoneId = "phone-precinct-001",
                manifestId = "pkg-precinct-first-rev-18",
                manifestVersion = 18,
                status = "READY",
                totalItems = 7,
                completedItems = 7,
                failedItems = 0,
                version = 3,
                readyForOfflineUse = true,
                updatedAt = 1_000L
            )
        )

        val status = database.offlinePackageInstallationDao().find(
            incidentId = "inc-precinct-first-001",
            policePhoneId = "phone-precinct-001"
        )

        assertEquals(18, status!!.manifestVersion)
        assertTrue(status.readyForOfflineUse)
    }

    @Test
    fun offlinePackageItemStatusSchemaStoresPerManifestProgress() = runBlocking {
        assertEquals(
            listOf(
                ColumnSpec("incident_id", nullable = false, primaryKey = true),
                ColumnSpec("police_phone_id", nullable = false, primaryKey = true),
                ColumnSpec("manifest_id", nullable = false, primaryKey = true),
                ColumnSpec("item_key", nullable = false, primaryKey = true),
                ColumnSpec("manifest_version", nullable = false),
                ColumnSpec("item_type", nullable = false),
                ColumnSpec("status", nullable = false),
                ColumnSpec("source_version", nullable = false),
                ColumnSpec("source_hash", nullable = false),
                ColumnSpec("bytes_total", nullable = true),
                ColumnSpec("bytes_downloaded", nullable = true),
                ColumnSpec("updated_at", nullable = false)
            ),
            tableColumns("offline_package_item_status")
        )

        database.offlinePackageItemStatusDao().upsertAll(
            listOf(
                OfflinePackageItemStatusEntity(
                    incidentId = "inc-precinct-first-001",
                    policePhoneId = "phone-precinct-001",
                    manifestId = "pkg-precinct-first-rev-18",
                    itemKey = "incident-meta",
                    manifestVersion = 18,
                    itemType = "INCIDENT_META",
                    status = "DOWNLOADED",
                    sourceVersion = 7,
                    sourceHash = "sha256:incident",
                    bytesTotal = null,
                    bytesDownloaded = null,
                    updatedAt = 1_000L
                ),
                OfflinePackageItemStatusEntity(
                    incidentId = "inc-precinct-first-001",
                    policePhoneId = "phone-precinct-001",
                    manifestId = "pkg-precinct-first-rev-18",
                    itemKey = "tile-1",
                    manifestVersion = 18,
                    itemType = "TILE",
                    status = "PENDING",
                    sourceVersion = 18,
                    sourceHash = "sha256:tile",
                    bytesTotal = 100,
                    bytesDownloaded = 40,
                    updatedAt = 1_100L
                ),
                OfflinePackageItemStatusEntity(
                    incidentId = "inc-precinct-first-001",
                    policePhoneId = "phone-precinct-002",
                    manifestId = "pkg-precinct-first-rev-18",
                    itemKey = "tile-1",
                    manifestVersion = 18,
                    itemType = "TILE",
                    status = "DOWNLOADED",
                    sourceVersion = 18,
                    sourceHash = "sha256:tile",
                    bytesTotal = 100,
                    bytesDownloaded = 100,
                    updatedAt = 1_200L
                )
            )
        )

        val items = database.offlinePackageItemStatusDao().findByManifest(
            incidentId = "inc-precinct-first-001",
            policePhoneId = "phone-precinct-001",
            manifestId = "pkg-precinct-first-rev-18"
        )

        assertEquals(listOf("incident-meta", "tile-1"), items.map { it.itemKey })
        assertEquals(40L, items.single { it.itemKey == "tile-1" }.bytesDownloaded)
    }

    private fun tableColumns(tableName: String): List<ColumnSpec> {
        val columns = mutableListOf<ColumnSpec>()
        val cursor = database.openHelper.readableDatabase.query("PRAGMA table_info($tableName)")

        cursor.use {
            while (it.moveToNext()) {
                columns += ColumnSpec(
                    name = it.getString(it.getColumnIndexOrThrow("name")),
                    nullable = it.getInt(it.getColumnIndexOrThrow("notnull")) == 0,
                    primaryKey = it.getInt(it.getColumnIndexOrThrow("pk")) > 0
                )
            }
        }

        return columns
    }

    private fun indexNames(tableName: String): Set<String> {
        val names = mutableSetOf<String>()
        val cursor = database.openHelper.readableDatabase.query("PRAGMA index_list($tableName)")

        cursor.use {
            while (it.moveToNext()) {
                names += it.getString(it.getColumnIndexOrThrow("name"))
            }
        }

        return names
    }

    private fun indexSpec(indexName: String): IndexSpec = indexSpec("android_outbox_row", indexName)

    private fun indexSpec(tableName: String, indexName: String): IndexSpec {
        var unique = false
        val indexListCursor = database.openHelper.readableDatabase.query(
            "PRAGMA index_list($tableName)"
        )

        indexListCursor.use {
            while (it.moveToNext()) {
                if (it.getString(it.getColumnIndexOrThrow("name")) == indexName) {
                    unique = it.getInt(it.getColumnIndexOrThrow("unique")) == 1
                }
            }
        }

        val columns = mutableListOf<String>()
        val indexInfoCursor = database.openHelper.readableDatabase.query(
            "PRAGMA index_info($indexName)"
        )

        indexInfoCursor.use {
            while (it.moveToNext()) {
                columns += it.getString(it.getColumnIndexOrThrow("name"))
            }
        }

        return IndexSpec(unique = unique, columns = columns)
    }

    private data class ColumnSpec(val name: String, val nullable: Boolean, val primaryKey: Boolean = false)

    private data class IndexSpec(val unique: Boolean, val columns: List<String>)
}
