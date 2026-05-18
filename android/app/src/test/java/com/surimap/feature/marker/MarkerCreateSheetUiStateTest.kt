package com.surimap.feature.marker

import com.surimap.feature.marker.ui.MarkerCreateSheetUiState
import com.surimap.feature.marker.ui.MarkerPhotoUiState
import com.surimap.feature.marker.ui.MarkerPhotoStage
import com.surimap.feature.marker.ui.MarkerSaveStatus
import com.surimap.feature.marker.ui.MarkerType
import com.surimap.feature.marker.ui.SupportRequestType
import com.surimap.feature.marker.ui.withCurrentLocation
import com.surimap.feature.marker.ui.withManualLocation
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
        assertTrue(clue.visibleText().any { it.contains("마커와 사진이 함께 등록") })
    }

    @Test
    fun manualLocationAdjustmentKeepsChosenPointForMarkerPayload() {
        val state =
            MarkerCreateSheetUiState.default(selectedType = MarkerType.CLUE)
                .withCurrentLocation(lon = 126.970100, lat = 37.580100)
                .withManualLocation(lon = 126.970321, lat = 37.580321)

        assertTrue(state.manualLocationAdjusted)
        assertEquals("location", state.locationPayloadName)
        assertEquals(126.970321, state.selectedLocation!!.lon, 0.0)
        assertEquals(37.580321, state.selectedLocation.lat, 0.0)
        assertEquals("위도 37.580321 · 경도 126.970321", state.locationLabel)
        assertEquals("사용자 지정 위치", state.locationSourceLabel)
        assertEquals("지도 중심으로 지정", state.locationActionLabel)
        assertTrue(state.visibleText().any { it.contains("지도 중심") })
        assertFalse(state.visibleText().any { it.contains("원하는 지점을 화면 가운데") })
    }

    @Test
    fun missingLocationBlocksSaveBecauseLocationIsRequiredMarkerField() {
        val state = MarkerCreateSheetUiState.default().copy(selectedLocation = null)

        assertFalse(state.selectedLocationIsValid)
        assertFalse(state.canSave)
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
        assertTrue(state.visibleText().any { it.contains("업로드 준비") })
        assertTrue(state.visibleText().any { it.contains("사진 업로드") })
        assertTrue(state.visibleText().any { it.contains("마커에 첨부") })
    }

    @Test
    fun markerCreateUiTextUsesFieldLabelsInsteadOfRawEnumValues() {
        val state = MarkerCreateSheetUiState.default(selectedType = MarkerType.PERSON_FOUND)

        assertTrue(state.visibleText().contains("실종자 발견"))
        assertFalse(state.visibleText().contains("PERSON_FOUND"))
        assertFalse(state.visibleText().contains("FIELD_CONDITION"))
        assertFalse(state.visibleText().contains("SUPPORT_REQUEST"))
    }

    @Test
    fun photoLimitsExposeMaxTenPhotosAndTenMegabytesPerFile() {
        val state = MarkerCreateSheetUiState.default()

        assertEquals(10, state.maxPhotoCount)
        assertEquals(10_485_760L, state.maxPhotoBytes)
        assertEquals("사진 0 / 10 · 파일당 10MB", state.photoLimitLabel)
        assertTrue(state.canAttachPhoto)
        assertTrue(state.visibleText().any { it.contains("파일당 10MB") })

        val full =
            state.copy(
                photoCount = 10,
                photos =
                List(10) { index ->
                    MarkerPhotoUiState("photo-$index.jpg", MarkerPhotoStage.Selected, progress = 0f)
                }
            )

        assertFalse(full.canAttachPhoto)
        assertEquals("마커당 사진은 10장까지 첨부할 수 있습니다.", full.photoLimitWarning)
        assertTrue(full.visibleText().any { it.contains("10장까지") })
    }

    @Test
    fun oversizedPhotoBlocksSaveAndExplainsFileLimit() {
        val oversized =
            MarkerCreateSheetUiState.default().copy(
                photoCount = 1,
                photos =
                listOf(
                    MarkerPhotoUiState(
                        fileName = "oversized.jpg",
                        stage = MarkerPhotoStage.Selected,
                        progress = 0f,
                        sizeBytes = 10_485_761L
                    )
                )
            )

        assertFalse(oversized.canSave)
        assertFalse(oversized.photosWithinSizeLimit)
        assertEquals("사진 파일은 10MB 이하만 첨부할 수 있습니다.", oversized.photoLimitWarning)
        assertTrue(oversized.visibleText().any { it.contains("10MB 이하") })
    }
}
