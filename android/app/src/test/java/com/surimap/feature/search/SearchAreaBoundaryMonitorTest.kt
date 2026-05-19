package com.surimap.feature.search

import com.surimap.feature.search.domain.AssignedSearchAreaBoundary
import com.surimap.feature.search.domain.SearchAreaBoundaryFix
import com.surimap.feature.search.domain.SearchAreaBoundaryMonitor
import com.surimap.feature.search.domain.SearchAreaBoundarySignal
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAreaBoundaryMonitorTest {
    @Test
    fun `does not alert while current fix stays inside assigned team area`() {
        val monitor = SearchAreaBoundaryMonitor()

        val signal =
            monitor.evaluate(
                boundaries = listOf(boundary()),
                fix = SearchAreaBoundaryFix(lon = 126.1, lat = 35.1),
                nowMs = 1_000L
            )

        assertNull(signal)
    }

    @Test
    fun `alerts once when current fix exits assigned team area`() {
        val monitor = SearchAreaBoundaryMonitor(repeatCooldownMs = 60_000L)

        val first =
            monitor.evaluate(
                boundaries = listOf(boundary()),
                fix = SearchAreaBoundaryFix(lon = 127.0, lat = 36.0),
                nowMs = 1_000L
            )
        val repeated =
            monitor.evaluate(
                boundaries = listOf(boundary()),
                fix = SearchAreaBoundaryFix(lon = 127.0, lat = 36.0),
                nowMs = 2_000L
            )

        assertTrue(first is SearchAreaBoundarySignal.Exited)
        assertNull(repeated)
    }

    @Test
    fun `alerts again after reentering and exiting assigned team area`() {
        val monitor = SearchAreaBoundaryMonitor(repeatCooldownMs = 60_000L)

        assertTrue(
            monitor.evaluate(
                boundaries = listOf(boundary()),
                fix = SearchAreaBoundaryFix(lon = 127.0, lat = 36.0),
                nowMs = 1_000L
            ) is SearchAreaBoundarySignal.Exited
        )
        assertTrue(
            monitor.evaluate(
                boundaries = listOf(boundary()),
                fix = SearchAreaBoundaryFix(lon = 126.1, lat = 35.1),
                nowMs = 2_000L
            ) is SearchAreaBoundarySignal.Reentered
        )
        assertTrue(
            monitor.evaluate(
                boundaries = listOf(boundary()),
                fix = SearchAreaBoundaryFix(lon = 127.0, lat = 36.0),
                nowMs = 3_000L
            ) is SearchAreaBoundarySignal.Exited
        )
    }

    private fun boundary(): AssignedSearchAreaBoundary =
        AssignedSearchAreaBoundary(
            searchAreaId = "area-team-001",
            label = "기동대 1부대 A팀 담당 구역",
            geoJson = """
                {
                  "type": "Polygon",
                  "coordinates": [[[126.0,35.0],[126.2,35.0],[126.2,35.2],[126.0,35.2],[126.0,35.0]]]
                }
            """.trimIndent()
        )
}
