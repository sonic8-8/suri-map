package com.surimap.feature.search

import com.surimap.core.database.LocalMarkerEntity
import com.surimap.core.database.OutboxStatusSummary
import com.surimap.core.marker.MarkerReadQuery
import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.path.SearchPathQuery
import com.surimap.feature.search.data.SearchMapSessionContext
import com.surimap.feature.search.data.SearchMapResponseCache
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
                          "openedAt": "2026-05-28T00:10:00Z",
                          "version": 8,
                          "missingPerson": {
                            "displayName": "김실종",
                            "photoUrl": "/mock-upload/missing-person/kim.jpg",
                            "appearanceText": "회색 점퍼",
                            "lastSeenLocationText": "무등산 증심사 입구",
                            "lastSeenAt": "2026-05-27T23:40:00Z"
                          },
                          "assignments": [
                            {
                              "accountId": "acct-command",
                              "accountDisplayName": "광주 실종팀 상황반",
                              "accountType": "COMMAND",
                              "organizationType": "MISSING_TEAM",
                              "incidentRole": "INCIDENT_COMMANDER",
                              "assignedAt": "2026-05-28T00:12:00Z"
                            },
                            {
                              "accountId": "acct-field",
                              "accountDisplayName": "기동대 1부대 A팀",
                              "accountType": "TEAM",
                              "organizationType": "SUPPORT_UNIT",
                              "incidentRole": "FIELD_COMMANDER",
                              "assignedAt": "2026-05-28T00:15:00Z"
                            },
                            {
                              "accountId": "acct-member",
                              "accountDisplayName": "광산 31호",
                              "accountType": "PATROL_CAR",
                              "organizationType": "POLICE_SUBSTATION",
                              "incidentRole": "MEMBER",
                              "assignedAt": "2026-05-28T00:18:00Z"
                            }
                          ]
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
                    currentDutyShiftId = DUTY_SHIFT_ID,
                    apiBaseUrl = "https://api.surimap.test/api",
                    objectStorageBaseUrl = "https://storage.surimap.test/api"
                )
            )

        assertEquals("광주 북구 산악 실종", state.incidentTitle)
        assertEquals("김실종 · 회색 점퍼", state.missingPersonSummary)
        assertEquals("진행 중", state.incidentStatusLabel)
        assertEquals("2026-05-28 09:10", state.openedAtLabel)
        assertEquals("김실종", state.missingPersonName)
        assertEquals("https://storage.surimap.test/mock-upload/missing-person/kim.jpg", state.missingPersonPhotoUrl)
        assertEquals("2026-05-28 08:40", state.lastSeenAtLabel)
        assertEquals("무등산 증심사 입구", state.lastSeenLocationLabel)
        assertEquals("회색 점퍼", state.appearanceLabel)
        assertEquals("3개", state.assignmentCountLabel)
        assertEquals("사건 지휘 1 · 현장 지휘 1 · 수색 대원 1", state.assignmentRoleSummary)
        assertEquals(3, state.assignmentItems.size)
        assertEquals("광주 실종팀 상황반", state.assignmentItems[0].displayName)
        assertEquals("사건 지휘", state.assignmentItems[0].roleLabel)
        assertEquals("지휘", state.assignmentItems[0].accountTypeLabel)
        assertEquals("실종팀", state.assignmentItems[0].organizationLabel)
        assertEquals("2026-05-28 09:12", state.assignmentItems[0].assignedAtLabel)
        assertEquals("기동대 1부대 A팀", state.assignmentItems[1].displayName)
        assertEquals("현장 지휘", state.assignmentItems[1].roleLabel)
        assertEquals("광산 31호", state.assignmentItems[2].displayName)
        assertEquals("수색 대원", state.assignmentItems[2].roleLabel)
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
        assertEquals("#f97316", state.layers[0].visualStyle?.lineColor)
        assertEquals(SearchLayerKind.Unit, state.layers[1].kind)
        assertEquals("기동대 1부대", state.layers[1].label)
        assertEquals(UNIT_AREA_ID, state.layers[1].overlayId)
        assertTrue(state.layers[1].geoJson!!.contains("\"Polygon\""))
        assertEquals("#22c55e", state.layers[1].visualStyle?.lineColor)
        assertEquals(SearchLayerKind.Team, state.layers[2].kind)
        assertEquals("A팀 담당 구역", state.layers[2].label)
        assertEquals(TEAM_AREA_ID, state.layers[2].overlayId)
        assertTrue(state.layers[2].highlighted)
        assertEquals("#a855f7", state.layers[2].visualStyle?.lineColor)
        assertEquals("A팀 담당 구역", state.assignmentLabel)
    }

    @Test
    fun searchAreasUseServerColorTokenBeforeLocalFallbackColor() = runBlocking {
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
                              "colorToken": "AREA_CYAN_01",
                              "geometry": $areaGeometry
                            },
                            {
                              "id": "$TEAM_AREA_ID",
                              "opId": "$OP_ID",
                              "parentAreaId": "$UNIT_AREA_ID",
                              "areaLevel": "TEAM",
                              "name": "A팀 담당 구역",
                              "status": "ACTIVE",
                              "colorToken": "AREA_AMBER_02",
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

        val unitLayer = state.layers.single { it.overlayId == UNIT_AREA_ID }
        val teamLayer = state.layers.single { it.overlayId == TEAM_AREA_ID }
        assertEquals("#06b6d4", unitLayer.visualStyle?.lineColor)
        assertEquals("#d97706", teamLayer.visualStyle?.lineColor)
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
    fun unitAreaLabelDoesNotFallbackAsAssignmentWhenTeamAreaIsMissing() = runBlocking {
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

        assertTrue(state.assignmentLabel.isBlank())
        assertEquals("담당구역 미배정", state.assignmentDisplayLabel)
    }

    @Test
    fun incidentSearchPathsUseOnlyAccountIdToFindCurrentRecordingPath() = runBlocking {
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
                              "accountId": "$ACCOUNT_ID",
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
                              "policePhoneId": "$POLICE_PHONE_ID",
                              "accountId": "$OTHER_ACCOUNT_ID",
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
                    policePhoneId = POLICE_PHONE_ID,
                    accountId = ACCOUNT_ID
                )
            )

        val pathLayers = state.layers.filter { it.kind == SearchLayerKind.Path }
        val positionLayers = state.layers.filter { it.kind == SearchLayerKind.CurrentLocation }
        assertEquals(SearchLayerKind.Overall, state.layers[0].kind)
        assertEquals(SearchLayerKind.Team, state.layers[1].kind)
        assertEquals(2, pathLayers.size)
        assertEquals(2, positionLayers.size)
        assertEquals("현재 경로", pathLayers[0].label)
        assertEquals("다른 대원 경로", pathLayers[1].label)
        assertEquals(PATH_ID, pathLayers[0].overlayId)
        assertEquals(OTHER_PATH_ID, pathLayers[1].overlayId)
        assertEquals(PATH_ID, state.activeSearchPathId)
        assertTrue(pathLayers[0].highlighted)
        assertFalse(pathLayers[1].highlighted)
        assertEquals("#22c55e", pathLayers[0].visualStyle?.lineColor)
        assertEquals("#22c55e", pathLayers[1].visualStyle?.lineColor)
        assertEquals("현재 위치", positionLayers[0].label)
        assertEquals("다른 대원 위치", positionLayers[1].label)
        assertTrue(positionLayers[0].highlighted)
        assertFalse(positionLayers[1].highlighted)
        assertEquals("#22c55e", positionLayers[0].visualStyle?.lineColor)
        assertEquals("#22c55e", positionLayers[1].visualStyle?.lineColor)
        assertTrue(pathLayers[0].geoJson!!.contains("\"LineString\""))
        assertTrue(pathLayers[1].geoJson!!.contains("\"LineString\""))
        assertTrue(positionLayers[0].geoJson!!.contains("\"Point\""))
        assertTrue(positionLayers[0].geoJson!!.contains("126.924"))
        assertEquals("경로 2개 표시", state.movementSummary)
        assertEquals(1779079992331L, state.activeSearchPathStartedAtEpochMs)
    }

    @Test
    fun cachedMapResponsesRenderImmediatelyWithoutNetworkFetch() = runBlocking {
        val areaGeometry =
            """{"type":"Polygon","coordinates":[[[126.91,37.51],[126.93,37.51],[126.93,37.53],[126.91,37.53],[126.91,37.51]]]}"""
        val pathGeometry =
            """{"type":"LineString","coordinates":[[126.912,37.512],[126.918,37.518]]}"""
        val cache =
            InMemorySearchMapResponseCache(
                mapOf(
                    "overall_search_area" to
                        """{"id":"$OVERALL_AREA_ID","incidentId":"$INCIDENT_ID","areaLevel":"OVERALL","status":"ACTIVE","geometry":$areaGeometry}""",
                    "op_search_areas" to
                        """{"areas":[{"id":"$TEAM_AREA_ID","opId":"$OP_ID","areaLevel":"TEAM","name":"A팀 담당 구역","status":"ACTIVE","geometry":$areaGeometry}]}""",
                    "search_paths" to
                        """{"paths":[{"id":"$PATH_ID","status":"RECORDING","incidentId":"$INCIDENT_ID","opId":"$OP_ID","accountId":"$ACCOUNT_ID","startedAt":"2026-05-18T04:53:12.331Z","geometry":$pathGeometry}]}""",
                    "live_markers" to
                        """{"markers":[{"id":"$MARKER_ID","type":"CLUE","status":"ACTIVE","location":{"type":"Point","coordinates":[126.919,37.519]}}]}"""
                )
            )
        val loader =
            SearchMapStateLoader(
                incidentDetail = { error("cached render must not fetch incident detail") },
                overallSearchArea = { error("cached render must not fetch overall area") },
                opSearchAreas = { _, _ -> error("cached render must not fetch op areas") },
                searchPaths = { error("cached render must not fetch search paths") },
                liveMarkers = { error("cached render must not fetch live markers") },
                responseCache = cache
            )

        val state =
            loader.cached(
                SearchMapSessionContext(
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentDutyShiftId = DUTY_SHIFT_ID,
                    policePhoneId = POLICE_PHONE_ID,
                    accountId = ACCOUNT_ID
                )
            )

        requireNotNull(state)
        assertTrue(state.layers.any { layer -> layer.kind == SearchLayerKind.Overall })
        assertTrue(state.layers.any { layer -> layer.kind == SearchLayerKind.Team && layer.label == "A팀 담당 구역" })
        assertTrue(state.layers.any { layer -> layer.kind == SearchLayerKind.Path && layer.overlayId == PATH_ID })
        assertTrue(state.layers.any { layer -> layer.kind == SearchLayerKind.Marker && layer.overlayId == MARKER_ID })
        assertEquals(PATH_ID, state.activeSearchPathId)
        assertEquals("경로 1개 표시", state.movementSummary)
    }

    @Test
    fun remoteMapLoadStoresResponsesForNextCachedRender() = runBlocking {
        val cache = InMemorySearchMapResponseCache()
        val loader =
            SearchMapStateLoader(
                incidentDetail = { notFoundResponse() },
                overallSearchArea = {
                    SuriMapApiResponse(
                        statusCode = 200,
                        body = """{"id":"$OVERALL_AREA_ID","geometry":{"type":"Polygon","coordinates":[[[126.91,37.51],[126.92,37.51],[126.92,37.52],[126.91,37.52],[126.91,37.51]]]}}""",
                        errorCode = null
                    )
                },
                opSearchAreas = { _, _ -> notFoundResponse() },
                responseCache = cache
            )

        loader.load(
            SearchMapSessionContext(
                incidentId = INCIDENT_ID,
                currentOpId = OP_ID,
                currentDutyShiftId = DUTY_SHIFT_ID,
                policePhoneId = POLICE_PHONE_ID
            )
        )

        assertTrue(cache.responses.containsKey("overall_search_area"))
    }

    @Test
    fun unchangedMapRevisionsReuseCachedResponsesWithoutHeavyFetches() = runBlocking {
        val areaGeometry =
            """{"type":"Polygon","coordinates":[[[126.91,37.51],[126.92,37.51],[126.92,37.52],[126.91,37.52],[126.91,37.51]]]}"""
        val pathGeometry = """{"type":"LineString","coordinates":[[126.912,37.512],[126.918,37.518]]}"""
        val revisions =
            mapOf(
                "incident_detail" to "incident-rev",
                "overall_search_area" to "overall-rev",
                "op_search_areas" to "areas-rev",
                "search_paths" to "paths-rev",
                "live_markers" to "markers-rev"
            )
        val cache =
            InMemorySearchMapResponseCache(
                initialResponses =
                    mapOf(
                        "incident_detail" to """{"id":"$INCIDENT_ID","title":"캐시 사건","missingPerson":{"displayName":"홍길동"}}""",
                        "overall_search_area" to """{"id":"$OVERALL_AREA_ID","geometry":$areaGeometry}""",
                        "op_search_areas" to """{"areas":[{"id":"$TEAM_AREA_ID","areaLevel":"TEAM","name":"A팀","geometry":$areaGeometry}]}""",
                        "search_paths" to """{"paths":[{"id":"$PATH_ID","status":"RECORDING","accountId":"$ACCOUNT_ID","startedAt":"2026-05-18T04:53:12.331Z","geometry":$pathGeometry}]}""",
                        "live_markers" to """{"markers":[{"id":"$MARKER_ID","type":"CLUE","status":"ACTIVE","location":{"type":"Point","coordinates":[126.919,37.519]}}]}"""
                    ),
                initialRevisions = revisions
            )
        val loader =
            SearchMapStateLoader(
                incidentDetail = { error("unchanged incident detail must not be fetched") },
                overallSearchArea = { error("unchanged overall area must not be fetched") },
                opSearchAreas = { _, _ -> error("unchanged op areas must not be fetched") },
                searchPaths = { error("unchanged search paths must not be fetched") },
                liveMarkers = { error("unchanged live markers must not be fetched") },
                mapRevisions = {
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                            """
                            {
                              "sources": [
                                {"source":"incident_detail","revision":"incident-rev"},
                                {"source":"overall_search_area","revision":"overall-rev"},
                                {"source":"op_search_areas","revision":"areas-rev"},
                                {"source":"search_paths","revision":"paths-rev"},
                                {"source":"live_markers","revision":"markers-rev"}
                              ]
                            }
                            """.trimIndent(),
                        errorCode = null
                    )
                },
                responseCache = cache
            )

        val state =
            loader.load(
                SearchMapSessionContext(
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentDutyShiftId = DUTY_SHIFT_ID,
                    policePhoneId = POLICE_PHONE_ID,
                    accountId = ACCOUNT_ID
                )
            )

        assertEquals("캐시 사건", state.incidentTitle)
        assertTrue(state.layers.any { layer -> layer.overlayId == PATH_ID })
        assertTrue(state.layers.any { layer -> layer.overlayId == MARKER_ID })
    }

    @Test
    fun changedAreaColorRecolorsCachedSearchPathsWithoutPathFetch() = runBlocking {
        val areaGeometry =
            """{"type":"Polygon","coordinates":[[[126.91,37.51],[126.93,37.51],[126.93,37.53],[126.91,37.53],[126.91,37.51]]]}"""
        val pathGeometry = """{"type":"LineString","coordinates":[[126.912,37.512],[126.918,37.518]]}"""
        val cache =
            InMemorySearchMapResponseCache(
                initialResponses =
                    mapOf(
                        "op_search_areas" to
                            """{"areas":[{"id":"$TEAM_AREA_ID","opId":"$OP_ID","areaLevel":"TEAM","name":"A팀","status":"ACTIVE","colorToken":"AREA_GREEN_01","geometry":$areaGeometry}]}""",
                        "search_paths" to
                            """{"paths":[{"id":"$PATH_ID","status":"RECORDING","incidentId":"$INCIDENT_ID","opId":"$OP_ID","accountId":"$ACCOUNT_ID","startedAt":"2026-05-18T04:53:12.331Z","geometry":$pathGeometry}]}"""
                    ),
                initialRevisions =
                    mapOf(
                        "op_search_areas" to "areas-old",
                        "search_paths" to "paths-rev"
                    )
            )
        val loader =
            SearchMapStateLoader(
                incidentDetail = { notFoundResponse() },
                overallSearchArea = { notFoundResponse() },
                opSearchAreas = { _, _ ->
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                            """{"areas":[{"id":"$TEAM_AREA_ID","opId":"$OP_ID","areaLevel":"TEAM","name":"A팀","status":"ACTIVE","colorToken":"AREA_ROSE_01","geometry":$areaGeometry}]}""",
                        errorCode = null
                    )
                },
                searchPaths = { error("unchanged search paths must be recolored from cache without fetching") },
                mapRevisions = {
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                            """
                            {
                              "sources": [
                                {"source":"op_search_areas","revision":"areas-new"},
                                {"source":"search_paths","revision":"paths-rev"}
                              ]
                            }
                            """.trimIndent(),
                        errorCode = null
                    )
                },
                responseCache = cache
            )

        val state =
            loader.load(
                SearchMapSessionContext(
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentDutyShiftId = DUTY_SHIFT_ID,
                    policePhoneId = POLICE_PHONE_ID,
                    accountId = ACCOUNT_ID
                )
            )

        val areaLayer = state.layers.single { layer -> layer.kind == SearchLayerKind.Team }
        val pathLayer = state.layers.single { layer -> layer.kind == SearchLayerKind.Path }
        assertEquals("#e11d48", areaLayer.visualStyle?.lineColor)
        assertEquals("#e11d48", pathLayer.visualStyle?.lineColor)
    }

    @Test
    fun missingAccountDoesNotReadSearchPaths() = runBlocking {
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
                    policePhoneId = POLICE_PHONE_ID,
                    accountId = null
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
                              "accountId": "$ACCOUNT_ID",
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
                    policePhoneId = POLICE_PHONE_ID,
                    accountId = ACCOUNT_ID
                )
            )

        val pathLayer = state.layers.first { it.kind == SearchLayerKind.Path }
        val latestLocationLayer = state.layers.first { it.kind == SearchLayerKind.CurrentLocation }
        val markerLayer = state.layers.first { it.kind == SearchLayerKind.Marker }
        assertEquals(PATH_ID, pathLayer.overlayId)
        assertEquals("$PATH_ID-latest-location", latestLocationLayer.overlayId)
        assertEquals("단서", markerLayer.label)
        assertEquals(MARKER_ID, markerLayer.overlayId)
        assertFalse(markerLayer.highlighted)
        assertTrue(markerLayer.geoJson!!.contains("\"Point\""))
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
        assertFalse(marker.highlighted)
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
        assertFalse(marker.highlighted)
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
            ).copy(bottomPanelExpanded = true)

        assertEquals(SearchLifecycleStatus.OpRequired, state.lifecycleStatus)
        assertFalse(state.canWritePath)
        assertFalse(state.canCreateMarker)
        assertTrue(state.visibleText().any { it.contains("수색 차수 확인") })
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
        assertTrue(source.contains("markerDetailModalId = markerId"))
        assertTrue(source.contains("MarkerDetailModal("))
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
        assertTrue(stateRememberKeys.contains("sessionContext.accountId"))

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

        val routeBody = source.substring(routeIndex, nextEffectIndex)
        val refreshEffect = source.substring(refreshEffectIndex, nextEffectIndex)
        assertTrue(routeBody.contains("suspend fun loadServerStatePreservingMapContent()"))
        assertTrue(routeBody.contains("loader.cached(sessionContext)"))
        assertTrue(routeBody.contains(".preserveMapContentFrom(searchMapState)"))
        assertTrue(routeBody.contains(".restoreViewport(initialRestoredViewportBounds)"))
        assertTrue(refreshEffect.contains("suspend fun refreshServerState()"))
        assertTrue(refreshEffect.contains("loadServerStatePreservingMapContent()"))
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

    private class InMemorySearchMapResponseCache(
        initialResponses: Map<String, String> = emptyMap(),
        initialRevisions: Map<String, String> = emptyMap()
    ) : SearchMapResponseCache {
        val responses = initialResponses.toMutableMap()
        private val sourceRevisions = initialRevisions.toMutableMap()

        override suspend fun read(
            context: SearchMapSessionContext,
            source: String
        ): String? = responses[source]

        override suspend fun revisions(context: SearchMapSessionContext): Map<String, String> =
            sourceRevisions.toMap()

        override suspend fun upsertIfChanged(
            context: SearchMapSessionContext,
            source: String,
            body: String,
            sourceRevision: String?
        ) {
            responses[source] = body
            sourceRevision?.takeIf(String::isNotBlank)?.let { revision ->
                sourceRevisions[source] = revision
            }
        }
    }

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val OP_ID = opIdFixture("precinct-first-001")
        val DUTY_SHIFT_ID = dutyShiftIdFixture("precinct-day-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-001")
        val OTHER_POLICE_PHONE_ID = policePhoneIdFixture("precinct-002")
        const val ACCOUNT_ID = "11111111-1111-1111-1111-111111110003"
        const val OTHER_ACCOUNT_ID = "11111111-1111-1111-1111-111111110004"
        val OVERALL_AREA_ID = areaIdFixture("overall-001")
        val UNIT_AREA_ID = areaIdFixture("unit-001")
        val TEAM_AREA_ID = areaIdFixture("team-001")
        val TEAM_AREA_ID_2 = areaIdFixture("team-002")
        val PATH_ID = pathIdFixture("001")
        val OTHER_PATH_ID = pathIdFixture("002")
        val MARKER_ID = markerIdFixture("clue-001")
    }
}
