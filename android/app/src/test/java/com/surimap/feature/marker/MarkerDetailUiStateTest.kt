package com.surimap.feature.marker

import com.surimap.feature.marker.ui.MarkerDetailPhotoStatus
import com.surimap.feature.marker.ui.MarkerDetailUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerDetailUiStateTest {

    @Test
    fun ownMarkerAllowsEditAndExplicitDeleteButton() {
        val own = MarkerDetailUiState.ownMarker()

        assertTrue(own.canEdit)
        assertTrue(own.canSave)
        assertTrue(own.canDelete)
        assertTrue(own.visibleText().any { it.contains("저장") })
        assertTrue(own.visibleText().any { it.contains("삭제") })
        assertFalse(own.longPressDeleteEnabled)
    }

    @Test
    fun otherAccountMarkerIsReadonlyAndHidesEditDeleteActions() {
        val readonly = MarkerDetailUiState.readonlyOtherAccount()

        assertFalse(readonly.canEdit)
        assertFalse(readonly.canDelete)
        assertTrue(readonly.visibleText().any { it.contains("읽기 전용") })
        assertFalse(readonly.visibleText().any { it == "저장" })
        assertFalse(readonly.visibleText().any { it == "삭제" })
    }

    @Test
    fun unavailableMarkerDisablesMutationActionsAndShowsFailureStatus() {
        val unavailable = MarkerDetailUiState.unavailable("missing-marker")

        assertFalse(unavailable.canEdit)
        assertFalse(unavailable.canSave)
        assertFalse(unavailable.canDelete)
        assertTrue(unavailable.visibleText().any { it.contains("읽기 전용") })
        assertTrue(unavailable.visibleText().any { it.contains("조회 실패") })
        assertTrue(unavailable.visibleText().any { it.contains("저장 실패") })
    }

    @Test
    fun deleteRequiresConfirmDialogAfterExplicitDeleteRequest() {
        val confirming = MarkerDetailUiState.ownMarker(showDeleteConfirm = true)

        assertTrue(confirming.canDelete)
        assertTrue(confirming.showDeleteConfirm)
        assertTrue(confirming.visibleText().any { it.contains("삭제 확인") })
        assertTrue(confirming.visibleText().any { it.contains("마커를 삭제합니다") })
    }

    @Test
    fun photoRowsShowAttachAndDeleteProgress() {
        val state = MarkerDetailUiState.withPhotoProgress()

        assertTrue(state.photos.any { it.status == MarkerDetailPhotoStatus.Attaching })
        assertTrue(state.photos.any { it.status == MarkerDetailPhotoStatus.Deleting })
        assertTrue(state.visibleText().any { it.contains("attach 진행 중") })
        assertTrue(state.visibleText().any { it.contains("삭제 진행 중") })
    }
}
