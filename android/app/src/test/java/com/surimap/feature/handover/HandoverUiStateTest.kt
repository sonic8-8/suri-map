package com.surimap.feature.handover

import com.surimap.feature.handover.ui.DutyHandoverUiState
import com.surimap.feature.handover.ui.DutyHandoverTab
import com.surimap.feature.handover.ui.HandoverMemoTarget
import com.surimap.feature.handover.ui.HandoverMemoUiState
import com.surimap.feature.handover.ui.HandoverPromptUiState
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoverUiStateTest {

    @Test
    fun dutyHandoverShowsReadOnlySummaryStatesAndOriginalRecords() {
        val generating = DutyHandoverUiState.generating()
        val needsSummary = DutyHandoverUiState.needsSummary()
        val unavailable = DutyHandoverUiState.unavailable()
        val empty = DutyHandoverUiState.empty()

        assertTrue(generating.visibleText().any { it.contains("자동 처리 중") })
        assertTrue(needsSummary.visibleText().any { it.contains("요약 생성 필요") })
        assertTrue(needsSummary.visibleText().any { it.contains("원본 확인") })
        assertTrue(unavailable.visibleText().any { it.contains("요약") })
        assertTrue(empty.visibleText().any { it.contains("이전 기록 없음") })

        listOf(generating, needsSummary, unavailable, empty).forEach { state ->
            assertTrue(state.visibleText().any { it.contains("원본 기록") })
            assertFalse(state.canRequestSummaryGeneration)
            assertFalse(state.visibleText().any { it.contains("다시 생성") })
            assertFalse(state.visibleText().any { it.contains("AI") })
            assertFalse(state.visibleText().any { it.contains("sourceReadiness") })
            assertFalse(state.visibleText().any { it.contains("summary_unavailable") })
            assertFalse(state.visibleText().any { it.contains("DutyShift") })
        }
    }

    @Test
    fun dutyHandoverHasReplayAndReportTabsWithoutRecommendationCopy() {
        val initial = DutyHandoverUiState.ready()
        val report = initial.selectTab(DutyHandoverTab.Report)

        assertEquals(listOf("리플레이", "보고서"), DutyHandoverTab.entries.map { it.label })
        assertEquals(DutyHandoverTab.Replay, initial.selectedTab)
        assertEquals(DutyHandoverTab.Report, report.selectedTab)
        assertTrue(initial.visibleText().any { it.contains("리플레이") })
        assertTrue(report.visibleText().any { it.contains("보고서") })
        assertTrue(report.visibleText().any { it.contains("서버 인수인계 요약") })

        listOf(initial, report).forEach { state ->
            assertFalse(state.visibleText().any { it.contains("추천") })
            assertFalse(state.visibleText().any { it.contains("위험") })
            assertFalse(state.visibleText().any { it.contains("미수색") })
        }
    }

    @Test
    fun reportTabExposesOnlyMvpSectionsWithoutPdfOrEmptyRecommendationSlot() {
        val report = DutyHandoverUiState.ready().selectTab(DutyHandoverTab.Report)

        assertEquals(
            listOf(
                "근무 개요",
                "서버 인수인계 요약",
                "이동 통계",
                "발견·기록 시간순",
                "인수인계 메모",
                "마커 사진",
                "동기화 상태"
            ),
            report.reportSectionTitles
        )
        report.reportSectionTitles.forEach { sectionTitle ->
            assertTrue(report.visibleText().any { it.contains(sectionTitle) })
        }
        assertEquals(listOf("운영 메모 · 북측 진입로"), report.handoverMemoRecords.map { it.title })
        assertTrue(report.visibleText().any { it.contains("동기화 완료") })
        assertTrue(report.visibleText().any { it.contains("사진 2장") })

        listOf(report).forEach { state ->
            assertFalse(state.visibleText().any { it.contains("PDF") })
            assertFalse(state.visibleText().any { it.contains("권장") })
            assertFalse(state.visibleText().any { it.contains("추천") })
            assertFalse(state.visibleText().any { it.contains("위험") })
            assertFalse(state.visibleText().any { it.contains("미수색") })
        }
    }

    @Test
    fun handoverMemoTargetsIncludeAllS8ContextsAndNoAiAction() {
        val state = HandoverMemoUiState.default(offline = true)

        assertEquals(
            listOf("OP", "경로", "구역", "근무", "마커"),
            HandoverMemoTarget.entries.map { it.label }
        )
        assertTrue(state.visibleText().any { it.contains("미전송") })
        assertTrue(state.visibleText().any { it.contains("저장") })
        assertFalse(state.visibleText().any { it.contains("AI") || it.contains("요약 생성") })
        assertFalse(state.visibleText().any { it.contains("DutyShift") || it.contains("사건 #") })
    }

    @Test
    fun handoverPromptShowsOnlyWhenServerShiftStartedAfterLastSeen() {
        val lastSeen = Instant.parse("2026-04-28T03:30:00Z")
        val newerServerShift =
            HandoverPromptUiState(
                currentDutyShiftStartedAt = Instant.parse("2026-04-28T04:00:00Z"),
                lastSeenHandoverAt = lastSeen
            )
        val alreadySeen =
            HandoverPromptUiState(
                currentDutyShiftStartedAt = Instant.parse("2026-04-28T03:00:00Z"),
                lastSeenHandoverAt = lastSeen
            )

        assertTrue(newerServerShift.shouldShow)
        assertFalse(alreadySeen.shouldShow)
        assertTrue(HandoverPromptUiState(currentDutyShiftStartedAt = Instant.parse("2026-04-28T04:00:00Z")).shouldShow)
    }

    @Test
    fun appHandoverRoutesUseRepositoriesInsteadOfSampleStateDirectly() {
        val source = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        assertFalse(source.contains("sampleDutyHandoverState()"))
        assertFalse(source.contains("sampleHandoverMemoState()"))
        assertTrue(source.contains("DutyHandoverStateLoader"))
        assertTrue(source.contains("DutyShiftLocalRecorder"))
        assertTrue(source.contains("dutyShiftRecorder.end"))
        assertTrue(source.contains("HandoverMemoLocalRecorder"))
        assertTrue(source.contains("clockOffsetMs = clockSyncState::clockOffsetMs"))
        assertTrue(source.contains("clockSyncedAt = clockSyncState::clockSyncedAt"))
        assertTrue(source.contains("clockSyncState.syncClockForIncident"))
        assertTrue(source.contains("HandoverMemoRepository"))
        assertTrue(source.contains("SearchHistorySummaryReadRepository"))
        assertTrue(source.contains("createMemo"))
    }

    @Test
    fun appHandoverRouteOwnsTabStateForP6A() {
        val source = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        val routeIndex = source.indexOf("private fun HandoverSummaryRoute")
        val tabStateIndex = source.indexOf("selectedHandoverTab", routeIndex)
        val onSelectTabIndex = source.indexOf("onSelectTab = { selectedHandoverTab = it }", routeIndex)

        assertTrue(routeIndex >= 0)
        assertTrue(tabStateIndex > routeIndex)
        assertTrue(onSelectTabIndex > tabStateIndex)
    }

    @Test
    fun handoverMemoSaveRefreshesClockBeforeOutboxWrite() {
        val source = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        val routeIndex = source.indexOf("private fun HandoverMemoRoute")
        val createMemoIndex = source.indexOf("recorder.createMemo", routeIndex)
        val clockSyncIndex =
            source.indexOf(
                "clockSyncState.syncClockForIncident(sessionContext.incidentId, policePhoneContext)",
                source.indexOf("onSave =", routeIndex)
            )

        assertTrue(routeIndex >= 0)
        assertTrue(createMemoIndex >= 0)
        assertTrue(clockSyncIndex >= 0)
        assertTrue(clockSyncIndex < createMemoIndex)
    }

    @Test
    fun dutyShiftEndRefreshesClockBeforeOutboxWrite() {
        val source = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        val routeIndex = source.indexOf("private fun HandoverSummaryRoute")
        val endIndex = source.indexOf("dutyShiftRecorder.end", routeIndex)
        val clockSyncIndex =
            source.indexOf(
                "clockSyncState.syncClockForIncident(sessionContext.incidentId, policePhoneContext)",
                source.indexOf("onEndDutyShift =", routeIndex)
            )

        assertTrue(routeIndex >= 0)
        assertTrue(endIndex >= 0)
        assertTrue(clockSyncIndex >= 0)
        assertTrue(clockSyncIndex < endIndex)
    }
}
