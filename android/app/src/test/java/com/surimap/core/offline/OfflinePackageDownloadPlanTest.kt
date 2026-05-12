package com.surimap.core.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflinePackageDownloadPlanTest {

    @Test
    fun manifestPackageItemsMapToDownloadPlanAndInitialStatuses() {
        val plan =
            OfflinePackageDownloadPlan.fromManifestJson(
                """
                {
                  "manifestId": "$MANIFEST_ID",
                  "incidentId": "$INCIDENT_ID",
                  "manifestVersion": 18,
                  "packageItems": [
                    {
                      "itemKey": "incident-meta",
                      "itemType": "INCIDENT_META",
                      "status": "PENDING",
                      "sourceVersion": 7,
                      "sourceHash": "sha256:incident",
                      "payload": {"title": "광주 북구 산악 실종"}
                    },
                    {
                      "itemKey": "tile:osm-local:15:27925:12680",
                      "itemType": "TILE",
                      "status": "DOWNLOADED",
                      "sourceVersion": 18,
                      "sourceHash": "sha256:tile",
                      "tile": {
                        "url": "/tiles/osm-local/15/27925/12680.pbf",
                        "checksum": "sha256:tile",
                        "bytes": 4096
                      }
                    }
                  ]
                }
                """.trimIndent()
            )

        assertEquals(MANIFEST_ID, plan!!.manifestId)
        assertEquals(18, plan.manifestVersion)
        assertEquals(listOf("incident-meta", "tile:osm-local:15:27925:12680"), plan.items.map { it.itemKey })
        assertNull(plan.items.first().downloadUrl)
        assertEquals("/tiles/osm-local/15/27925/12680.pbf", plan.items.last().downloadUrl)
        assertEquals(4096L, plan.items.last().bytesTotal)

        val statuses = plan.initialItemStatuses(INCIDENT_ID, POLICE_PHONE_ID)

        assertEquals("PENDING", statuses.single { it.itemKey == "incident-meta" }.status)
        assertNull(statuses.single { it.itemKey == "incident-meta" }.bytesTotal)
        assertEquals("DOWNLOADED", statuses.single { it.itemType == "TILE" }.status)
        assertEquals(4096L, statuses.single { it.itemType == "TILE" }.bytesTotal)
        assertEquals(4096L, statuses.single { it.itemType == "TILE" }.bytesDownloaded)
    }

    @Test
    fun blankOrMalformedManifestDoesNotCreateDownloadPlan() {
        assertNull(OfflinePackageDownloadPlan.fromManifestJson(""))
        assertNull(OfflinePackageDownloadPlan.fromManifestJson("""{"manifestId":"","packageItems":[]}"""))
        assertNull(OfflinePackageDownloadPlan.fromManifestJson("""{"manifestId":"$MANIFEST_ID"}"""))
    }

    private companion object {
        const val INCIDENT_ID = "inc-precinct-first-001"
        const val POLICE_PHONE_ID = "phone-precinct-001"
        const val MANIFEST_ID = "pkg-precinct-first-rev-18"
    }
}
