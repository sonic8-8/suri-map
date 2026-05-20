package com.surimap.feature.handover

import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.operationalperiod.DutyShiftQuery
import com.surimap.core.operationalperiod.HandoverMemoQuery
import com.surimap.core.operationalperiod.HandoverTimelineQuery
import com.surimap.core.operationalperiod.SearchHistorySummaryQuery
import com.surimap.feature.handover.data.DutyHandoverStateLoader
import com.surimap.feature.handover.data.HandoverSessionContext
import com.surimap.feature.handover.ui.HandoverRecordScope
import com.surimap.feature.handover.ui.SearchHistorySummaryStatus
import com.surimap.feature.handover.ui.SummarySourceReadiness
import com.surimap.testing.dutyShiftIdFixture
import com.surimap.testing.handoverMemoIdFixture
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.opIdFixture
import com.surimap.testing.policePhoneIdFixture
import com.surimap.testing.searchHistorySummaryIdFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DutyHandoverStateLoaderTest {

    @Test
    fun handoverTimelineResponseMapsToReplayStateAndSummaryEvidence() = runBlocking {
        var memoCalled = false
        var summaryCalled = false
        val loader =
            DutyHandoverStateLoader(
                handoverTimeline = { operationalPeriodId, query: HandoverTimelineQuery ->
                    assertEquals(OP_ID, operationalPeriodId)
                    assertEquals(INCIDENT_ID, query.incidentId)
                    assertEquals("DUTY_SHIFT", query.scopeType)
                    assertEquals(DUTY_SHIFT_ID, query.dutyShiftId)
                    ok(
                        """
                        {
                          "incidentId": "$INCIDENT_ID",
                          "operationalPeriodId": "$OP_ID",
                          "scope": {
                            "scopeType": "DUTY_SHIFT",
                            "dutyShiftId": "$DUTY_SHIFT_ID",
                            "startedAt": "2026-05-11T03:00:00Z",
                            "endedAt": "2026-05-11T03:10:00Z"
                          },
                          "actors": [
                            {"actorId": "actor-1", "displayName": "순찰차 1", "colorKey": "blue"}
                          ],
                          "paths": [
                            {
                              "pathId": "path-1",
                              "actorId": "actor-1",
                              "mode": "FOOT",
                              "startedAt": "2026-05-11T03:00:00Z",
                              "endedAt": "2026-05-11T03:10:00Z",
                              "points": [
                                {"at": "2026-05-11T03:00:00Z", "lat": 37.1000, "lng": 127.1000, "accuracyMeters": 5},
                                {"at": "2026-05-11T03:10:00Z", "lat": 37.1100, "lng": 127.1200, "accuracyMeters": 7}
                              ]
                            }
                          ],
                          "events": [
                            {
                              "eventId": "marker-1",
                              "occurredAt": "2026-05-11T03:05:00Z",
                              "type": "MARKER",
                              "actorId": "actor-1",
                              "label": "마커 기록",
                              "detail": {
                                "markerType": "CLUE",
                                "memo": "배수로 입구 확인",
                                "location": {"lat": 37.1050, "lng": 127.1100},
                                "photoCount": 2
                              }
                            },
                            {
                              "eventId": "memo-1",
                              "occurredAt": "2026-05-11T03:08:00Z",
                              "type": "HANDOVER_MEMO",
                              "actorId": "actor-1",
                              "label": "인수인계 메모",
                              "detail": {"content": "북측 진입로 주민 진술 대기"}
                            }
                          ],
                          "metrics": {
                            "distanceMeters": 1840,
                            "walkingDistanceMeters": 1840,
                            "drivingDistanceMeters": 0,
                            "averageSpeedKmh": 2.1,
                            "stoppedSegmentCount": 0,
                            "markerCount": 1,
                            "handoverMemoCount": 1,
                            "syncStatus": "READY"
                          },
                          "summary": {
                            "id": "$SUMMARY_ID",
                            "status": "READY",
                            "displayStatus": "READY",
                            "content": "북측 진입로와 배수로 입구를 확인했습니다.",
                            "sourceReadiness": "READY",
                            "updatedAt": "2026-05-11T03:12:00Z"
                          }
                        }
                        """.trimIndent()
                    )
                },
                handoverMemos = {
                    memoCalled = true
                    ok("""{"items":[]}""")
                },
                searchHistorySummaries = { _, _ ->
                    summaryCalled = true
                    ok("""{"items":[]}""")
                }
            )

        val state = loader.load(CONTEXT)

        assertFalse(memoCalled)
        assertFalse(summaryCalled)
        assertEquals(HandoverRecordScope.DutyShift, state.recordScope)
        assertEquals(SearchHistorySummaryStatus.Ready, state.summaryStatus)
        assertEquals("북측 진입로와 배수로 입구를 확인했습니다.", state.summary)
        assertEquals(600_000L, state.replayControl.displayDurationMs)
        assertEquals(2, state.replayPoints.size)
        assertTrue(state.replayPathSegments.any { it.label.contains("순찰차 1") && it.modeLabel == "도보" })
        assertTrue(state.replayMarkers.any { it.title.contains("배수로 입구") && it.photoCountLabel == "사진 2장" })
        assertTrue(state.records.any { it.subtitle.contains("북측 진입로") })
        assertTrue(state.metrics.any { it.label == "총 이동" && it.value == "1.8km" })
    }

    @Test
    fun previousEndedDutyShiftIsDefaultReplayScopeWhenOptionsExist() = runBlocking {
        val timelineQueries = mutableListOf<HandoverTimelineQuery>()
        val loader =
            DutyHandoverStateLoader(
                dutyShifts = { query: DutyShiftQuery ->
                    assertEquals(INCIDENT_ID, query.incidentId)
                    assertEquals(OP_ID, query.opId)
                    assertEquals(POLICE_PHONE_ID, query.policePhoneId)
                    ok(
                        """
                        {
                          "items": [
                            {
                              "id": "$DUTY_SHIFT_ID",
                              "status": "ACTIVE",
                              "startedAt": "2026-05-11T04:00:00Z"
                            },
                            {
                              "id": "$PREVIOUS_DUTY_SHIFT_ID",
                              "status": "ENDED",
                              "startedAt": "2026-05-11T02:00:00Z"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                },
                handoverTimeline = { operationalPeriodId, query: HandoverTimelineQuery ->
                    assertEquals(OP_ID, operationalPeriodId)
                    timelineQueries += query
                    assertEquals("DUTY_SHIFT", query.scopeType)
                    assertEquals(PREVIOUS_DUTY_SHIFT_ID, query.dutyShiftId)
                    ok(
                        """
                        {
                          "incidentId": "$INCIDENT_ID",
                          "operationalPeriodId": "$OP_ID",
                          "scope": {
                            "scopeType": "DUTY_SHIFT",
                            "dutyShiftId": "$PREVIOUS_DUTY_SHIFT_ID",
                            "startedAt": "2026-05-11T02:00:00Z",
                            "endedAt": "2026-05-11T03:30:00Z"
                          },
                          "actors": [
                            {"actorId": "actor-1", "displayName": "이전 근무 폴리폰", "colorKey": "blue"}
                          ],
                          "paths": [
                            {
                              "pathId": "path-prev-1",
                              "actorId": "actor-1",
                              "mode": "FOOT",
                              "startedAt": "2026-05-11T02:05:00Z",
                              "endedAt": "2026-05-11T02:15:00Z",
                              "points": [
                                {"at": "2026-05-11T02:05:00Z", "lat": 37.1000, "lng": 127.1000},
                                {"at": "2026-05-11T02:15:00Z", "lat": 37.1020, "lng": 127.1030}
                              ]
                            }
                          ],
                          "events": [],
                          "metrics": {
                            "distanceMeters": 600,
                            "markerCount": 0,
                            "handoverMemoCount": 0,
                            "syncStatus": "READY"
                          },
                          "summary": {
                            "status": "READY",
                            "displayStatus": "READY",
                            "content": "이전 근무자가 산책로 동측을 확인했습니다.",
                            "sourceReadiness": "READY",
                            "updatedAt": "2026-05-11T03:32:00Z"
                          }
                        }
                        """.trimIndent()
                    )
                },
                handoverMemos = { ok("""{"items":[]}""") },
                searchHistorySummaries = { _, _ -> ok("""{"items":[]}""") }
            )

        val state = loader.load(CONTEXT)

        assertEquals(listOf(PREVIOUS_DUTY_SHIFT_ID), timelineQueries.map { it.dutyShiftId })
        assertEquals(HandoverRecordScope.DutyShift, state.recordScope)
        assertEquals("OP 3차 · 이전 근무", state.subtitle)
        assertTrue(state.dutyShiftOptions.any { it.dutyShiftId == PREVIOUS_DUTY_SHIFT_ID && it.selected })
        assertTrue(state.dutyShiftOptions.any { it.dutyShiftId == DUTY_SHIFT_ID && !it.selected })
        assertEquals("path-prev-1", state.replayPathSegments.single().sourceKey)
        assertEquals(2, state.replayPathSegments.single().points.size)
    }

    @Test
    fun emptyDutyShiftTimelineFallsBackToOpTimelineWithOpLabels() = runBlocking {
        val timelineQueries = mutableListOf<HandoverTimelineQuery>()
        var memoCalled = false
        var summaryCalled = false
        val loader =
            DutyHandoverStateLoader(
                dutyShifts = {
                    ok(
                        """
                        {
                          "items": [
                            {
                              "id": "$DUTY_SHIFT_ID",
                              "status": "ACTIVE",
                              "startedAt": "2026-05-11T04:00:00Z"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                },
                handoverTimeline = { operationalPeriodId, query: HandoverTimelineQuery ->
                    assertEquals(OP_ID, operationalPeriodId)
                    timelineQueries += query
                    if (query.scopeType == "DUTY_SHIFT") {
                        ok(
                            """
                            {
                              "incidentId": "$INCIDENT_ID",
                              "operationalPeriodId": "$OP_ID",
                              "scope": {
                                "scopeType": "DUTY_SHIFT",
                                "dutyShiftId": "$DUTY_SHIFT_ID"
                              },
                              "actors": [],
                              "paths": [],
                              "events": [],
                              "metrics": {
                                "distanceMeters": 0,
                                "markerCount": 0,
                                "handoverMemoCount": 0,
                                "syncStatus": "READY"
                              }
                            }
                            """.trimIndent()
                        )
                    } else {
                        assertEquals("OP", query.scopeType)
                        ok(
                            """
                            {
                              "incidentId": "$INCIDENT_ID",
                              "operationalPeriodId": "$OP_ID",
                              "scope": {"scopeType": "OP"},
                              "actors": [
                                {"actorId": "actor-1", "displayName": "실종팀 1팀", "colorKey": "blue"}
                              ],
                              "paths": [
                                {
                                  "pathId": "path-op-1",
                                  "actorId": "actor-1",
                                  "mode": "VEHICLE",
                                  "startedAt": "2026-05-11T02:00:00Z",
                                  "endedAt": "2026-05-11T02:05:00Z",
                                  "points": [
                                    {"at": "2026-05-11T02:00:00Z", "lat": 37.1000, "lng": 127.1000},
                                    {"at": "2026-05-11T02:05:00Z", "lat": 37.1010, "lng": 127.1010}
                                  ]
                                }
                              ],
                              "events": [
                                {
                                  "eventId": "op-marker-1",
                                  "occurredAt": "2026-05-11T02:03:00Z",
                                  "type": "MARKER",
                                  "actorId": "actor-1",
                                  "label": "단서 마커",
                                  "detail": {
                                    "markerType": "CLUE",
                                    "memo": "산책로 입구 천 조각",
                                    "photoCount": 1
                                  }
                                }
                              ],
                              "metrics": {
                                "distanceMeters": 2100,
                                "markerCount": 1,
                                "handoverMemoCount": 0,
                                "syncStatus": "READY"
                              },
                              "summary": {
                                "status": "READY",
                                "displayStatus": "READY",
                                "content": "OP 2차에서 산책로 입구와 주변 경로를 확인했습니다.",
                                "sourceReadiness": "READY",
                                "updatedAt": "2026-05-11T02:10:00Z"
                              }
                            }
                            """.trimIndent()
                        )
                    }
                },
                handoverMemos = {
                    memoCalled = true
                    ok("""{"items":[]}""")
                },
                searchHistorySummaries = { _, _ ->
                    summaryCalled = true
                    ok("""{"items":[]}""")
                }
            )

        val state = loader.load(CONTEXT)

        assertEquals(listOf("DUTY_SHIFT", "OP"), timelineQueries.map { it.scopeType })
        assertFalse(memoCalled)
        assertFalse(summaryCalled)
        assertEquals(HandoverRecordScope.OperationalPeriod, state.recordScope)
        assertEquals("수색 이력 확인", state.title)
        assertEquals("OP 3차 · 수색 이력", state.subtitle)
        assertEquals(SearchHistorySummaryStatus.Ready, state.summaryStatus)
        assertEquals("OP 2차에서 산책로 입구와 주변 경로를 확인했습니다.", state.summary)
        assertTrue(state.dutyShiftOptions.isEmpty())
        val reportState = state.selectTab(com.surimap.feature.handover.ui.DutyHandoverTab.Report)
        assertTrue(reportState.visibleText().contains("OP 수색 이력 요약"))
        assertTrue(reportState.visibleText().contains("OP 개요"))
        assertFalse(reportState.visibleText().contains("이전 근무 요약"))
        assertFalse(reportState.visibleText().contains("근무 개요"))
        assertTrue(state.replayBadges.contains("OP 기준"))
        assertTrue(state.replayBadges.contains("복수 기록자"))
        assertEquals(2, state.replayPoints.size)
        assertTrue(state.records.any { it.subtitle.contains("산책로 입구") })
    }

    @Test
    fun handoverLoadCanDisableOpFallbackWhenDutyShiftTimelineIsEmpty() = runBlocking {
        val timelineQueries = mutableListOf<HandoverTimelineQuery>()
        val loader =
            DutyHandoverStateLoader(
                handoverTimeline = { _, query: HandoverTimelineQuery ->
                    timelineQueries += query
                    if (query.scopeType == "OP") {
                        throw AssertionError("인수인계 route에서는 OP fallback을 직접 표시하지 않는다.")
                    }
                    ok(
                        """
                        {
                          "incidentId": "$INCIDENT_ID",
                          "operationalPeriodId": "$OP_ID",
                          "scope": {"scopeType": "DUTY_SHIFT", "dutyShiftId": "$DUTY_SHIFT_ID"},
                          "actors": [],
                          "paths": [],
                          "events": [],
                          "metrics": {
                            "distanceMeters": 0,
                            "markerCount": 0,
                            "handoverMemoCount": 0,
                            "syncStatus": "READY"
                          }
                        }
                        """.trimIndent()
                    )
                },
                handoverMemos = { ok("""{"items":[]}""") },
                searchHistorySummaries = { _, _ -> ok("""{"items":[]}""") }
            )

        val state = loader.load(CONTEXT, allowOperationalPeriodFallback = false)

        assertEquals(listOf("DUTY_SHIFT"), timelineQueries.map { it.scopeType })
        assertEquals(HandoverRecordScope.DutyShift, state.recordScope)
        assertEquals("이전 근무 확인", state.title)
        assertEquals("OP 3차 · 교대 인수인계", state.subtitle)
        assertTrue(state.replayBadges.contains("근무 기준"))
        assertFalse(state.replayBadges.contains("OP 기준"))
    }

    @Test
    fun operationalPeriodSearchHistoryLoadsOpScopeDirectly() = runBlocking {
        val timelineQueries = mutableListOf<HandoverTimelineQuery>()
        var dutyShiftsCalled = false
        var memosCalled = false
        val loader =
            DutyHandoverStateLoader(
                dutyShifts = {
                    dutyShiftsCalled = true
                    ok("""{"items":[]}""")
                },
                handoverTimeline = { operationalPeriodId, query: HandoverTimelineQuery ->
                    assertEquals(OP_ID, operationalPeriodId)
                    timelineQueries += query
                    assertEquals("OP", query.scopeType)
                    ok(
                        """
                        {
                          "incidentId": "$INCIDENT_ID",
                          "operationalPeriodId": "$OP_ID",
                          "scope": {"scopeType": "OP"},
                          "actors": [
                            {"actorId": "actor-1", "displayName": "현장 기록자 1"}
                          ],
                          "paths": [
                            {
                              "pathId": "path-op-direct",
                              "actorId": "actor-1",
                              "mode": "FOOT",
                              "startedAt": "2026-05-11T02:00:00Z",
                              "endedAt": "2026-05-11T02:10:00Z",
                              "points": [
                                {"at": "2026-05-11T02:00:00Z", "lat": 37.1000, "lng": 127.1000},
                                {"at": "2026-05-11T02:10:00Z", "lat": 37.1020, "lng": 127.1030}
                              ]
                            }
                          ],
                          "events": [],
                          "metrics": {
                            "distanceMeters": 1200,
                            "markerCount": 0,
                            "handoverMemoCount": 0,
                            "syncStatus": "READY"
                          },
                          "summary": {
                            "status": "READY",
                            "content": "OP 3차 수색 이력을 확인했습니다.",
                            "sourceReadiness": "READY",
                            "updatedAt": "2026-05-11T02:12:00Z"
                          }
                        }
                        """.trimIndent()
                    )
                },
                handoverMemos = {
                    memosCalled = true
                    ok("""{"items":[]}""")
                }
            )

        val state = loader.loadOperationalPeriod(CONTEXT)

        assertEquals(listOf("OP"), timelineQueries.map { it.scopeType })
        assertFalse(dutyShiftsCalled)
        assertFalse(memosCalled)
        assertEquals(HandoverRecordScope.OperationalPeriod, state.recordScope)
        assertEquals("수색 이력 확인", state.title)
        assertEquals("OP 3차 · 수색 이력", state.subtitle)
        assertTrue(state.dutyShiftOptions.isEmpty())
        assertEquals("OP 3차 수색 이력을 확인했습니다.", state.summary)
        assertEquals("path-op-direct", state.replayPathSegments.single().sourceKey)
    }

    @Test
    fun readySummaryAndMemoReadResponsesMapToHandoverUiState() = runBlocking {
        val loader =
            DutyHandoverStateLoader(
                handoverMemos = { query: HandoverMemoQuery ->
                    assertEquals(INCIDENT_ID, query.incidentId)
                    assertEquals(OP_ID, query.opId)
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "items": [
                            {
                              "id": "$MEMO_ID",
                              "opId": "$OP_ID",
                              "memoTargetType": "OPERATIONAL_PERIOD",
                              "memoTargetId": "$OP_ID",
                              "content": "북측 진입로 주민 진술 대기",
                              "version": 1
                            }
                          ]
                        }
                        """.trimIndent(),
                        errorCode = null
                    )
                },
                searchHistorySummaries = { operationalPeriodId, query: SearchHistorySummaryQuery ->
                    assertEquals(OP_ID, operationalPeriodId)
                    assertEquals(INCIDENT_ID, query.incidentId)
                    assertEquals("DUTY_SHIFT", query.scopeType)
                    assertEquals(DUTY_SHIFT_ID, query.scopeId)
                    assertEquals(DUTY_SHIFT_ID, query.dutyShiftId)
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                        """
                        {
                          "items": [
                            {
                              "id": "$SUMMARY_ID",
                              "status": "READY",
                              "displayStatus": "READY",
                              "content": "동쪽 능선 수색 완료. 원본 메모를 확인하세요.",
                              "sourceReadiness": "READY",
                              "updatedAt": "2026-05-11T06:10:00Z"
                            }
                          ]
                        }
                        """.trimIndent(),
                        errorCode = null
                    )
                }
            )

        val state = loader.load(CONTEXT)

        assertEquals(SearchHistorySummaryStatus.Ready, state.summaryStatus)
        assertEquals(SummarySourceReadiness.Ready, state.sourceReadiness)
        assertEquals("동쪽 능선 수색 완료. 원본 메모를 확인하세요.", state.summary)
        assertTrue(state.generatedAtLabel.contains("2026-05-11T06:10:00Z"))
        assertEquals("OP 3차 · 교대 인수인계", state.subtitle)
        assertTrue(state.records.any { it.title.contains("OPERATIONAL_PERIOD") })
        assertTrue(state.records.any { it.subtitle.contains("북측 진입로") })
        assertEquals(MEMO_ID, state.records.single().sourceKey)
        assertTrue(state.metrics.any { it.label == "메모" && it.value == "1건" })
        assertFalse(state.canRequestSummaryGeneration)
        assertFalse(state.visibleText().any { it.contains("다시 생성") })
    }

    @Test
    fun pendingSyncSummaryMapsToGeneratingWithoutClientGenerationAction() = runBlocking {
        val loader =
            DutyHandoverStateLoader(
                handoverMemos = { ok("""{"items":[]}""") },
                searchHistorySummaries = { _, _ ->
                    ok(
                        """
                        {
                          "items": [
                            {
                              "id": "$PENDING_SUMMARY_ID",
                              "status": "GENERATING",
                              "sourceReadiness": "PENDING_SYNC"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                }
            )

        val state = loader.load(CONTEXT)

        assertEquals(SearchHistorySummaryStatus.Generating, state.summaryStatus)
        assertEquals(SummarySourceReadiness.PendingSync, state.sourceReadiness)
        assertFalse(state.canRequestSummaryGeneration)
        assertFalse(state.visibleText().any { it.contains("AI 요약 생성") || it.contains("다시 생성") })
    }

    @Test
    fun missingDutyShiftDoesNotReadOpScopedSummaryIntoHandoverUi() = runBlocking {
        var summaryCalled = false
        val loader =
            DutyHandoverStateLoader(
                handoverMemos = { ok("""{"items":[]}""") },
                searchHistorySummaries = { _, _ ->
                    summaryCalled = true
                    ok("""{"items":[{"scopeType":"OP","content":"OP 결과 브리핑"}]}""")
                }
            )

        val state = loader.load(CONTEXT.copy(dutyShiftId = null))

        assertFalse(summaryCalled)
        assertEquals(SearchHistorySummaryStatus.Empty, state.summaryStatus)
        assertFalse(state.visibleText().any { it.contains("OP 결과 브리핑") })
    }

    @Test
    fun missingIncidentOrOpDoesNotCallRepositories() = runBlocking {
        var memoCalled = false
        var summaryCalled = false
        val loader =
            DutyHandoverStateLoader(
                handoverMemos = {
                    memoCalled = true
                    ok("""{"items":[]}""")
                },
                searchHistorySummaries = { _, _ ->
                    summaryCalled = true
                    ok("""{"items":[]}""")
                }
            )

        val state = loader.load(CONTEXT.copy(opId = null))

        assertEquals(SearchHistorySummaryStatus.Empty, state.summaryStatus)
        assertTrue(state.records.isEmpty())
        assertFalse(memoCalled)
        assertFalse(summaryCalled)
    }

    private fun ok(body: String): SuriMapApiResponse =
        SuriMapApiResponse(statusCode = 200, body = body, errorCode = null)

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val OP_ID = opIdFixture("precinct-first-001")
        val DUTY_SHIFT_ID = dutyShiftIdFixture("precinct-first-001")
        val PREVIOUS_DUTY_SHIFT_ID = dutyShiftIdFixture("precinct-first-000")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-001")
        val MEMO_ID = handoverMemoIdFixture("memo-001")
        val SUMMARY_ID = searchHistorySummaryIdFixture("summary-001")
        val PENDING_SUMMARY_ID = searchHistorySummaryIdFixture("summary-pending")
        val CONTEXT =
            HandoverSessionContext(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                opLabel = "OP 3차",
                dutyShiftId = DUTY_SHIFT_ID,
                policePhoneId = POLICE_PHONE_ID
            )
    }
}
