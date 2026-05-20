package com.surimap.feature.handover

import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.operationalperiod.HandoverMemoQuery
import com.surimap.core.operationalperiod.HandoverTimelineQuery
import com.surimap.core.operationalperiod.SearchHistorySummaryQuery
import com.surimap.feature.handover.data.DutyHandoverStateLoader
import com.surimap.feature.handover.data.HandoverSessionContext
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
