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
                              "detail": {
                                "targetType": "SEARCH_AREA",
                                "targetId": "area-north-entrance",
                                "content": "북측 진입로 주민 진술 대기"
                              }
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
        assertEquals(300_000L, state.replayMarkers.single().elapsedMs)
        assertTrue(state.records.any { it.subtitle.contains("북측 진입로") })
        assertTrue(state.metrics.any { it.label == "총 이동" && it.value == "1.8km" })
        assertTrue(state.metrics.any { it.label == "도보" && it.value == "1.8km" })
        assertTrue(state.metrics.any { it.label == "차량" && it.value == "0m" })
        assertTrue(state.metrics.any { it.label == "평균 속도" && it.value == "2.1km/h" })
        assertTrue(state.metrics.any { it.label == "정지 구간" && it.value == "0회" })
        val markerRecord = state.records.first { it.sourceKey == "marker-1" }
        assertTrue(markerRecord.detailLines.any { it.contains("유형: 단서") })
        assertTrue(markerRecord.detailLines.any { it.contains("사진: 2장") })
        assertTrue(markerRecord.detailLines.any { it.contains("위치: 37.10500, 127.11000") })
        val memoRecord = state.records.first { it.sourceKey == "memo-1" }
        assertEquals("구역 메모", memoRecord.title)
        assertTrue(memoRecord.detailLines.any { it.contains("작성자: 순찰차 1") })
        assertTrue(memoRecord.detailLines.any { it.contains("대상: 구역") })
        assertTrue(memoRecord.detailLines.any { it.contains("내용: 북측 진입로 주민 진술 대기") })
        assertEquals(listOf(memoRecord), state.areaMemoRecords)
    }

    @Test
    fun currentDutyShiftIsDefaultReplayScopeAndPreviousUnconfirmedRecordIsHidden() = runBlocking {
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
                              "startedAt": "2026-05-11T04:00:00Z",
                              "policePhoneLabel": "현재 폴리폰"
                            },
                            {
                              "id": "$PREVIOUS_DUTY_SHIFT_ID",
                              "status": "ENDED",
                              "startedAt": "2026-05-11T02:00:00Z",
                              "endedAt": "2026-05-11T03:30:00Z",
                              "policePhoneLabel": "이전 근무 폴리폰"
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
                    assertEquals(DUTY_SHIFT_ID, query.dutyShiftId)
                    ok(
                        """
                        {
                          "incidentId": "$INCIDENT_ID",
                          "operationalPeriodId": "$OP_ID",
                          "scope": {
                            "scopeType": "DUTY_SHIFT",
                            "dutyShiftId": "$DUTY_SHIFT_ID",
                            "startedAt": "2026-05-11T04:00:00Z"
                          },
                          "actors": [
                            {"actorId": "actor-1", "displayName": "현재 폴리폰", "colorKey": "blue"}
                          ],
                          "paths": [
                            {
                              "pathId": "path-current-1",
                              "actorId": "actor-1",
                              "mode": "FOOT",
                              "startedAt": "2026-05-11T04:05:00Z",
                              "endedAt": "2026-05-11T04:15:00Z",
                              "points": [
                                {"at": "2026-05-11T04:05:00Z", "lat": 37.1000, "lng": 127.1000},
                                {"at": "2026-05-11T04:15:00Z", "lat": 37.1020, "lng": 127.1030}
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
                            "content": "현재 근무자가 산책로 동측을 확인했습니다.",
                            "sourceReadiness": "READY",
                            "updatedAt": "2026-05-11T04:16:00Z"
                          }
                        }
                        """.trimIndent()
                    )
                },
                handoverMemos = { ok("""{"items":[]}""") },
                searchHistorySummaries = { _, _ -> ok("""{"items":[]}""") }
            )

        val state = loader.load(CONTEXT)

        assertEquals(listOf(DUTY_SHIFT_ID), timelineQueries.map { it.dutyShiftId })
        assertEquals(HandoverRecordScope.DutyShift, state.recordScope)
        assertEquals("OP 3차 · 현재 근무 · 현재 폴리폰", state.subtitle)
        assertTrue(state.dutyShiftOptions.any { it.dutyShiftId == DUTY_SHIFT_ID && it.selected })
        assertFalse(state.dutyShiftOptions.any { it.dutyShiftId == PREVIOUS_DUTY_SHIFT_ID })
        assertEquals("path-current-1", state.replayPathSegments.single().sourceKey)
        assertEquals(2, state.replayPathSegments.single().points.size)
    }

    @Test
    fun currentDutyShiftReplayEvidenceDoesNotProbePreviousUnconfirmedDutyShift() = runBlocking {
        val timelineQueries = mutableListOf<HandoverTimelineQuery>()
        var memoCalled = false
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
                              "startedAt": "2026-05-11T04:00:00Z",
                              "policePhoneLabel": "현재 폴리폰"
                            },
                            {
                              "id": "$PREVIOUS_DUTY_SHIFT_ID",
                              "status": "ENDED",
                              "startedAt": "2026-05-11T02:00:00Z",
                              "endedAt": "2026-05-11T03:30:00Z",
                              "policePhoneLabel": "이전 근무 폴리폰"
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
                    when (query.dutyShiftId) {
                        PREVIOUS_DUTY_SHIFT_ID ->
                            ok(
                                """
                                {
                                  "incidentId": "$INCIDENT_ID",
                                  "operationalPeriodId": "$OP_ID",
                                  "scope": {
                                    "scopeType": "DUTY_SHIFT",
                                    "dutyShiftId": "$PREVIOUS_DUTY_SHIFT_ID"
                                  },
                                  "actors": [],
                                  "paths": [],
                                  "events": [],
                                  "metrics": {
                                    "distanceMeters": 0,
                                    "markerCount": 0,
                                    "handoverMemoCount": 0,
                                    "syncStatus": "READY"
                                  },
                                  "summary": {
                                    "status": "READY",
                                    "displayStatus": "READY",
                                    "content": "이전 근무 요약은 있지만 지도 경로는 없습니다.",
                                    "sourceReadiness": "READY",
                                    "updatedAt": "2026-05-11T03:32:00Z"
                                  }
                                }
                                """.trimIndent()
                            )

                        DUTY_SHIFT_ID ->
                            ok(
                                """
                                {
                                  "incidentId": "$INCIDENT_ID",
                                  "operationalPeriodId": "$OP_ID",
                                  "scope": {
                                    "scopeType": "DUTY_SHIFT",
                                    "dutyShiftId": "$DUTY_SHIFT_ID",
                                    "startedAt": "2026-05-11T04:00:00Z"
                                  },
                                  "actors": [
                                    {"actorId": "actor-current", "displayName": "현재 근무 폴리폰", "colorKey": "green"}
                                  ],
                                  "paths": [
                                    {
                                      "pathId": "path-current-1",
                                      "actorId": "actor-current",
                                      "mode": "FOOT",
                                      "startedAt": "2026-05-11T04:02:00Z",
                                      "endedAt": "2026-05-11T04:12:00Z",
                                      "points": [
                                        {"at": "2026-05-11T04:02:00Z", "lat": 37.1000, "lng": 127.1000},
                                        {"at": "2026-05-11T04:12:00Z", "lat": 37.1040, "lng": 127.1060}
                                      ]
                                    }
                                  ],
                                  "events": [],
                                  "metrics": {
                                    "distanceMeters": 720,
                                    "markerCount": 0,
                                    "handoverMemoCount": 0,
                                    "syncStatus": "READY"
                                  },
                                  "summary": {
                                    "status": "READY",
                                    "displayStatus": "READY",
                                    "content": "현재 근무 경로가 기록되어 있습니다.",
                                    "sourceReadiness": "READY",
                                    "updatedAt": "2026-05-11T04:13:00Z"
                                  }
                                }
                                """.trimIndent()
                            )

                        else -> throw AssertionError("unexpected dutyShiftId ${query.dutyShiftId}")
                    }
                },
                handoverMemos = {
                    memoCalled = true
                    ok("""{"items":[]}""")
                },
                searchHistorySummaries = { _, _ -> ok("""{"items":[]}""") }
            )

        val state = loader.load(CONTEXT)

        assertFalse(memoCalled)
        assertEquals(listOf(DUTY_SHIFT_ID), timelineQueries.map { it.dutyShiftId })
        assertEquals(HandoverRecordScope.DutyShift, state.recordScope)
        assertEquals("현재 근무 확인", state.title)
        assertEquals("OP 3차 · 현재 근무 · 현재 폴리폰", state.subtitle)
        assertTrue(state.dutyShiftOptions.any { it.dutyShiftId == DUTY_SHIFT_ID && it.selected })
        assertFalse(state.dutyShiftOptions.any { it.dutyShiftId == PREVIOUS_DUTY_SHIFT_ID })
        assertEquals("path-current-1", state.replayPathSegments.single().sourceKey)
        assertEquals(2, state.replayPathSegments.single().points.size)
        assertEquals("현재 근무 경로가 기록되어 있습니다.", state.summary)
    }

    @Test
    fun firstDutyShiftOptionIsSelectedWhenSessionHasNoCurrentDutyShift() = runBlocking {
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
                              "policePhoneLabel": "후보 폴리폰",
                              "status": "ACTIVE",
                              "startedAt": "2026-05-11T04:00:00Z"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                },
                handoverTimeline = { _, query: HandoverTimelineQuery ->
                    timelineQueries += query
                    assertEquals(DUTY_SHIFT_ID, query.dutyShiftId)
                    ok(
                        """
                        {
                          "incidentId": "$INCIDENT_ID",
                          "operationalPeriodId": "$OP_ID",
                          "scope": {"scopeType": "DUTY_SHIFT", "dutyShiftId": "$DUTY_SHIFT_ID"},
                          "actors": [{"actorId": "actor-1", "displayName": "후보 폴리폰", "colorKey": "blue"}],
                          "paths": [
                            {
                              "pathId": "path-first-1",
                              "actorId": "actor-1",
                              "mode": "FOOT",
                              "startedAt": "2026-05-11T04:05:00Z",
                              "endedAt": "2026-05-11T04:15:00Z",
                              "points": [
                                {"at": "2026-05-11T04:05:00Z", "lat": 37.1000, "lng": 127.1000},
                                {"at": "2026-05-11T04:15:00Z", "lat": 37.1020, "lng": 127.1030}
                              ]
                            }
                          ],
                          "events": [],
                          "metrics": {"distanceMeters": 600, "markerCount": 0, "handoverMemoCount": 0, "syncStatus": "READY"}
                        }
                        """.trimIndent()
                    )
                },
                handoverMemos = { ok("""{"items":[]}""") },
                searchHistorySummaries = { _, _ -> ok("""{"items":[]}""") }
            )

        val state = loader.load(CONTEXT.copy(dutyShiftId = null))

        assertEquals(listOf(DUTY_SHIFT_ID), timelineQueries.map { it.dutyShiftId })
        assertEquals("근무 구간 확인", state.title)
        assertEquals("OP 3차 · 근무 구간 · 후보 폴리폰", state.subtitle)
        assertTrue(state.dutyShiftOptions.single().selected)
        assertEquals("path-first-1", state.replayPathSegments.single().sourceKey)
    }

    @Test
    fun emptyDutyShiftTimelineStaysInDutyShiftHandoverScope() = runBlocking {
        val timelineQueries = mutableListOf<HandoverTimelineQuery>()
        var memoCalled = false
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
                    assertEquals("DUTY_SHIFT", query.scopeType)
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
                },
                handoverMemos = {
                    memoCalled = true
                    ok("""{"items":[]}""")
                },
                searchHistorySummaries = { _, _ -> ok("""{"items":[]}""") }
            )

        val state = loader.load(CONTEXT)

        assertEquals(listOf("DUTY_SHIFT"), timelineQueries.map { it.scopeType })
        assertTrue(memoCalled)
        assertEquals(HandoverRecordScope.DutyShift, state.recordScope)
        assertEquals("현재 근무 확인", state.title)
        assertEquals("OP 3차 · 현재 근무", state.subtitle)
        assertEquals(SearchHistorySummaryStatus.Empty, state.summaryStatus)
        assertTrue(state.replayBadges.contains("근무 기준"))
        assertFalse(state.replayBadges.contains("OP 기준"))
        val reportState = state.selectTab(com.surimap.feature.handover.ui.DutyHandoverTab.Report)
        assertTrue(reportState.visibleText().contains("이전 근무 요약"))
        assertTrue(reportState.visibleText().contains("근무 개요"))
        assertFalse(reportState.visibleText().contains("OP 수색 이력 요약"))
        assertFalse(reportState.visibleText().contains("OP 개요"))
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

        val state = loader.load(CONTEXT)

        assertEquals(listOf("DUTY_SHIFT"), timelineQueries.map { it.scopeType })
        assertEquals(HandoverRecordScope.DutyShift, state.recordScope)
        assertEquals("이전 근무 확인", state.title)
        assertEquals("OP 3차 · 교대 인수인계", state.subtitle)
        assertTrue(state.replayBadges.contains("근무 기준"))
        assertFalse(state.replayBadges.contains("OP 기준"))
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
        assertTrue(state.records.any { it.title.contains("OP") })
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
