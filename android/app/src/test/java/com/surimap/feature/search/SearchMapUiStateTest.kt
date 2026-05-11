package com.surimap.feature.search

import com.surimap.feature.search.ui.SearchLifecycleStatus
import com.surimap.feature.search.ui.SearchMapSyncStatus
import com.surimap.feature.search.ui.SearchMapUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMapUiStateTest {

    @Test
    fun activeSearchAllowsPathAndMarkerWrites() {
        val active = SearchMapUiState.active()

        assertEquals(SearchLifecycleStatus.Active, active.lifecycleStatus)
        assertTrue(active.canWritePath)
        assertTrue(active.canCreateMarker)
        assertEquals("일시정지", active.primaryActionLabel)
        assertTrue(active.visibleText().any { it.contains("마커 생성") })
    }

    @Test
    fun opRequiredBlocksAllMapWritesAndShowsRetryCopy() {
        val opRequired = SearchMapUiState.opRequired()
        val opTransition = SearchMapUiState.opTransition()

        assertEquals(SearchLifecycleStatus.OpRequired, opRequired.lifecycleStatus)
        assertFalse(opRequired.canWritePath)
        assertFalse(opRequired.canCreateMarker)
        assertTrue(opRequired.visibleText().any { it.contains("OP 다시 확인") })
        assertTrue(opRequired.visibleText().any { it.contains("경로·마커 기록 차단") })
        assertEquals(SearchLifecycleStatus.OpTransition, opTransition.lifecycleStatus)
        assertFalse(opTransition.canWritePath)
        assertFalse(opTransition.canCreateMarker)
        assertTrue(opTransition.visibleText().any { it.contains("readonly") })
    }

    @Test
    fun pausedAndStoppedStatesExposeWriteAvailability() {
        val paused = SearchMapUiState.paused()
        val stopped = SearchMapUiState.stopped()

        assertEquals("재개", paused.primaryActionLabel)
        assertFalse(paused.canWritePath)
        assertFalse(paused.canCreateMarker)
        assertEquals("수색 시작", stopped.primaryActionLabel)
        assertFalse(stopped.canWritePath)
        assertFalse(stopped.canCreateMarker)
    }

    @Test
    fun normalOutboxQueueStaysInSyncChipInsteadOfBlockedQueueRoute() {
        val offline = SearchMapUiState.offline(unsentCount = 12, oldestPendingMinutes = 8)
        val sending = SearchMapUiState.sending(unsentCount = 3)

        assertEquals(SearchMapSyncStatus.Offline, offline.syncStatus)
        assertEquals("미전송 12 · 8분", offline.syncLabel)
        assertFalse(offline.shouldOpenBlockedOutbox)
        assertEquals(SearchMapSyncStatus.Sending, sending.syncStatus)
        assertEquals("전송 중 · 3", sending.syncLabel)
        assertFalse(sending.shouldOpenBlockedOutbox)
    }

    @Test
    fun blockedOutboxIsSeparateFromNormalQueueAndHandoverPromptCanEnterP6A() {
        val state = SearchMapUiState.active(
            blockedOutboxCount = 2,
            hasUnreadHandover = true
        )

        assertTrue(state.shouldOpenBlockedOutbox)
        assertTrue(state.showHandoverPrompt)
        assertTrue(state.visibleText().any { it.contains("미전송 2건 처리 불가") })
        assertTrue(state.visibleText().any { it.contains("이전 근무 기록 있음") })
    }
}
