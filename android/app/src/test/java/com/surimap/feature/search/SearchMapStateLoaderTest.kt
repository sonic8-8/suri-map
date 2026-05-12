package com.surimap.feature.search

import com.surimap.feature.search.data.SearchMapSessionContext
import com.surimap.feature.search.data.SearchMapStateLoader
import com.surimap.feature.search.ui.SearchLifecycleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMapStateLoaderTest {

    @Test
    fun currentOpSessionMapsToActiveSearchMapWithoutSampleIncidentCopy() {
        val state =
            SearchMapStateLoader().load(
                SearchMapSessionContext(
                    incidentId = "inc-precinct-first-001",
                    currentOpId = "op-precinct-first-001",
                    currentDutyShiftId = "shift-precinct-day-001"
                )
            )

        assertEquals(SearchLifecycleStatus.Active, state.lifecycleStatus)
        assertEquals("inc-precinct-first-001", state.incidentTitle)
        assertEquals("OP op-precinct-first-001", state.opLabel)
        assertEquals("DutyShift shift-precinct-day-001", state.dutyShiftLabel)
        assertTrue(state.canWritePath)
        assertTrue(state.canCreateMarker)
        assertFalse(state.visibleText().any { it.contains("광주 북구 산악 실종") })
        assertFalse(state.visibleText().any { it.contains("기동대 1부대") })
    }

    @Test
    fun missingCurrentOpBlocksPathAndMarkerWrites() {
        val state =
            SearchMapStateLoader().load(
                SearchMapSessionContext(
                    incidentId = "inc-precinct-first-001",
                    currentOpId = null,
                    currentDutyShiftId = null
                )
            )

        assertEquals(SearchLifecycleStatus.OpRequired, state.lifecycleStatus)
        assertFalse(state.canWritePath)
        assertFalse(state.canCreateMarker)
        assertTrue(state.visibleText().any { it.contains("OP 다시 확인") })
        assertTrue(state.visibleText().any { it.contains("경로·마커 기록 차단") })
    }

    @Test
    fun appSearchMapRouteDoesNotRenderSampleStateDirectly() {
        val source = java.io.File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        assertFalse(source.contains("state = sampleSearchMapState()"))
        assertFalse(source.contains("import com.surimap.feature.search.ui.sampleSearchMapState"))
        assertTrue(source.contains("SearchMapStateLoader"))
        assertTrue(source.contains("toMapLibreRuntimeMapState"))
        assertTrue(source.contains("tileBaseUrl"))
        assertTrue(source.contains("policePhoneId"))
    }
}
