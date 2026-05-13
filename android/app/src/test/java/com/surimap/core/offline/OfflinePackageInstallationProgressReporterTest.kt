package com.surimap.core.offline

import com.surimap.core.sync.DependencyGroup
import com.surimap.core.sync.EnqueueResult
import com.surimap.core.sync.HarnessSyncStatus
import com.surimap.core.sync.LocalWriteOperation
import com.surimap.core.sync.OutboxStatus
import com.surimap.core.sync.SyncClient
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflinePackageInstallationProgressReporterTest {

    @Test
    fun completedPackageItemsReportReadyInstallationThroughOutbox() = runBlocking {
        val syncClient = CapturingSyncClient()
        val reporter =
            OfflinePackageInstallationProgressReporter(
                repository = OfflinePackageRepository(syncClient = syncClient)
            )

        reporter.report(
            progressCommand(
                items =
                listOf(
                    item("incident-meta", "INCIDENT_META", "DOWNLOADED"),
                    item("tile-1", "TILE", "SKIPPED")
                )
            )
        )

        val operation = syncClient.lastOperation!!
        assertEquals(DependencyGroup.PACKAGE_INSTALLATION, operation.dependencyGroup)
        assertEquals("POST", operation.method)
        assertEquals("/api/incidents/$INCIDENT_ID/offline-package/installations", operation.endpoint)
        assertEquals(
            """{"policePhoneId":"$POLICE_PHONE_ID","manifestId":"$MANIFEST_ID","manifestVersion":18,"status":"READY","totalItems":2,"completedItems":2,"failedItems":0,"version":7,"clientTs":"2026-05-11T06:00:00Z","readyForOfflineUse":true,"failedItemKeys":[],"sequence":30,"clockOffsetMs":50}""",
            operation.payload
        )
    }

    @Test
    fun failedAndPendingPackageItemsReportPartialInstallation() = runBlocking {
        val syncClient = CapturingSyncClient()
        val reporter =
            OfflinePackageInstallationProgressReporter(
                repository = OfflinePackageRepository(syncClient = syncClient)
            )

        reporter.report(
            progressCommand(
                items =
                listOf(
                    item("incident-meta", "INCIDENT_META", "DOWNLOADED"),
                    item("marker-1", "INITIAL_MARKER", "FAILED"),
                    item("tile-1", "TILE", "PENDING", bytesTotal = 100, bytesDownloaded = 40)
                )
            )
        )

        val operation = syncClient.lastOperation!!
        assertEquals(
            """{"policePhoneId":"$POLICE_PHONE_ID","manifestId":"$MANIFEST_ID","manifestVersion":18,"status":"PARTIAL","totalItems":3,"completedItems":1,"failedItems":1,"version":7,"clientTs":"2026-05-11T06:00:00Z","readyForOfflineUse":false,"failedItemKeys":["marker-1"],"sequence":30,"clockOffsetMs":50}""",
            operation.payload
        )
    }

    @Test
    fun allFailedPackageItemsReportFailedInstallation() {
        val aggregate =
            OfflinePackageInstallationProgressReporter.aggregate(
                listOf(
                    item("marker-1", "INITIAL_MARKER", "FAILED"),
                    item("tile-1", "TILE", "FAILED")
                )
            )

        assertEquals("FAILED", aggregate.status)
        assertEquals(false, aggregate.readyForOfflineUse)
        assertEquals(2, aggregate.failedItems)
        assertEquals(listOf("marker-1", "tile-1"), aggregate.failedItemKeys)
    }

    @Test
    fun emptyPackageItemsReportNotStartedInstallation() {
        val aggregate = OfflinePackageInstallationProgressReporter.aggregate(emptyList())

        assertEquals("NOT_STARTED", aggregate.status)
        assertEquals(0, aggregate.totalItems)
        assertEquals(0, aggregate.completedItems)
        assertEquals(0, aggregate.failedItems)
        assertEquals(false, aggregate.readyForOfflineUse)
    }

    private fun progressCommand(
        items: List<OfflinePackageItemStatus>
    ): OfflinePackageInstallationProgressCommand =
        OfflinePackageInstallationProgressCommand(
            operationId = "op-package-install-001",
            incidentId = INCIDENT_ID,
            policePhoneId = POLICE_PHONE_ID,
            idempotencyKey = "idem-package-install-001",
            sequence = 30,
            manifestId = MANIFEST_ID,
            manifestVersion = 18,
            version = 7,
            clientTs = CLIENT_TS,
            clockOffsetMs = 50,
            clockSyncedAt = CLOCK_SYNCED_AT,
            items = items
        )

    private fun item(
        itemKey: String,
        itemType: String,
        status: String,
        bytesTotal: Long? = null,
        bytesDownloaded: Long? = null
    ): OfflinePackageItemStatus =
        OfflinePackageItemStatus(
            incidentId = INCIDENT_ID,
            policePhoneId = POLICE_PHONE_ID,
            manifestId = MANIFEST_ID,
            manifestVersion = 18,
            itemKey = itemKey,
            itemType = itemType,
            status = status,
            sourceVersion = 18,
            sourceHash = "sha256:$itemKey",
            bytesTotal = bytesTotal,
            bytesDownloaded = bytesDownloaded
        )

    private class CapturingSyncClient : SyncClient {
        var lastOperation: LocalWriteOperation? = null

        override suspend fun enqueue(writeOperation: LocalWriteOperation): EnqueueResult {
            lastOperation = writeOperation
            return EnqueueResult(
                outboxId = "outbox-001",
                operationId = writeOperation.operationId,
                status = OutboxStatus.PENDING,
                harnessStatus = HarnessSyncStatus.PENDING_SEND
            )
        }
    }

    private companion object {
        const val INCIDENT_ID = "inc-precinct-first-001"
        const val POLICE_PHONE_ID = "phone-precinct-001"
        const val MANIFEST_ID = "pkg-precinct-first-rev-18"
        val CLIENT_TS: Instant = Instant.parse("2026-05-11T06:00:00Z")
        val CLOCK_SYNCED_AT: Instant = Instant.parse("2026-05-11T05:59:30Z")
    }
}
