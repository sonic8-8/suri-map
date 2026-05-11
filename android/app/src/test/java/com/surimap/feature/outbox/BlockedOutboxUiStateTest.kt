package com.surimap.feature.outbox

import com.surimap.feature.outbox.ui.BlockedOutboxReason
import com.surimap.feature.outbox.ui.BlockedOutboxUiState
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
        assertTrue(state.visibleText().any { it.contains("IT 부서 문의") })
        assertTrue(state.visibleText().any { it.contains("incident_closed") })
        assertTrue(state.visibleText().any { it.contains("police_phone_not_assigned") })
        assertTrue(state.visibleText().any { it.contains("retry_exhausted") })
        assertTrue(state.visibleText().any { it.contains("payload_validation_failure") })
    }

    @Test
    fun normalPendingQueueDoesNotExposeDiagnosticEntry() {
        val normal = BlockedOutboxUiState.normalPendingFixture()

        assertEquals(0, normal.blockedCount)
        assertFalse(normal.canEnterDiagnostic)
        assertFalse(normal.visibleText().any { it.contains("IT 부서 문의") })
        assertFalse(normal.visibleText().any { it.contains("처리 불가") })
        assertTrue(normal.visibleText().any { it.contains("자동 처리 대기 12건") })
        assertTrue(normal.visibleText().any { it.contains("사용자 조치 불요") })
    }

    @Test
    fun blockedReasonsKeepSafeCopyWithoutRawErrors() {
        val labels = BlockedOutboxReason.entries.map { it.errorCode }

        assertEquals(
            listOf("incident_closed", "police_phone_not_assigned", "retry_exhausted", "payload_validation_failure"),
            labels
        )
        assertFalse(BlockedOutboxUiState.blockedFixture().visibleText().any { it.contains("Exception") || it.contains("http_") })
    }
}
