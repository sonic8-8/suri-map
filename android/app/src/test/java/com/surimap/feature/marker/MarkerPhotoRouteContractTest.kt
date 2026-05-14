package com.surimap.feature.marker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerPhotoRouteContractTest {

    @Test
    fun markerDetailRouteWiresCameraAlbumAndFileProviderForPhotoUpload() {
        val appSource = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(appSource.contains("ActivityResultContracts.TakePicture()"))
        assertTrue(appSource.contains("createMarkerPhotoCaptureUri"))
        assertTrue(appSource.contains("photoCapture.launch"))
        assertTrue(appSource.contains("photoPicker.launch(\"image/*\")"))
        assertTrue(appSource.contains("onCapturePhoto"))
        assertTrue(appSource.contains("onPickPhoto"))
        assertTrue(manifest.contains("androidx.core.content.FileProvider"))
    }
}
