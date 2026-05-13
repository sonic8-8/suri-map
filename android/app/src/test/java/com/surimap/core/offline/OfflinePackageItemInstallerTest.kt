package com.surimap.core.offline

import com.surimap.testing.incidentIdFixture
import com.surimap.testing.manifestIdFixture
import com.surimap.testing.policePhoneIdFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePackageItemInstallerTest {

    @Test
    fun checksumMatchMarksDownloadableItemDownloadedAndReportsFinalStatuses() = runBlocking {
        val persisted = mutableListOf<List<OfflinePackageItemStatus>>()
        val reported = mutableListOf<List<OfflinePackageItemStatus>>()
        val installer =
            OfflinePackageItemInstaller(
                fetchBytes = { "tile-bytes".encodeToByteArray() },
                persistItemStatuses = { statuses -> persisted += statuses },
                reportInstallationProgress = { statuses -> reported += statuses }
            )

        val result =
            installer.install(
                command(
                    listOf(
                        item(
                            itemKey = "tile-1",
                            sourceHash = "sha256:256aa026a53e12257562cb73c6a1e4ce769566957c1823773028e08cf6ddb355"
                        )
                    )
                )
            )

        assertEquals("DOWNLOADED", result.single().status)
        assertEquals(10L, result.single().bytesTotal)
        assertEquals(10L, result.single().bytesDownloaded)
        assertEquals(result, persisted.single())
        assertEquals(result, reported.single())
    }

    @Test
    fun checksumMismatchMarksItemFailed() = runBlocking {
        val installer =
            OfflinePackageItemInstaller(
                fetchBytes = { "different-bytes".encodeToByteArray() },
                persistItemStatuses = {},
                reportInstallationProgress = {}
            )

        val result =
            installer.install(
                command(
                    listOf(
                        item(
                            itemKey = "tile-1",
                            sourceHash = "sha256:431e5be21ada11c5305cb2fc34a14f88a524b326edc04745dc8371c849a7501e"
                        )
                    )
                )
            )

        assertEquals("FAILED", result.single().status)
        assertEquals(15L, result.single().bytesTotal)
        assertEquals(15L, result.single().bytesDownloaded)
    }

    @Test
    fun fetchFailureMarksItemFailedAndStillReportsProgress() = runBlocking {
        val reported = mutableListOf<List<OfflinePackageItemStatus>>()
        val installer =
            OfflinePackageItemInstaller(
                fetchBytes = { error("network unavailable") },
                persistItemStatuses = {},
                reportInstallationProgress = { statuses -> reported += statuses }
            )

        val result = installer.install(command(listOf(item(itemKey = "tile-1"))))

        assertEquals("FAILED", result.single().status)
        assertEquals(null, result.single().bytesTotal)
        assertEquals(null, result.single().bytesDownloaded)
        assertEquals(result, reported.single())
    }

    @Test
    fun itemWithoutDownloadUrlIsSkippedAsCompleteLocalItem() = runBlocking {
        val installer =
            OfflinePackageItemInstaller(
                fetchBytes = { error("should not fetch payload-only item") },
                persistItemStatuses = {},
                reportInstallationProgress = {}
            )

        val result =
            installer.install(
                command(
                    listOf(
                        item(
                            itemKey = "incident-meta",
                            itemType = "INCIDENT_META",
                            downloadUrl = null
                        )
                    )
                )
            )

        assertEquals("SKIPPED", result.single().status)
        assertTrue(result.single().sourceHash.isNotBlank())
    }

    private fun command(items: List<OfflinePackageDownloadItem>): OfflinePackageItemInstallCommand =
        OfflinePackageItemInstallCommand(
            incidentId = INCIDENT_ID,
            policePhoneId = POLICE_PHONE_ID,
            manifestId = MANIFEST_ID,
            manifestVersion = 18,
            items = items
        )

    private fun item(
        itemKey: String,
        itemType: String = "TILE",
        sourceHash: String = "sha256:placeholder",
        downloadUrl: String? = "https://suri-map.example.com/tiles/osm-local/15/1/1.pbf"
    ): OfflinePackageDownloadItem =
        OfflinePackageDownloadItem(
            itemKey = itemKey,
            itemType = itemType,
            sourceVersion = 18,
            sourceHash = sourceHash,
            downloadUrl = downloadUrl
        )

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-001")
        val MANIFEST_ID = manifestIdFixture("precinct-first-rev-18")
    }
}
