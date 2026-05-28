package com.surimap.feature.incidents

import com.surimap.feature.incidents.ui.IncidentHomeMapDataStatus
import com.surimap.feature.incidents.ui.IncidentHomeUiState
import com.surimap.feature.incidents.ui.IncidentAssignmentUiState
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
                openedAtLabel = "2026-05-28 09:10",
                missingPersonName = "홍길동",
                missingPersonPhotoUrl = "https://api.surimap.test/mock-upload/missing-person/hong.jpg",
                lastSeenAtLabel = "2026-05-28 08:40",
                lastSeenLocationLabel = "황룡강 생태길 북측 진입로",
                appearanceLabel = "회색 점퍼",
                opLabel = "2차 수색",
                assignmentLabel = "팀 담당 구역",
                assignmentCountLabel = "3개",
                assignmentRoleSummary = "사건 지휘 1 · 현장 지휘 1 · 수색 대원 1",
                assignmentItems = listOf(
                    IncidentAssignmentUiState(
                        displayName = "광주 실종팀 상황반",
                        roleLabel = "사건 지휘",
                        accountTypeLabel = "지휘",
                        organizationLabel = "실종팀",
                        assignedAtLabel = "2026-05-28 09:12"
                    ),
                    IncidentAssignmentUiState(
                        displayName = "기동대 1부대 A팀",
                        roleLabel = "현장 지휘",
                        accountTypeLabel = "팀",
                        organizationLabel = "지원부대",
                        assignedAtLabel = "2026-05-28 09:20"
                    )
                ),
                mapDataStatus = IncidentHomeMapDataStatus.Ready,
                mapDataDetail = "오프라인 지도와 사건 기본 정보가 준비되어 있습니다.",
                syncLabel = "자동 전송 대기",
                lastUpdatedLabel = "5분 전 갱신",
                pendingOutboxCount = 3,
                blockedOutboxCount = 0
            )

        assertTrue(state.visibleText().contains("사건 정보"))
        assertTrue(state.visibleText().contains("2026-05-28 09:10"))
        assertTrue(state.visibleText().contains("홍길동"))
        assertTrue(state.visibleText().contains("실종자 사진"))
        assertTrue(state.visibleText().contains("2026-05-28 08:40"))
        assertTrue(state.visibleText().contains("황룡강 생태길 북측 진입로"))
        assertTrue(state.visibleText().contains("3개"))
        assertTrue(state.visibleText().contains("사건 지휘 1 · 현장 지휘 1 · 수색 대원 1"))
        assertTrue(state.visibleText().contains("광주 실종팀 상황반"))
        assertTrue(state.visibleText().contains("지휘 · 실종팀"))
        assertTrue(state.visibleText().contains("기동대 1부대 A팀"))
        assertTrue(state.visibleText().contains("팀 · 지원부대"))
        assertTrue(state.visibleText().contains("지도 데이터 준비 완료"))
        assertTrue(state.visibleText().contains("자동 전송 대기 3건"))
        assertFalse(state.visibleText().contains("현장 기록 열기"))
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
