package com.surimap.feature.handover

import com.surimap.feature.handover.ui.DutyHandoverUiState
import com.surimap.feature.handover.ui.HandoverMemoTarget
import com.surimap.feature.handover.ui.HandoverMemoUiState
import com.surimap.feature.handover.ui.HandoverPromptUiState
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
        assertTrue(unavailable.visibleText().any { it.contains("summary_unavailable") })
        assertTrue(empty.visibleText().any { it.contains("이전 기록 없음") })

        listOf(generating, needsSummary, unavailable, empty).forEach { state ->
            assertTrue(state.visibleText().any { it.contains("원본 기록") })
            assertFalse(state.canRequestSummaryGeneration)
            assertFalse(state.visibleText().any { it.contains("다시 생성") })
            assertFalse(state.visibleText().any { it.contains("AI 요약 생성") })
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
}
