package com.surimap.feature.incidents

import com.surimap.feature.incidents.ui.IncidentHomeMapDataStatus
import com.surimap.feature.incidents.ui.IncidentHomeUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentHomeUiStateTest {
    @Test
    fun incidentHomeCollectsFieldReadinessWithoutCommandLanguage() {
        val state =
            IncidentHomeUiState(
                incidentTitle = "광주 광산구 황룡강 생태길 실종 신고",
                missingPersonSummary = "70대 남성 · 회색 점퍼",
                opLabel = "2차 수색",
                assignmentLabel = "팀 담당 구역",
                mapDataStatus = IncidentHomeMapDataStatus.Ready,
                mapDataDetail = "오프라인 지도와 사건 기본 정보가 준비되어 있습니다.",
                syncLabel = "자동 전송 대기",
                lastUpdatedLabel = "5분 전 갱신",
                pendingOutboxCount = 3,
                blockedOutboxCount = 0
            )

        assertTrue(state.visibleText().contains("사건 정보"))
        assertTrue(state.visibleText().contains("지도 데이터 준비 완료"))
        assertTrue(state.visibleText().contains("자동 전송 대기 3건"))
        assertTrue(state.visibleText().contains("현장 기록 열기"))
        assertTrue(state.visibleText().contains("지도 데이터 확인"))
        assertFalse(state.visibleText().any { it.contains("오프라인 패키지") || it.contains("manifest") })
    }

    @Test
    fun blockedOutboxCountPromotesDiagnosticEntry() {
        val state = IncidentHomeUiState.sample()

        assertTrue(state.visibleText().contains("처리 불가 2건"))
        assertTrue(state.visibleText().contains("미전송 진단 보기"))
    }
}
