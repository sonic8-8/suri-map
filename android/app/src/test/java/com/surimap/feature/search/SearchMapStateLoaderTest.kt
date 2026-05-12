package com.surimap.feature.search

import com.surimap.core.network.SuriMapApiResponse
import com.surimap.feature.search.data.SearchMapSessionContext
import com.surimap.feature.search.data.SearchMapStateLoader
import com.surimap.feature.search.ui.SearchLifecycleStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchMapStateLoaderTest {

    @Test
    fun currentOpSessionMapsToActiveSearchMapWithoutSampleIncidentCopy() = runBlocking {
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
    fun incidentDetailMapsTitleAndMissingPersonSummary() = runBlocking {
        val loader =
            SearchMapStateLoader(
                incidentDetail = { incidentId ->
                    assertEquals("inc-precinct-first-001", incidentId)
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "id": "inc-precinct-first-001",
                          "incidentId": "inc-precinct-first-001",
                          "title": "광주 북구 산악 실종",
                          "status": "OPEN",
                          "version": 8,
                          "missingPerson": {
                            "displayName": "김실종",
                            "appearanceText": "회색 점퍼"
                          },
                          "assignments": []
                        }
                        """.trimIndent(),
                        errorCode = null
                    )
                }
            )

        val state =
            loader.load(
                SearchMapSessionContext(
                    incidentId = "inc-precinct-first-001",
                    currentOpId = "op-precinct-first-001",
                    currentDutyShiftId = "shift-precinct-day-001"
                )
            )

        assertEquals("광주 북구 산악 실종", state.incidentTitle)
        assertEquals("김실종 · 회색 점퍼", state.missingPersonSummary)
        assertEquals(SearchLifecycleStatus.Active, state.lifecycleStatus)
        assertTrue(state.canCreateMarker)
    }

    @Test
    fun incidentDetailFailureKeepsSessionBasedFallbackAndWriteGate() = runBlocking {
        val loader =
            SearchMapStateLoader(
                incidentDetail = {
                    SuriMapApiResponse(statusCode = 403, body = """{"error":"team_not_assigned"}""", errorCode = "team_not_assigned")
                }
            )

        val state =
            loader.load(
                SearchMapSessionContext(
                    incidentId = "inc-precinct-first-001",
                    currentOpId = null,
                    currentDutyShiftId = null
                )
            )

        assertEquals("inc-precinct-first-001", state.incidentTitle)
        assertEquals(SearchLifecycleStatus.OpRequired, state.lifecycleStatus)
        assertFalse(state.canCreateMarker)
    }

    @Test
    fun missingCurrentOpBlocksPathAndMarkerWrites() = runBlocking {
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
