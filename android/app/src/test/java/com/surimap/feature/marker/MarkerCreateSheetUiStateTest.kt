package com.surimap.feature.marker

import com.surimap.feature.marker.ui.MarkerCreateSheetUiState
import com.surimap.feature.marker.ui.MarkerPhotoStage
import com.surimap.feature.marker.ui.MarkerSaveStatus
import com.surimap.feature.marker.ui.MarkerType
import com.surimap.feature.marker.ui.SupportRequestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerCreateSheetUiStateTest {

    @Test
    fun markerTypesMatchS5ApiContractExactly() {
        assertEquals(
            listOf("CLUE", "PERSON_FOUND", "FIELD_CONDITION", "SUPPORT_REQUEST", "NOTE"),
            MarkerType.entries.map { it.apiValue }
        )
        assertEquals(
            listOf("DRONE", "POLICE_DOG", "OTHER"),
            SupportRequestType.entries.map { it.apiValue }
        )
    }

    @Test
    fun minimalMarkerCanBeSavedWithoutMemoOrPhoto() {
        val clue = MarkerCreateSheetUiState.default(selectedType = MarkerType.CLUE)
        val support = MarkerCreateSheetUiState.default(
            selectedType = MarkerType.SUPPORT_REQUEST,
            supportRequestType = SupportRequestType.DRONE
        )

        assertTrue(clue.canSave)
        assertFalse(clue.requiresSupportRequestType)
        assertTrue(support.canSave)
        assertEquals("type", support.markerTypePayloadName)
        assertEquals("supportRequestType", support.supportRequestPayloadName)
    }

    @Test
    fun supportRequestRequiresSubtypeBeforeSave() {
        val state = MarkerCreateSheetUiState.default(
            selectedType = MarkerType.SUPPORT_REQUEST,
            supportRequestType = null
        )

        assertTrue(state.requiresSupportRequestType)
        assertFalse(state.canSave)
        assertTrue(state.visibleText().any { it.contains("지원 요청 유형") })
    }

    @Test
    fun offlinePendingDoesNotOpenBlockedOutboxDiagnostic() {
        val pending = MarkerCreateSheetUiState.offlinePending()

        assertEquals(MarkerSaveStatus.PendingOutbox, pending.saveStatus)
        assertFalse(pending.opensBlockedOutbox)
        assertTrue(pending.visibleText().any { it.contains("오프라인 저장됨") })
        assertTrue(pending.visibleText().any { it.contains("전송 대기") })
    }

    @Test
    fun photoFlowNamesUploadUrlObjectStorageUploadAndAttachStages() {
        val state = MarkerCreateSheetUiState.photoUploading()

        assertEquals(
            listOf(MarkerPhotoStage.UploadUrl, MarkerPhotoStage.ObjectStorageUpload, MarkerPhotoStage.Attach),
            state.photos.map { it.stage }
        )
        assertTrue(state.visibleText().any { it.contains("upload-url") })
        assertTrue(state.visibleText().any { it.contains("object storage 업로드") })
        assertTrue(state.visibleText().any { it.contains("attach") })
    }
}
