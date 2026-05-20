package com.surimap.feature.marker

import android.graphics.Bitmap
import android.graphics.Color
import com.surimap.feature.marker.data.MarkerPhotoPayloadReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MarkerPhotoPayloadReaderTest {

    @Test
    fun supportedSmallPhotoKeepsOriginalBytesAndNormalizedContentType() {
        val bytes = patternedBitmap(width = 32, height = 24).toBytes(Bitmap.CompressFormat.JPEG, quality = 90)

        val payload =
            MarkerPhotoPayloadReader.fromBytes(
                markerId = "marker-1",
                contentType = "image/jpg",
                bytes = bytes
            )

        assertNotNull(payload)
        requireNotNull(payload)
        assertEquals("marker-1", payload.markerId)
        assertEquals("image/jpeg", payload.contentType)
        assertArrayEquals(bytes, payload.bytes)
        assertEquals(32, payload.width)
        assertEquals(24, payload.height)
    }

    @Test
    fun oversizedOrUnsupportedPhotoIsReencodedForMinioUploadLimit() {
        val original = patternedBitmap(width = 1200, height = 900).toBytes(Bitmap.CompressFormat.PNG, quality = 100)
        val maxBytes = 900_000L
        assertTrue(original.size > maxBytes)

        val payload =
            MarkerPhotoPayloadReader.fromBytes(
                markerId = "marker-1",
                contentType = "image/png",
                bytes = original,
                maxBytes = maxBytes
            )

        assertNotNull(payload)
        requireNotNull(payload)
        assertEquals("image/jpeg", payload.contentType)
        assertTrue(payload.bytes.size <= maxBytes)
        assertTrue(requireNotNull(payload.width) <= 1200)
        assertTrue(requireNotNull(payload.height) <= 900)
    }

    private fun patternedBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels =
            IntArray(width * height) { index ->
                val x = index % width
                val y = index / width
                Color.rgb(
                    (x * 17 + y * 31) and 0xff,
                    (x * 47 + y * 13) and 0xff,
                    (x * 5 + y * 73) and 0xff
                )
            }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun Bitmap.toBytes(format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        compress(format, quality, output)
        return output.toByteArray()
    }
}
