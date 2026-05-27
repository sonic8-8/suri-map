package com.surimap.feature.marker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerDetailRouteWiringTest {

    @Test
    fun markerDetailRouteLoadsActualMarkerAndEnqueuesUpdateDeleteWrites() {
        val source = java.io.File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        assertFalse(source.contains("state = sampleMarkerDetailState()"))
        assertFalse(source.contains("import com.surimap.feature.marker.ui.sampleMarkerDetailState"))
        assertTrue(source.contains("MarkerDetailDeepLink.RoutePattern"))
        assertTrue(source.contains("MarkerDetailStateLoader"))
        assertTrue(source.contains("MarkerRepository"))
        assertTrue(source.contains("listMarkers"))
        assertTrue(source.contains("MarkerLocalRecorder"))
        assertTrue(source.contains("updateMarker"))
        assertTrue(source.contains("deleteMarker"))
        assertTrue(source.contains("markerDetailState.toMarkerUpsertInput()"))
        assertTrue(source.contains("MarkerSaveStatus.PendingOutbox"))
        assertTrue(source.contains("MarkerSaveStatus.Failed"))
    }

    @Test
    fun markerDetailRouteConnectsPhotoUploadCoordinatorToAddPhotoAction() {
        val source = java.io.File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        assertTrue(source.contains("MarkerPhotoUiUploadCoordinator"))
        assertTrue(source.contains("HttpObjectStorageUploader"))
        assertTrue(source.contains("photoUploadCoordinator.upload"))
        assertTrue(source.contains("MarkerPhotoUploadPayload"))
        assertFalse(source.contains("onAddPhoto = {},"))
    }

    @Test
    fun markerDetailPhotoUploadRefreshesClockBeforeOutboxAttach() {
        val source = java.io.File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        val beginPhotoUploadIndex = source.indexOf("fun beginPhotoUpload")
        val payloadIndex =
            source.indexOf("val payload = context.markerPhotoUploadPayload", beginPhotoUploadIndex)
        val clockSyncIndex =
            source.indexOf(
                "clockSyncState.syncClockForIncident(sessionContext.incidentId, policePhoneContext)",
                beginPhotoUploadIndex
            )

        assertTrue(beginPhotoUploadIndex >= 0)
        assertTrue(payloadIndex >= 0)
        assertTrue(clockSyncIndex >= 0)
        assertTrue(clockSyncIndex < payloadIndex)
    }

    @Test
    fun markerDetailRouteUsesCloseLabelWhenOpenedAsModal() {
        val source = java.io.File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        assertTrue(source.contains("closeLabel = if (onClose != null) \"닫기\" else \"목록으로\""))
        assertTrue(source.contains("modalPresentation = onClose != null"))
    }

    @Test
    fun markerDetailModalShowsStablePreviewLoadingStateBeforeDetailHydrates() {
        val routeSource = java.io.File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()
        val screenSource = java.io.File("src/main/java/com/surimap/feature/marker/ui/MarkerDetailScreen.kt").readText()

        assertTrue(routeSource.contains("searchMapState.markerDetailLoadingState(markerId)"))
        assertTrue(routeSource.contains("initialLoadingState: MarkerDetailUiState? = null"))
        assertTrue(routeSource.contains("val loadingState ="))
        assertTrue(routeSource.contains("markerDetailState = loadingState"))
        assertTrue(screenSource.contains("state.loading"))
        assertTrue(screenSource.contains("MarkerDetailLoadingContent"))
        assertTrue(screenSource.contains("MarkerDetailSkeletonCard"))
        assertTrue(screenSource.contains("rememberInfiniteTransition"))
        assertTrue(screenSource.contains("Brush.linearGradient"))
        assertTrue(screenSource.contains("shimmerBrush"))
        assertTrue(screenSource.contains("shimmerOffset"))
        assertTrue(screenSource.contains(".background(shimmerBrush)"))
        assertFalse(screenSource.contains(".alpha(skeletonAlpha)"))

        val loadingContentStart = screenSource.indexOf("private fun MarkerDetailLoadingContent")
        val loadingContentEnd = screenSource.indexOf("private fun MarkerDetailSkeletonCard", loadingContentStart)
        val loadingContentSource = screenSource.substring(loadingContentStart, loadingContentEnd)
        assertFalse(loadingContentSource.contains("PoliBanner("))
    }
}
