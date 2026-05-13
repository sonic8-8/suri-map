package com.surimap.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidHarnessFixtureCatalogTest {
    @Test
    fun commonFixtureResourceIsConnectedForAndroidUiScenarios() {
        val catalog = AndroidHarnessFixtureCatalog.load()

        val expectedScenarioFixtureIds =
            mapOf(
                "SC-03" to "tile-manifest-inc-precinct-001",
                "SC-05" to "gps-path-normal-001",
                "SC-06" to "mk-precinct-clue-001",
                "SC-07" to "net-script-domain-write-001",
                "SC-08" to "evt-s5-person-found-001",
                "SC-09" to "net-script-outbox-flush-001",
                "SC-10" to "memo-precinct-handover-001",
                "SC-11" to "summary-precinct-op2-001",
                "SC-12" to "inc-precinct-first-001"
            )

        expectedScenarioFixtureIds.forEach { (scenarioId, fixtureId) ->
            val entry = catalog.requireFixture(fixtureId)
            assertTrue("$fixtureId must cover $scenarioId", scenarioId in entry.usesScenarios)
        }
    }

    @Test
    fun catalogKeepsFixtureIdsOnCanonicalOwnerPaths() {
        val catalog = AndroidHarnessFixtureCatalog.load()

        val ownerPaths =
            mapOf(
                "tile-manifest-inc-precinct-001" to "tileManifest.fixtureId",
                "gps-path-normal-001" to "gpsPath.fixtureId",
                "outbox-path-001" to "outboxReplay.sc05PathReplay.outboxId",
                "outbox-marker-001" to "outboxReplay.sc06MarkerPhotoReplay.markerOutboxId",
                "outbox-photo-001" to "outboxReplay.sc06MarkerPhotoReplay.photoOutboxId",
                "evt-s5-support-request-001" to "eventRegistry.supportRequestNotification.eventId",
                "memo-precinct-op2-001" to "searchHistorySummary.memoAlias",
                "summary-precinct-op2-001" to "searchHistorySummary.summaryAlias"
            )

        ownerPaths.forEach { (fixtureId, ownerPath) ->
            assertEquals(ownerPath, catalog.requireFixture(fixtureId).ownerPath)
        }
    }
}
