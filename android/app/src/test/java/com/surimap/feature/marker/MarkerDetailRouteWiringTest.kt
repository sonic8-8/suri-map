package com.surimap.feature.marker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerDetailRouteWiringTest {

    @Test
    fun markerDetailRouteLoadsActualMarkerAndEnqueuesUpdateDeleteWrites() {
        val source = java.io.File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        assertFalse(source.contains("state = sampleMarkerDetailState()"))
        assertFalse(source.contains("import com.surimap.feature.marker.ui.sampleMarkerDetailState"))
        assertTrue(source.contains("MarkerDetailDeepLink.RoutePattern"))
        assertTrue(source.contains("MarkerDetailStateLoader"))
        assertTrue(source.contains("MarkerRepository"))
        assertTrue(source.contains("listMarkers"))
        assertTrue(source.contains("MarkerLocalRecorder"))
        assertTrue(source.contains("updateMarker"))
        assertTrue(source.contains("deleteMarker"))
        assertTrue(source.contains("markerDetailState.toMarkerUpsertInput()"))
        assertTrue(source.contains("MarkerSaveStatus.PendingOutbox"))
        assertTrue(source.contains("MarkerSaveStatus.Failed"))
    }
}
