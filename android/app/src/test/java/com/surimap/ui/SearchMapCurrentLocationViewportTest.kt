package com.surimap.ui

import com.surimap.core.location.GpsLocationFix
import com.surimap.feature.search.ui.SearchLayerKind
import com.surimap.feature.search.ui.SearchMapLayerUiState
import com.surimap.feature.search.ui.SearchMapUiState
import com.surimap.feature.search.ui.SearchMapViewportBounds
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchMapCurrentLocationViewportTest {

    @Test
    fun centerOnCurrentLocationClearsFocusedMarkerAndRecentersViewport() {
        val state =
            SearchMapUiState.active().copy(
                focusedMarkerId = "marker-1",
                layers =
                    listOf(
                        SearchMapLayerUiState(
                            label = "단서",
                            kind = SearchLayerKind.Marker,
                            overlayId = "marker-1",
                            geoJson = """{"type":"Point","coordinates":[126.901,35.161]}"""
                        )
                    )
            )

        val centered =
            state.centerOnCurrentLocation(
                GpsLocationFix(
                    lon = 126.912345,
                    lat = 35.176543,
                    bearingDegrees = null,
                    speedMps = null,
                    horizontalAccuracyM = 8,
                    capturedAt = Instant.parse("2026-05-18T09:00:00Z")
                )
            )

        assertNull(centered.focusedMarkerId)
        assertEquals(35.173543, centered.viewportBounds!!.south, 0.000001)
        assertEquals(126.909345, centered.viewportBounds!!.west, 0.000001)
        assertEquals(35.179543, centered.viewportBounds!!.north, 0.000001)
        assertEquals(126.915345, centered.viewportBounds!!.east, 0.000001)
    }

    @Test
    fun currentLocationCarriesNormalizedBearingForMapHeadingIndicator() {
        val centered =
            SearchMapUiState.active().centerOnCurrentLocation(
                GpsLocationFix(
                    lon = 126.912345,
                    lat = 35.176543,
                    bearingDegrees = 361.7,
                    speedMps = 1.4,
                    horizontalAccuracyM = 8,
                    capturedAt = Instant.parse("2026-05-18T09:00:00Z")
                )
            )

        val currentLocationLayer = centered.layers.single { it.kind == SearchLayerKind.CurrentLocation }
        assertEquals(1.7, currentLocationLayer.bearingDegrees!!, 0.000001)
        assertEquals("current-location", currentLocationLayer.overlayId)
        assertEquals("""{"type":"Point","coordinates":[126.912345,35.176543]}""", currentLocationLayer.geoJson)
        assertEquals("", currentLocationLayer.label)
    }

    @Test
    fun serverRefreshPreservesCurrentLocationViewport() {
        val centered =
            SearchMapUiState.active().centerOnCurrentLocation(
                GpsLocationFix(
                    lon = 126.912345,
                    lat = 35.176543,
                    bearingDegrees = null,
                    speedMps = null,
                    horizontalAccuracyM = 8,
                    capturedAt = Instant.parse("2026-05-18T09:00:00Z")
                )
            )
        val refreshedFromServer =
            SearchMapUiState.active().copy(
                viewportBounds =
                SearchMapViewportBounds(
                    south = 35.0,
                    west = 126.7,
                    north = 35.3,
                    east = 127.0
                )
            )

        val preserved = refreshedFromServer.preserveViewportFrom(centered)

        assertEquals(centered.viewportBounds, preserved.viewportBounds)
    }
}
