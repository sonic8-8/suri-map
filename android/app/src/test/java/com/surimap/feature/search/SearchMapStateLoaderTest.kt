package com.surimap.feature.search

import com.surimap.core.database.LocalMarkerEntity
import com.surimap.core.database.OutboxStatusSummary
import com.surimap.core.marker.MarkerReadQuery
import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.path.SearchPathQuery
import com.surimap.feature.search.data.SearchMapSessionContext
import com.surimap.feature.search.data.SearchMapStateLoader
import com.surimap.feature.search.ui.SearchLifecycleStatus
import com.surimap.feature.search.ui.SearchLayerKind
import com.surimap.feature.search.ui.SearchMapSyncStatus
import com.surimap.feature.search.ui.SearchMapViewportBounds
import com.surimap.testing.areaIdFixture
import com.surimap.testing.dutyShiftIdFixture
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.markerIdFixture
import com.surimap.testing.opIdFixture
import com.surimap.testing.pathIdFixture
import com.surimap.testing.policePhoneIdFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentOpLabel = "OP 2차",
                    currentDutyShiftId = DUTY_SHIFT_ID
                )
            )

        assertEquals(SearchLifecycleStatus.Active, state.lifecycleStatus)
        assertEquals(INCIDENT_ID, state.incidentTitle)
        assertEquals("OP 2차", state.opLabel)
        assertEquals("DutyShift $DUTY_SHIFT_ID", state.dutyShiftLabel)
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
                    assertEquals(INCIDENT_ID, incidentId)
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "id": "$INCIDENT_ID",
                          "incidentId": "$INCIDENT_ID",
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
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentDutyShiftId = DUTY_SHIFT_ID
                )
            )

        assertEquals("광주 북구 산악 실종", state.incidentTitle)
        assertEquals("김실종 · 회색 점퍼", state.missingPersonSummary)
        assertEquals(SearchLifecycleStatus.Active, state.lifecycleStatus)
        assertTrue(state.canCreateMarker)
    }

    @Test
    fun incidentDetailHidesMissingPersonFixtureCodeFromSummary() = runBlocking {
        val loader =
            SearchMapStateLoader(
                incidentDetail = {
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "id": "$INCIDENT_ID",
                          "title": "무등산 증심사 계곡 실종자 수색",
                          "missingPerson": {
                            "displayName": "실종자 T2-무등-01",
                            "appearanceText": "남색 등산복"
                          }
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
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentDutyShiftId = DUTY_SHIFT_ID
                )
            )

        assertEquals("실종자 · 남색 등산복", state.missingPersonSummary)
        assertFalse(state.missingPersonSummary.contains("T2-무등-01"))
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
                    incidentId = INCIDENT_ID,
                    currentOpId = null,
                    currentDutyShiftId = null
                )
            )

        assertEquals(INCIDENT_ID, state.incidentTitle)
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
                    assertEquals(INCIDENT_ID, incidentId)
                    assertEquals(POLICE_PHONE_ID, policePhoneId)
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
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentDutyShiftId = DUTY_SHIFT_ID,
                    policePhoneId = POLICE_PHONE_ID
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
                [126.647507, 35.052595],
                [127.017482, 35.052595],
                [127.017482, 35.256837],
                [126.647507, 35.256837],
                [126.647507, 35.052595]
              ]]
            }
            """.trimIndent()
        val loader =
            SearchMapStateLoader(
                incidentDetail = { SuriMapApiResponse(statusCode = 404, body = null, errorCode = null) },
                overallSearchArea = { incidentId ->
                    assertEquals(INCIDENT_ID, incidentId)
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "id": "$OVERALL_AREA_ID",
                          "incidentId": "$INCIDENT_ID",
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
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentDutyShiftId = DUTY_SHIFT_ID
                )
            )

        assertEquals(
            SearchMapViewportBounds(
                south = 35.052595,
                west = 126.647507,
                north = 35.256837,
                east = 127.017482
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
                          "id": "$OVERALL_AREA_ID",
                          "incidentId": "$INCIDENT_ID",
                          "status": "ACTIVE",
                          "geometry": $areaGeometry
                        }
                        """.trimIndent(),
                        errorCode = null
                    )
                },
                opSearchAreas = { incidentId, opId ->
                    assertEquals(INCIDENT_ID, incidentId)
                    assertEquals(OP_ID, opId)
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "incidentId": "$INCIDENT_ID",
                          "sourceVersion": 9,
                          "areas": [
                            {
                              "id": "$UNIT_AREA_ID",
                              "opId": "$OP_ID",
                              "areaLevel": "UNIT",
                              "name": "기동대 1부대",
                              "status": "ACTIVE",
                              "version": 4,
                              "geometry": $areaGeometry
                            },
                            {
                              "id": "$TEAM_AREA_ID",
                              "opId": "$OP_ID",
                              "parentAreaId": "$UNIT_AREA_ID",
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
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentDutyShiftId = DUTY_SHIFT_ID
                )
            )

        assertEquals(3, state.layers.size)
        assertEquals(SearchLayerKind.Overall, state.layers[0].kind)
        assertEquals(SearchLayerKind.Unit, state.layers[1].kind)
        assertEquals("기동대 1부대", state.layers[1].label)
        assertEquals(UNIT_AREA_ID, state.layers[1].overlayId)
        assertTrue(state.layers[1].geoJson!!.contains("\"Polygon\""))
        assertEquals(SearchLayerKind.Team, state.layers[2].kind)
        assertEquals("A팀 담당 구역", state.layers[2].label)
        assertEquals(TEAM_AREA_ID, state.layers[2].overlayId)
        assertTrue(state.layers[2].highlighted)
        assertEquals("A팀 담당 구역", state.assignmentLabel)
    }

    @Test
    fun manifestAssignedAreasMarkOnlyCurrentPolicePhoneTeamBoundary() = runBlocking {
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
                overallSearchArea = { notFoundResponse() },
                opSearchAreas = { _, _ ->
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "areas": [
                            {
                              "id": "$UNIT_AREA_ID",
                              "opId": "$OP_ID",
                              "areaLevel": "UNIT",
                              "name": "기동대 1부대",
                              "status": "ACTIVE",
                              "geometry": $areaGeometry
                            },
                            {
                              "id": "$TEAM_AREA_ID",
                              "opId": "$OP_ID",
                              "parentAreaId": "$UNIT_AREA_ID",
                              "areaLevel": "TEAM",
                              "name": "A팀 담당 구역",
                              "status": "ACTIVE",
                              "geometry": $areaGeometry
                            },
                            {
                              "id": "$TEAM_AREA_ID_2",
                              "opId": "$OP_ID",
                              "parentAreaId": "$UNIT_AREA_ID",
                              "areaLevel": "TEAM",
                              "name": "B팀 담당 구역",
                              "status": "ACTIVE",
                              "geometry": $areaGeometry
                            }
                          ]
                        }
                        """.trimIndent(),
                        errorCode = null
                    )
                },
                initialMarkers = { _, policePhoneId ->
                    assertEquals(POLICE_PHONE_ID, policePhoneId)
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "assignedAreas": [
                            {
                              "areaId": "$TEAM_AREA_ID",
                              "incidentId": "$INCIDENT_ID",
                              "opId": "$OP_ID",
                              "status": "ACTIVE",
                              "version": 2
                            }
                          ],
                          "initialMarkers": []
                        }
                        """.trimIndent(),
                        errorCode = null
                    )
                }
            )

        val state =
            loader.load(
                SearchMapSessionContext(
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentDutyShiftId = DUTY_SHIFT_ID,
                    policePhoneId = POLICE_PHONE_ID
                )
            )

        val teamLayers = state.layers.filter { it.kind == SearchLayerKind.Team }
        assertEquals(2, teamLayers.size)
        assertEquals("A팀 담당 구역", state.assignmentLabel)
        assertTrue(teamLayers.single { it.overlayId == TEAM_AREA_ID }.assignedToCurrentPhone)
        assertTrue(teamLayers.single { it.overlayId == TEAM_AREA_ID }.highlighted)
        assertFalse(teamLayers.single { it.overlayId == TEAM_AREA_ID_2 }.assignedToCurrentPhone)
        assertFalse(teamLayers.single { it.overlayId == TEAM_AREA_ID_2 }.highlighted)
    }

    @Test
    fun unitAreaLabelFallsBackAsAssignmentWhenTeamAreaIsMissing() = runBlocking {
        val areaGeometry =
            """
            {
              "type": "Polygon",
              "coordinates": [[[126.91,35.16],[126.93,35.16],[126.93,35.18],[126.91,35.18],[126.91,35.16]]]
            }
            """.trimIndent()
        val loader =
            SearchMapStateLoader(
                incidentDetail = { notFoundResponse() },
                overallSearchArea = { notFoundResponse() },
                opSearchAreas = { _, _ ->
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "areas": [
                            {
                              "id": "$UNIT_AREA_ID",
                              "opId": "$OP_ID",
                              "areaLevel": "UNIT",
                              "name": "기동대 1부대",
                              "status": "ACTIVE",
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
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentDutyShiftId = DUTY_SHIFT_ID
                )
            )

        assertEquals("기동대 1부대", state.assignmentLabel)
        assertEquals("기동대 1부대", state.assignmentDisplayLabel)
    }

    @Test
    fun incidentSearchPathsRenderAllPhonesButOnlyCurrentPhoneControlsRecordingState() = runBlocking {
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
                          "id": "$OVERALL_AREA_ID",
                          "incidentId": "$INCIDENT_ID",
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
                              "id": "$TEAM_AREA_ID",
                              "opId": "$OP_ID",
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
                    assertEquals(INCIDENT_ID, query.incidentId)
                    assertEquals(OP_ID, query.opId)
                    assertNull(query.policePhoneId)
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
                              "id": "$PATH_ID",
                              "status": "RECORDING",
                              "version": 12,
                              "incidentId": "$INCIDENT_ID",
                              "opId": "$OP_ID",
                              "policePhoneId": "$POLICE_PHONE_ID",
                              "startedAt": "2026-05-18T04:53:12.331Z",
                              "geometryMode": "RENDER_SIMPLIFIED",
                              "geometry": $pathGeometry
                            },
                            {
                              "id": "$OTHER_PATH_ID",
                              "status": "RECORDING",
                              "version": 9,
                              "incidentId": "$INCIDENT_ID",
                              "opId": "$OP_ID",
                              "policePhoneId": "$OTHER_POLICE_PHONE_ID",
                              "startedAt": "2026-05-18T04:55:12.331Z",
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
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentDutyShiftId = DUTY_SHIFT_ID,
                    policePhoneId = POLICE_PHONE_ID
                )
            )

        assertEquals(SearchLayerKind.Overall, state.layers[0].kind)
        assertEquals(SearchLayerKind.Team, state.layers[1].kind)
        assertEquals(SearchLayerKind.Path, state.layers[2].kind)
        assertEquals(SearchLayerKind.Path, state.layers[3].kind)
        assertEquals("현재 경로", state.layers[2].label)
        assertEquals("다른 단말 경로", state.layers[3].label)
        assertEquals(PATH_ID, state.layers[2].overlayId)
        assertEquals(OTHER_PATH_ID, state.layers[3].overlayId)
        assertEquals(PATH_ID, state.activeSearchPathId)
        assertTrue(state.layers[2].highlighted)
        assertFalse(state.layers[3].highlighted)
        assertTrue(state.layers[2].geoJson!!.contains("\"LineString\""))
        assertTrue(state.layers[3].geoJson!!.contains("\"LineString\""))
        assertEquals("경로 2개 표시", state.movementSummary)
        assertEquals(1779079992331L, state.activeSearchPathStartedAtEpochMs)
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
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentDutyShiftId = DUTY_SHIFT_ID,
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
                        body = """{"id":"$OVERALL_AREA_ID","geometry":$areaGeometry}""",
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
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentDutyShiftId = DUTY_SHIFT_ID,
                    policePhoneId = POLICE_PHONE_ID
                )
            )

        assertEquals(1, state.layers.size)
        assertEquals(SearchLayerKind.Overall, state.layers.single().kind)
        assertTrue(state.layers.single().geoJson!!.contains("\"Polygon\""))
    }

    @Test
    fun manifestInitialMarkersMapToMarkerPointOverlaysAfterPathLayers() = runBlocking {
        val markerLocation =
            """
            {
              "type": "Point",
              "coordinates": [126.915000, 37.515000]
            }
            """.trimIndent()
        val pathGeometry =
            """
            {
              "type": "LineString",
              "coordinates": [
                [126.912000, 37.512000],
                [126.918000, 37.518000]
              ]
            }
            """.trimIndent()
        val loader =
            SearchMapStateLoader(
                incidentDetail = { notFoundResponse() },
                overallSearchArea = { notFoundResponse() },
                opSearchAreas = { _, _ -> notFoundResponse() },
                searchPaths = {
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "paths": [
                            {
                              "id": "$PATH_ID",
                              "status": "ACTIVE",
                              "geometry": $pathGeometry
                            }
                          ]
                        }
                        """.trimIndent(),
                        errorCode = null
                    )
                },
                initialMarkers = { incidentId, policePhoneId ->
                    assertEquals(INCIDENT_ID, incidentId)
                    assertEquals(POLICE_PHONE_ID, policePhoneId)
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "manifestId": "manifest-001",
                          "incidentId": "$INCIDENT_ID",
                          "manifestVersion": 7,
                          "initialMarkers": [
                            {
                              "id": "$MARKER_ID",
                              "incidentId": "$INCIDENT_ID",
                              "opId": "$OP_ID",
                              "type": "CLUE",
                              "status": "ACTIVE",
                              "version": 3,
                              "location": $markerLocation,
                              "memo": "등산로 입구 제보"
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
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentDutyShiftId = DUTY_SHIFT_ID,
                    policePhoneId = POLICE_PHONE_ID
                )
            )

        assertEquals(SearchLayerKind.Path, state.layers[2].kind)
        assertEquals(SearchLayerKind.Marker, state.layers[3].kind)
        assertEquals("단서", state.layers[3].label)
        assertEquals(MARKER_ID, state.layers[3].overlayId)
        assertTrue(state.layers[3].highlighted)
        assertTrue(state.layers[3].geoJson!!.contains("\"Point\""))
    }

    @Test
    fun liveMarkerReadMapsToMarkerOverlaysAndSkipsInitialManifestFallback() = runBlocking {
        var initialMarkersCalled = false
        val markerLocation =
            """
            {
              "type": "Point",
              "coordinates": [126.916000, 37.516000]
            }
            """.trimIndent()
        val loader =
            SearchMapStateLoader(
                incidentDetail = { notFoundResponse() },
                overallSearchArea = { notFoundResponse() },
                opSearchAreas = { _, _ -> notFoundResponse() },
                searchPaths = { notFoundResponse() },
                liveMarkers = { query: MarkerReadQuery ->
                    assertEquals(INCIDENT_ID, query.incidentId)
                    assertEquals(OP_ID, query.opId)
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "incidentId": "$INCIDENT_ID",
                          "markers": [
                            {
                              "id": "$MARKER_ID",
                              "incidentId": "$INCIDENT_ID",
                              "opId": "$OP_ID",
                              "type": "CLUE",
                              "status": "UPDATED",
                              "version": 5,
                              "location": $markerLocation,
                              "memo": "서버 최신 마커"
                            }
                          ]
                        }
                        """.trimIndent(),
                        errorCode = null
                    )
                },
                initialMarkers = { _, _ ->
                    initialMarkersCalled = true
                    notFoundResponse()
                }
            )

        val state =
            loader.load(
                SearchMapSessionContext(
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentDutyShiftId = DUTY_SHIFT_ID,
                    policePhoneId = POLICE_PHONE_ID
                )
            )

        val marker = state.layers.single { it.kind == SearchLayerKind.Marker }
        assertFalse(initialMarkersCalled)
        assertEquals("단서", marker.label)
        assertEquals(MARKER_ID, marker.overlayId)
        assertTrue(marker.highlighted)
        assertTrue(marker.geoJson!!.contains("126.916"))
        assertTrue(marker.geoJson!!.contains("37.516"))
        assertViewportBounds(
            state.viewportBounds,
            south = 37.513,
            west = 126.913,
            north = 37.519,
            east = 126.919
        )
    }

    @Test
    fun pendingLocalMarkersRenderBeforeServerReplay() = runBlocking {
        val loader =
            SearchMapStateLoader(
                incidentDetail = { notFoundResponse() },
                overallSearchArea = { notFoundResponse() },
                opSearchAreas = { _, _ -> notFoundResponse() },
                searchPaths = { notFoundResponse() },
                initialMarkers = { _, _ -> notFoundResponse() },
                pendingMarkers = { incidentId, policePhoneId ->
                    assertEquals(INCIDENT_ID, incidentId)
                    assertEquals(POLICE_PHONE_ID, policePhoneId)
                    listOf(
                        LocalMarkerEntity(
                            localMarkerId = MARKER_ID,
                            outboxId = "outbox-marker-001",
                            operationId = "22222222-2222-4222-8222-222222222001",
                            incidentId = INCIDENT_ID,
                            opId = OP_ID,
                            policePhoneId = POLICE_PHONE_ID,
                            type = "CLUE",
                            supportRequestType = null,
                            memo = "수동 조정 좌표",
                            lon = 126.970321,
                            lat = 37.580321,
                            syncStatus = "PENDING_SEND",
                            createdAtMillis = 1_000L,
                            updatedAtMillis = 1_000L
                        )
                    )
                }
            )

        val state =
            loader.load(
                SearchMapSessionContext(
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentDutyShiftId = DUTY_SHIFT_ID,
                    policePhoneId = POLICE_PHONE_ID
                )
            )

        val marker = state.layers.single { it.kind == SearchLayerKind.Marker }
        assertEquals("단서 · 전송 대기", marker.label)
        assertEquals(MARKER_ID, marker.overlayId)
        assertTrue(marker.highlighted)
        assertTrue(marker.geoJson!!.contains("[126.970321,37.580321]"))
        assertViewportBounds(
            state.viewportBounds,
            south = 37.577321,
            west = 126.967321,
            north = 37.583321,
            east = 126.973321
        )
    }

    @Test
    fun missingPolicePhoneDoesNotReadInitialMarkers() = runBlocking {
        var initialMarkersCalled = false
        val loader =
            SearchMapStateLoader(
                incidentDetail = { notFoundResponse() },
                overallSearchArea = { notFoundResponse() },
                opSearchAreas = { _, _ -> notFoundResponse() },
                searchPaths = { notFoundResponse() },
                initialMarkers = { _, _ ->
                    initialMarkersCalled = true
                    notFoundResponse()
                }
            )

        loader.load(
            SearchMapSessionContext(
                incidentId = INCIDENT_ID,
                currentOpId = OP_ID,
                currentDutyShiftId = DUTY_SHIFT_ID,
                policePhoneId = null
            )
        )

        assertFalse(initialMarkersCalled)
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
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentDutyShiftId = DUTY_SHIFT_ID
                )
            )

        assertEquals(null, state.viewportBounds)
        assertTrue(state.layers.all { layer -> layer.geoJson == null })
        assertFalse(state.visibleText().any { it == "담당 구역 확인 중" })
        assertTrue(state.assignmentLabel.isBlank())
        assertEquals("담당구역 미배정", state.assignmentDisplayLabel)
        assertFalse(state.visibleText().contains("담당구역 미배정"))
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
                    incidentId = INCIDENT_ID,
                    currentOpId = null,
                    currentDutyShiftId = DUTY_SHIFT_ID
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
                    incidentId = INCIDENT_ID,
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
        assertTrue(source.contains("SearchPathLocalRecorder"))
        assertTrue(source.contains("SearchPathGpsBatchRecorder"))
        assertTrue(source.contains("AndroidLocationUpdates"))
        assertTrue(source.contains("latestLocationFix"))
        assertTrue(source.contains("latestGpsLocationFix"))
        assertTrue(source.contains("lastKnownFix"))
        assertTrue(source.contains("withCurrentLocationViewport"))
        assertTrue(source.contains("recordFix"))
        assertTrue(source.contains("gpsBatchRecorder.flush"))
        assertTrue(source.contains("gpsBatchRecorder.clear"))
        assertTrue(source.contains("ClockSyncState"))
        assertTrue(source.contains("syncClockForIncident"))
        assertTrue(source.contains("clockOffsetMs = clockSyncState::clockOffsetMs"))
        assertTrue(source.contains("clockSyncedAt = clockSyncState::clockSyncedAt"))
        assertTrue(source.contains("RoomSyncClient(database.outboxDao(), database.localWriteDraftDao())"))
        assertTrue(source.contains("outboxSummaryForWarnings?.normalUnsentCount"))
        assertTrue(source.contains("outboxReplayScheduler.schedule"))
        assertTrue(source.contains("SearchLifecycleStatus.Stopped"))
        assertFalse(source.contains("onPrimaryLifecycleAction = {}"))
        assertFalse(source.contains("onStopSearch = {}"))
        assertTrue(source.contains("MarkerLocalRecorder"))
        assertTrue(source.contains("MarkerRepository"))
        assertTrue(source.contains("listMarkers"))
        assertTrue(source.contains("onOpenFocusedMarkerDetail"))
        assertTrue(source.contains("MarkerDetailDeepLink.route(markerId)"))
        assertTrue(source.contains("createMarker"))
        assertTrue(source.contains("MarkerUpsertInput"))
        assertTrue(source.contains("markerCreationLocation"))
        assertTrue(source.contains(".withCurrentLocation(currentGpsLocation.toMarkerLocation())"))
        assertTrue(source.contains("markerSheetState.withManualLocation(displayedSearchMapState.markerCreationLocation())"))
        assertTrue(source.contains("localMarkerDao"))
        assertTrue(source.contains("toMarkerUpsertInput()"))
        assertFalse(source.contains("toMarkerUpsertInput(searchMapState.markerCreationLocation())"))
        assertFalse(source.contains("onSave = { markerSheetOpen = false }"))
        assertTrue(source.contains("OfflinePackageRepository"))
        assertTrue(source.contains("initialMarkers"))
    }

    @Test
    fun appSearchMapRouteResetsPathStateWhenSessionContextChanges() {
        val source = java.io.File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()
        val routeIndex = source.indexOf("private fun SearchMapRoute")
        val stateRememberIndex = source.indexOf("var searchMapState by remember(", routeIndex)
        val stateRememberEnd = source.indexOf(") {", stateRememberIndex)
        val boundaryMonitorIndex = source.indexOf("val boundaryMonitor = remember(", routeIndex)
        val boundaryMonitorEnd = source.indexOf(") { SearchAreaBoundaryMonitor() }", boundaryMonitorIndex)

        assertTrue(routeIndex >= 0)
        assertTrue(stateRememberIndex >= 0)
        assertTrue(stateRememberEnd > stateRememberIndex)
        assertTrue(boundaryMonitorIndex >= 0)
        assertTrue(boundaryMonitorEnd > boundaryMonitorIndex)

        val stateRememberKeys = source.substring(stateRememberIndex, stateRememberEnd)
        assertTrue(stateRememberKeys.contains("sessionContext.incidentId"))
        assertTrue(stateRememberKeys.contains("sessionContext.currentOpId"))
        assertTrue(stateRememberKeys.contains("sessionContext.policePhoneId"))

        val boundaryMonitorKeys = source.substring(boundaryMonitorIndex, boundaryMonitorEnd)
        assertTrue(boundaryMonitorKeys.contains("sessionContext.incidentId"))
        assertTrue(boundaryMonitorKeys.contains("sessionContext.currentOpId"))
        assertTrue(boundaryMonitorKeys.contains("sessionContext.policePhoneId"))
    }

    @Test
    fun appSearchMapRoutePollsServerStateWhileMapIsOpen() {
        val source = java.io.File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()
        val routeIndex = source.indexOf("private fun SearchMapRoute")
        val refreshConstantIndex = source.indexOf("private const val SEARCH_MAP_SERVER_REFRESH_MS = 10_000L")
        val refreshEffectIndex = source.indexOf("LaunchedEffect(loader, sessionContext, focusMarkerId)", routeIndex)
        val nextEffectIndex = source.indexOf("DisposableEffect(context)", refreshEffectIndex)

        assertTrue(routeIndex >= 0)
        assertTrue(refreshConstantIndex >= 0)
        assertTrue(refreshEffectIndex > routeIndex)
        assertTrue(nextEffectIndex > refreshEffectIndex)

        val refreshEffect = source.substring(refreshEffectIndex, nextEffectIndex)
        assertTrue(refreshEffect.contains("suspend fun refreshServerState()"))
        assertTrue(refreshEffect.contains("loader.load(sessionContext).withFocusedMarker(focusMarkerId)"))
        assertTrue(refreshEffect.contains("outboxDao.observeStatusSummary"))
        assertTrue(refreshEffect.contains("while (true)"))
        assertTrue(refreshEffect.contains("delay(SEARCH_MAP_SERVER_REFRESH_MS)"))
        assertTrue(refreshEffect.contains("refreshServerState()"))
    }

    private fun fallbackOnlyLoader(): SearchMapStateLoader =
        SearchMapStateLoader(
            incidentDetail = { notFoundResponse() },
            overallSearchArea = { notFoundResponse() },
            opSearchAreas = { _, _ -> notFoundResponse() }
        )

    private fun notFoundResponse(): SuriMapApiResponse =
        SuriMapApiResponse(statusCode = 404, body = null, errorCode = null)

    private fun assertViewportBounds(
        actual: SearchMapViewportBounds?,
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ) {
        requireNotNull(actual)
        assertEquals(south, actual.south, 0.000001)
        assertEquals(west, actual.west, 0.000001)
        assertEquals(north, actual.north, 0.000001)
        assertEquals(east, actual.east, 0.000001)
    }

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val OP_ID = opIdFixture("precinct-first-001")
        val DUTY_SHIFT_ID = dutyShiftIdFixture("precinct-day-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-001")
        val OTHER_POLICE_PHONE_ID = policePhoneIdFixture("precinct-002")
        val OVERALL_AREA_ID = areaIdFixture("overall-001")
        val UNIT_AREA_ID = areaIdFixture("unit-001")
        val TEAM_AREA_ID = areaIdFixture("team-001")
        val TEAM_AREA_ID_2 = areaIdFixture("team-002")
        val PATH_ID = pathIdFixture("001")
        val OTHER_PATH_ID = pathIdFixture("002")
        val MARKER_ID = markerIdFixture("clue-001")
    }
}
