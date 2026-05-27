package com.surimap.feature.outbox

import com.surimap.feature.outbox.ui.BlockedOutboxReason
import com.surimap.feature.outbox.ui.BlockedOutboxUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedOutboxUiStateTest {

    @Test
    fun blockedDiagnosticGroupsOnlyUserActionRequiredFailures() {
        val state = BlockedOutboxUiState.blockedFixture()

        assertEquals(4, state.blockedCount)
        assertTrue(state.canEnterDiagnostic)
        assertTrue(state.visibleText().any { it.contains("처리 불가 4건") })
        assertTrue(state.visibleText().any { it.contains("문의 안내") })
        assertTrue(state.visibleText().any { it.contains("종료된 사건 전송 차단") })
        assertTrue(state.visibleText().any { it.contains("단말 배정 확인 필요") })
        assertTrue(state.visibleText().any { it.contains("자동 재시도 한도 초과") })
        assertTrue(state.visibleText().any { it.contains("지금 재시도") })
        assertTrue(state.visibleText().any { it.contains("저장 데이터 검증 실패") })
        assertFalse(state.visibleText().any { it.contains("batch") || it.contains("finalize") || it.contains("패키지") })
    }

    @Test
    fun normalPendingQueueDoesNotExposeDiagnosticEntry() {
        val normal = BlockedOutboxUiState.normalPendingFixture()

        assertEquals(0, normal.blockedCount)
        assertFalse(normal.canEnterDiagnostic)
        assertFalse(normal.visibleText().any { it.contains("문의 안내") })
        assertFalse(normal.visibleText().any { it.contains("처리 불가") })
        assertTrue(normal.visibleText().any { it.contains("자동 처리 대기 12건") })
        assertTrue(normal.visibleText().any { it.contains("사용자 조치 불요") })
    }

    @Test
    fun blockedReasonsKeepSafeCopyWithoutRawErrors() {
        val labels = BlockedOutboxReason.entries.map { it.errorCode }

        assertEquals(
            listOf(
                "incident_closed",
                "police_phone_not_assigned",
                "retry_exhausted",
                "payload_validation_failure",
                "clock_resync_required",
                "retryable_network",
                "police_phone_access_required"
            ),
            labels
        )
        val visibleText = BlockedOutboxUiState.blockedFixture().visibleText()

        assertFalse(
            visibleText.any { text ->
                text.contains("Exception") ||
                    text.contains("http_") ||
                    labels.any(text::contains)
            }
        )
    }

    @Test
    fun appBlockedOutboxRouteUsesRoomDiagnosticsAndRequeueApiInsteadOfSampleState() {
        val source = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        assertTrue(source.contains("BlockedOutboxStateLoader"))
        assertTrue(source.contains("RoomOutboxRequeue"))
        assertTrue(source.contains("OutboxRequeueNetworkRequest"))
        assertTrue(source.contains("requeueClient.requeue"))
        assertFalse(source.contains("BlockedOutboxRouteScreen(onBack"))
    }
}
