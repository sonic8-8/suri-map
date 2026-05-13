package com.surimap.core.offline

import com.surimap.testing.incidentIdFixture
import com.surimap.testing.manifestIdFixture
import com.surimap.testing.policePhoneIdFixture
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
    fun manifestTileItemsMapToConcreteTileDownloads() {
        val plan =
            OfflinePackageDownloadPlan.fromManifestJson(
                """
                {
                  "manifestId": "$MANIFEST_ID",
                  "incidentId": "$INCIDENT_ID",
                  "manifestVersion": 18,
                  "tileItems": [
                    {
                      "styleId": "osm-local",
                      "z": 15,
                      "x": 27925,
                      "y": 12680,
                      "url": "local://tiles/inc-precinct-first-001/15/27925/12680.pbf",
                      "checksum": "sha256:tile-a",
                      "bytes": 18432
                    }
                  ],
                  "packageItems": [
                    {
                      "itemKey": "incident-meta",
                      "itemType": "INCIDENT_META",
                      "status": "PENDING",
                      "sourceVersion": 7,
                      "sourceHash": "sha256:incident"
                    },
                    {
                      "itemKey": "tile-manifest:tile-manifest-inc-precinct-001",
                      "itemType": "TILE",
                      "status": "PENDING",
                      "sourceVersion": 18,
                      "sourceHash": "sha256:tile-manifest"
                    }
                  ]
                }
                """.trimIndent()
            )

        assertEquals(
            listOf("incident-meta", "tile:osm-local:15:27925:12680"),
            plan!!.items.map { it.itemKey }
        )
        val tile = plan.items.single { it.itemType == "TILE" }
        assertEquals("/tiles/osm-local/15/27925/12680.pbf", tile.downloadUrl)
        assertEquals("sha256:tile-a", tile.sourceHash)
        assertEquals(18432L, tile.bytesTotal)
    }

    @Test
    fun blankOrMalformedManifestDoesNotCreateDownloadPlan() {
        assertNull(OfflinePackageDownloadPlan.fromManifestJson(""))
        assertNull(OfflinePackageDownloadPlan.fromManifestJson("""{"manifestId":"","packageItems":[]}"""))
        assertNull(OfflinePackageDownloadPlan.fromManifestJson("""{"manifestId":"$MANIFEST_ID"}"""))
    }

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-001")
        val MANIFEST_ID = manifestIdFixture("precinct-first-rev-18")
    }
}
