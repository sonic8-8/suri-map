package com.surimap.feature.marker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerPhotoRouteContractTest {

    @Test
    fun markerDetailRouteWiresCameraAlbumAndFileProviderForPhotoUpload() {
        val appSource = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(appSource.contains("ActivityResultContracts.TakePicture()"))
        assertTrue(appSource.contains("createMarkerPhotoCaptureUri"))
        assertTrue(appSource.contains("captured || context.hasReadableMarkerPhoto(capturedUri)"))
        assertTrue(appSource.contains("private fun Context.hasReadableMarkerPhoto"))
        assertTrue(appSource.contains("photoCapture.launch"))
        assertTrue(appSource.contains("photoPicker.launch(\"image/*\")"))
        assertTrue(appSource.contains("onCapturePhoto"))
        assertTrue(appSource.contains("onPickPhoto"))
        assertTrue(manifest.contains("androidx.core.content.FileProvider"))
    }

    @Test
    fun debugQaMarkerDetailButtonsOpenCameraAndAlbumLaunchers() {
        val qaSource = File("src/debug/java/com/surimap/ui/qa/DeviceQaActivity.kt").readText()

        assertTrue(qaSource.contains("ActivityResultContracts.TakePicture()"))
        assertTrue(qaSource.contains("ActivityResultContracts.GetContent()"))
        assertTrue(qaSource.contains("createQaMarkerPhotoCaptureUri"))
        assertTrue(qaSource.contains("captured || context.hasReadableQaMarkerPhoto(capturedUri)"))
        assertTrue(qaSource.contains("photoCapture.launch"))
        assertTrue(qaSource.contains("photoPicker.launch(\"image/*\")"))
        assertFalse(qaSource.contains("onCapturePhoto = {},"))
        assertFalse(qaSource.contains("onPickPhoto = {},"))
    }

    @Test
    fun markerCreateRouteDoesNotUsePreviewSampleStateForProductionInput() {
        val appSource = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        assertFalse(appSource.contains("sampleMarkerCreateSheetState"))
        assertTrue(appSource.contains("MarkerCreateSheetUiState.default()"))
        assertTrue(appSource.contains("latestGpsLocationFix ?: locationUpdates.lastKnownFix()"))
        assertTrue(appSource.contains(".withCurrentLocation(currentGpsLocation.toMarkerLocation())"))
    }
}
