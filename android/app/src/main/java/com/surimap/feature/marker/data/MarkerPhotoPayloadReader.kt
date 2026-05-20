package com.surimap.feature.marker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object MarkerPhotoPayloadReader {
    private const val MAX_PHOTO_BYTES = 10_485_760L
    private const val MAX_ENCODE_DIMENSION = 1920
    private const val MIN_ENCODE_DIMENSION = 960
    private val JPEG_QUALITIES = listOf(92, 84, 76, 68, 60)
    private val SUPPORTED_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")

    fun read(context: Context, markerId: String, uri: Uri): MarkerPhotoUploadPayload? =
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() } ?: return null
            fromBytes(
                markerId = markerId,
                contentType = context.contentResolver.getType(uri),
                bytes = bytes
            )
        }.getOrNull()

    internal fun fromBytes(
        markerId: String,
        contentType: String?,
        bytes: ByteArray,
        maxBytes: Long = MAX_PHOTO_BYTES
    ): MarkerPhotoUploadPayload? {
        if (markerId.isBlank() || bytes.isEmpty() || maxBytes <= 0L) {
            return null
        }
        val normalizedContentType = contentType.normalizedImageContentType()
        val bounds = bytes.imageBounds()
        if (normalizedContentType in SUPPORTED_CONTENT_TYPES && bytes.size.toLong() <= maxBytes) {
            return MarkerPhotoUploadPayload(
                markerId = markerId,
                contentType = normalizedContentType,
                bytes = bytes,
                width = bounds?.first,
                height = bounds?.second
            )
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val encoded = bitmap.encodeJpegWithin(maxBytes) ?: return null
        return MarkerPhotoUploadPayload(
            markerId = markerId,
            contentType = "image/jpeg",
            bytes = encoded.bytes,
            width = encoded.width,
            height = encoded.height
        )
    }

    private fun String?.normalizedImageContentType(): String? =
        when (this?.trim()?.lowercase()) {
            "image/jpg", "image/jpeg" -> "image/jpeg"
            "image/png" -> "image/png"
            "image/webp" -> "image/webp"
            else -> null
        }

    private fun ByteArray.imageBounds(): Pair<Int, Int>? {
        val options =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeByteArray(this, 0, size, options)
        val width = options.outWidth
        val height = options.outHeight
        return if (width > 0 && height > 0) width to height else null
    }

    private fun Bitmap.encodeJpegWithin(maxBytes: Long): EncodedPhoto? {
        var maxDimension = max(width, height).coerceAtMost(MAX_ENCODE_DIMENSION)
        while (maxDimension > 0) {
            val bitmap = scaledToMaxDimension(maxDimension)
            JPEG_QUALITIES.forEach { quality ->
                val bytes = bitmap.toJpegBytes(quality)
                if (bytes.size.toLong() <= maxBytes) {
                    return EncodedPhoto(bytes = bytes, width = bitmap.width, height = bitmap.height)
                }
            }
            if (maxDimension <= MIN_ENCODE_DIMENSION) {
                break
            }
            maxDimension = (maxDimension * 0.75f).roundToInt().coerceAtLeast(MIN_ENCODE_DIMENSION)
        }
        return null
    }

    private fun Bitmap.scaledToMaxDimension(maxDimension: Int): Bitmap {
        val currentMaxDimension = max(width, height)
        if (currentMaxDimension <= maxDimension) {
            return this
        }
        val ratio = maxDimension.toFloat() / currentMaxDimension.toFloat()
        val targetWidth = (width * ratio).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private fun Bitmap.toJpegBytes(quality: Int): ByteArray {
        val output = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, output)
        return output.toByteArray()
    }

    private data class EncodedPhoto(
        val bytes: ByteArray,
        val width: Int,
        val height: Int
    )
}
