package com.surimap.feature.search

import com.surimap.core.database.OutboxStatusSummary
import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.path.SearchPathQuery
import com.surimap.feature.search.data.SearchMapSessionContext
import com.surimap.feature.search.data.SearchMapStateLoader
import com.surimap.feature.search.ui.SearchLifecycleStatus
import com.surimap.feature.search.ui.SearchLayerKind
import com.surimap.feature.search.ui.SearchMapSyncStatus
import com.surimap.feature.search.ui.SearchMapViewportBounds
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
            fallbackOnlyLoader().load(
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
                },
                overallSearchArea = { notFoundResponse() },
                opSearchAreas = { _, _ -> notFoundResponse() }
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
                },
                overallSearchArea = { notFoundResponse() },
                opSearchAreas = { _, _ -> notFoundResponse() }
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
    fun outboxSummaryMapsPendingQueueAndBlockedFinalFailures() = runBlocking {
        val loader =
            SearchMapStateLoader(
                incidentDetail = { SuriMapApiResponse(statusCode = 404, body = null, errorCode = null) },
                overallSearchArea = { notFoundResponse() },
                opSearchAreas = { _, _ -> notFoundResponse() },
                outboxSummary = { incidentId, policePhoneId ->
                    assertEquals("inc-precinct-first-001", incidentId)
                    assertEquals("phone-precinct-001", policePhoneId)
                    OutboxStatusSummary(
                        pendingCount = 2,
                        retryableCount = 1,
                        finalFailedCount = 1,
                        oldestPendingClientRequestedAt = 1_000L
                    )
                },
                nowMs = { 181_000L }
            )

        val state =
            loader.load(
                SearchMapSessionContext(
                    incidentId = "inc-precinct-first-001",
                    currentOpId = "op-precinct-first-001",
                    currentDutyShiftId = "shift-precinct-day-001",
                    policePhoneId = "phone-precinct-001"
                )
            )

        assertEquals(SearchMapSyncStatus.Offline, state.syncStatus)
        assertEquals(3, state.unsentCount)
        assertEquals(3, state.oldestPendingMinutes)
        assertEquals(1, state.blockedOutboxCount)
        assertTrue(state.visibleText().any { it.contains("미전송 1건 처리 불가") })
    }

    @Test
    fun activeOverallSearchAreaMapsViewportAndOverallOverlay() = runBlocking {
        val overallGeometry =
            """
            {
              "type": "Polygon",
              "coordinates": [[
                [126.900000, 37.500000],
                [127.080000, 37.500000],
                [127.080000, 37.620000],
                [126.900000, 37.620000],
                [126.900000, 37.500000]
              ]]
            }
            """.trimIndent()
        val loader =
            SearchMapStateLoader(
                incidentDetail = { SuriMapApiResponse(statusCode = 404, body = null, errorCode = null) },
                overallSearchArea = { incidentId ->
                    assertEquals("inc-precinct-first-001", incidentId)
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "id": "area-overall-001",
                          "incidentId": "inc-precinct-first-001",
                          "areaLevel": "OVERALL",
                          "status": "ACTIVE",
                          "version": 3,
                          "geometry": $overallGeometry
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

        assertEquals(
            SearchMapViewportBounds(
                south = 37.5,
                west = 126.9,
                north = 37.62,
                east = 127.08
            ),
            state.viewportBounds
        )
        assertEquals(1, state.layers.size)
        assertEquals(SearchLayerKind.Overall, state.layers[0].kind)
        assertTrue(state.layers[0].geoJson!!.contains("\"Polygon\""))
        assertEquals("전체 수색 구역", state.layers[0].label)
        assertFalse(state.layers[0].highlighted)
    }

    @Test
    fun currentOpSearchAreasMapUnitAndTeamOverlaysAfterOverallLayer() = runBlocking {
        val areaGeometry =
            """
            {
              "type": "Polygon",
              "coordinates": [[
                [126.910000, 37.510000],
                [126.930000, 37.510000],
                [126.930000, 37.530000],
                [126.910000, 37.530000],
                [126.910000, 37.510000]
              ]]
            }
            """.trimIndent()
        val loader =
            SearchMapStateLoader(
                incidentDetail = { notFoundResponse() },
                overallSearchArea = {
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "id": "area-overall-001",
                          "incidentId": "inc-precinct-first-001",
                          "status": "ACTIVE",
                          "geometry": $areaGeometry
                        }
                        """.trimIndent(),
                        errorCode = null
                    )
                },
                opSearchAreas = { incidentId, opId ->
                    assertEquals("inc-precinct-first-001", incidentId)
                    assertEquals("op-precinct-first-001", opId)
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "incidentId": "inc-precinct-first-001",
                          "sourceVersion": 9,
                          "areas": [
                            {
                              "id": "area-unit-001",
                              "opId": "op-precinct-first-001",
                              "areaLevel": "UNIT",
                              "name": "기동대 1부대",
                              "status": "ACTIVE",
                              "version": 4,
                              "geometry": $areaGeometry
                            },
                            {
                              "id": "area-team-001",
                              "opId": "op-precinct-first-001",
                              "parentAreaId": "area-unit-001",
                              "areaLevel": "TEAM",
                              "name": "A팀 담당 구역",
                              "status": "ACTIVE",
                              "version": 5,
                              "geometry": $areaGeometry
                            }
                          ]
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

        assertEquals(3, state.layers.size)
        assertEquals(SearchLayerKind.Overall, state.layers[0].kind)
        assertEquals(SearchLayerKind.Unit, state.layers[1].kind)
        assertEquals("기동대 1부대", state.layers[1].label)
        assertEquals("area-unit-001", state.layers[1].overlayId)
        assertTrue(state.layers[1].geoJson!!.contains("\"Polygon\""))
        assertEquals(SearchLayerKind.Team, state.layers[2].kind)
        assertEquals("A팀 담당 구역", state.layers[2].label)
        assertEquals("area-team-001", state.layers[2].overlayId)
        assertTrue(state.layers[2].highlighted)
    }

    @Test
    fun currentPolicePhoneSearchPathsMapToLineOverlaysAfterAreaLayers() = runBlocking {
        val areaGeometry =
            """
            {
              "type": "Polygon",
              "coordinates": [[
                [126.910000, 37.510000],
                [126.930000, 37.510000],
                [126.930000, 37.530000],
                [126.910000, 37.530000],
                [126.910000, 37.510000]
              ]]
            }
            """.trimIndent()
        val pathGeometry =
            """
            {
              "type": "LineString",
              "coordinates": [
                [126.912000, 37.512000],
                [126.918000, 37.518000],
                [126.924000, 37.524000]
              ]
            }
            """.trimIndent()
        val loader =
            SearchMapStateLoader(
                incidentDetail = { notFoundResponse() },
                overallSearchArea = {
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "id": "area-overall-001",
                          "incidentId": "inc-precinct-first-001",
                          "status": "ACTIVE",
                          "geometry": $areaGeometry
                        }
                        """.trimIndent(),
                        errorCode = null
                    )
                },
                opSearchAreas = { _, _ ->
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "areas": [
                            {
                              "id": "area-team-001",
                              "opId": "op-precinct-first-001",
                              "areaLevel": "TEAM",
                              "name": "A팀 담당 구역",
                              "status": "ACTIVE",
                              "geometry": $areaGeometry
                            }
                          ]
                        }
                        """.trimIndent(),
                        errorCode = null
                    )
                },
                searchPaths = { query ->
                    assertEquals("inc-precinct-first-001", query.incidentId)
                    assertEquals("op-precinct-first-001", query.opId)
                    assertEquals("phone-precinct-001", query.policePhoneId)
                    assertEquals(true, query.includeGeometry)
                    assertEquals("RENDER_SIMPLIFIED", query.geometryMode)
                    assertEquals("startedAtAsc", query.sort)
                    assertEquals(500, query.limit)
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "paths": [
                            {
                              "id": "path-001",
                              "status": "ACTIVE",
                              "version": 12,
                              "incidentId": "inc-precinct-first-001",
                              "opId": "op-precinct-first-001",
                              "policePhoneId": "phone-precinct-001",
                              "geometryMode": "RENDER_SIMPLIFIED",
                              "geometry": $pathGeometry
                            }
                          ]
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
                    currentDutyShiftId = "shift-precinct-day-001",
                    policePhoneId = "phone-precinct-001"
                )
            )

        assertEquals(SearchLayerKind.Overall, state.layers[0].kind)
        assertEquals(SearchLayerKind.Team, state.layers[1].kind)
        assertEquals(SearchLayerKind.Path, state.layers[2].kind)
        assertEquals("현재 경로", state.layers[2].label)
        assertEquals("path-001", state.layers[2].overlayId)
        assertTrue(state.layers[2].highlighted)
        assertTrue(state.layers[2].geoJson!!.contains("\"LineString\""))
        assertEquals("경로 1개 표시", state.movementSummary)
    }

    @Test
    fun missingPolicePhoneDoesNotReadSearchPaths() = runBlocking {
        var searchPathsCalled = false
        val loader =
            SearchMapStateLoader(
                incidentDetail = { notFoundResponse() },
                overallSearchArea = { notFoundResponse() },
                opSearchAreas = { _, _ -> notFoundResponse() },
                searchPaths = { _: SearchPathQuery ->
                    searchPathsCalled = true
                    notFoundResponse()
                }
            )

        val state =
            loader.load(
                SearchMapSessionContext(
                    incidentId = "inc-precinct-first-001",
                    currentOpId = "op-precinct-first-001",
                    currentDutyShiftId = "shift-precinct-day-001",
                    policePhoneId = null
                )
            )

        assertFalse(searchPathsCalled)
        assertEquals(SearchLifecycleStatus.Active, state.lifecycleStatus)
    }

    @Test
    fun searchPathReadFailureKeepsAreaOverlays() = runBlocking {
        val areaGeometry =
            """
            {
              "type": "Polygon",
              "coordinates": [[
                [126.910000, 37.510000],
                [126.930000, 37.510000],
                [126.930000, 37.530000],
                [126.910000, 37.530000],
                [126.910000, 37.510000]
              ]]
            }
            """.trimIndent()
        val loader =
            SearchMapStateLoader(
                incidentDetail = { notFoundResponse() },
                overallSearchArea = {
                    SuriMapApiResponse(
                        statusCode = 200,
                        body = """{"id":"area-overall-001","geometry":$areaGeometry}""",
                        errorCode = null
                    )
                },
                opSearchAreas = { _, _ -> notFoundResponse() },
                searchPaths = {
                    SuriMapApiResponse(
                        statusCode = 403,
                        body = """{"error":"team_not_assigned"}""",
                        errorCode = "team_not_assigned"
                    )
                }
            )

        val state =
            loader.load(
                SearchMapSessionContext(
                    incidentId = "inc-precinct-first-001",
                    currentOpId = "op-precinct-first-001",
                    currentDutyShiftId = "shift-precinct-day-001",
                    policePhoneId = "phone-precinct-001"
                )
            )

        assertEquals(1, state.layers.size)
        assertEquals(SearchLayerKind.Overall, state.layers.single().kind)
        assertTrue(state.layers.single().geoJson!!.contains("\"Polygon\""))
    }

    @Test
    fun overallSearchAreaRequiredKeepsFallbackWithoutMapOverlay() = runBlocking {
        val loader =
            SearchMapStateLoader(
                incidentDetail = { notFoundResponse() },
                overallSearchArea = {
                    SuriMapApiResponse(
                        statusCode = 409,
                        body = """{"error":"overall_search_area_required"}""",
                        errorCode = "overall_search_area_required"
                    )
                },
                opSearchAreas = { _, _ -> notFoundResponse() }
            )

        val state =
            loader.load(
                SearchMapSessionContext(
                    incidentId = "inc-precinct-first-001",
                    currentOpId = "op-precinct-first-001",
                    currentDutyShiftId = "shift-precinct-day-001"
                )
            )

        assertEquals(null, state.viewportBounds)
        assertTrue(state.layers.all { layer -> layer.geoJson == null })
        assertTrue(state.visibleText().any { it == "담당 구역 확인 중" })
    }

    @Test
    fun missingCurrentOpDoesNotReadOpSearchAreas() = runBlocking {
        var opSearchAreasCalled = false
        val loader =
            SearchMapStateLoader(
                incidentDetail = { notFoundResponse() },
                overallSearchArea = { notFoundResponse() },
                opSearchAreas = { _, _ ->
                    opSearchAreasCalled = true
                    notFoundResponse()
                }
            )

        val state =
            loader.load(
                SearchMapSessionContext(
                    incidentId = "inc-precinct-first-001",
                    currentOpId = null,
                    currentDutyShiftId = "shift-precinct-day-001"
                )
            )

        assertFalse(opSearchAreasCalled)
        assertEquals(SearchLifecycleStatus.OpRequired, state.lifecycleStatus)
        assertTrue(state.layers.all { layer -> layer.geoJson == null })
    }

    @Test
    fun missingCurrentOpBlocksPathAndMarkerWrites() = runBlocking {
        val state =
            fallbackOnlyLoader().load(
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
        assertTrue(source.contains("statusSummary"))
        assertTrue(source.contains("activeOverall"))
        assertTrue(source.contains("opSearchAreas"))
        assertTrue(source.contains("list(incidentId = incidentId, opId = opId, status = \"ACTIVE\")"))
        assertTrue(source.contains("SearchPathRepository"))
        assertTrue(source.contains("listSearchPaths"))
    }

    private fun fallbackOnlyLoader(): SearchMapStateLoader =
        SearchMapStateLoader(
            incidentDetail = { notFoundResponse() },
            overallSearchArea = { notFoundResponse() },
            opSearchAreas = { _, _ -> notFoundResponse() }
        )

    private fun notFoundResponse(): SuriMapApiResponse =
        SuriMapApiResponse(statusCode = 404, body = null, errorCode = null)
}
