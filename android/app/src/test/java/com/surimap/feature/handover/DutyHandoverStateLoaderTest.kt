package com.surimap.feature.handover

import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.operationalperiod.HandoverMemoQuery
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
