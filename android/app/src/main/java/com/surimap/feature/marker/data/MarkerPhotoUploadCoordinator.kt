package com.surimap.feature.marker.data

import com.surimap.core.sync.SyncClient
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_STRING_FIELD = Regex(""""%s"\s*:\s*"([^"]+)"""")
private val JSON_LONG_FIELD = Regex(""""%s"\s*:\s*([0-9]+)""")

data class MarkerPhotoUploadUrlResponseInput(
    val markerId: String?,
    val photoId: String?,
    val uploadUrl: String?,
    val maxSizeBytes: Long? = null,
    val version: Long? = null
) {
    companion object {
        fun fromJson(markerId: String?, body: String?): MarkerPhotoUploadUrlResponseInput? {
            val json = body?.takeIf(String::isNotBlank) ?: return null
            return MarkerPhotoUploadUrlResponseInput(
                markerId = markerId,
                photoId = json.stringField("photoId") ?: return null,
                uploadUrl = json.stringField("uploadUrl") ?: return null,
                maxSizeBytes = json.longField("maxSizeBytes"),
                version = json.longField("version")
            )
        }
    }
}

data class MarkerPhotoUploadPayload(
    val markerId: String?,
    val contentType: String?,
    val bytes: ByteArray,
    val width: Int? = null,
    val height: Int? = null,
    val checksumSha256: String? = null
) {
    val sizeBytes: Long
        get() = bytes.size.toLong()
}

sealed interface MarkerPhotoUploadResult {
    data object Blocked : MarkerPhotoUploadResult

    data class UploadUrlEnqueued(
        val operationId: String,
        val outboxId: String
    ) : MarkerPhotoUploadResult

    data class UploadFailed(
        val reason: String? = null
    ) : MarkerPhotoUploadResult

    data class AttachedEnqueued(
        val operationId: String,
        val outboxId: String
    ) : MarkerPhotoUploadResult
}

data class ObjectStoragePutRequest(
    val uploadUrl: URI,
    val contentType: String,
    val sizeBytes: Long,
    val bytes: ByteArray
)

sealed interface ObjectStoragePutResult {
    data object Success : ObjectStoragePutResult

    data class Failure(
        val reason: String? = null
    ) : ObjectStoragePutResult
}

interface ObjectStorageUploader {
    suspend fun put(request: ObjectStoragePutRequest): ObjectStoragePutResult
}

class HttpObjectStorageUploader(
    private val callFactory: Call.Factory = OkHttpClient()
) : ObjectStorageUploader {
    override suspend fun put(request: ObjectStoragePutRequest): ObjectStoragePutResult = withContext(Dispatchers.IO) {
        val body = request.bytes.toRequestBody(request.contentType.toMediaTypeOrNull())
        val httpRequest =
            Request.Builder()
                .url(request.uploadUrl.toString())
                .put(body)
                .header("Content-Type", request.contentType)
                .build()

        try {
            callFactory.newCall(httpRequest).execute().use { response ->
                if (response.isSuccessful) {
                    ObjectStoragePutResult.Success
                } else {
                    ObjectStoragePutResult.Failure("http_${response.code}")
                }
            }
        } catch (exception: IOException) {
            ObjectStoragePutResult.Failure("network_error")
        }
    }
}

class MarkerPhotoUploadCoordinator(
    syncClient: SyncClient,
    private val uploader: ObjectStorageUploader,
    now: () -> Instant = { Instant.now() },
    clockOffsetMs: () -> Long? = { 0L },
    clockSyncedAt: () -> Instant? = { now() },
    sequenceSource: () -> Long = { System.currentTimeMillis() },
    idFactory: (String) -> String = { _ -> UUID.randomUUID().toString() }
) {
    private val recorder =
        MarkerLocalRecorder(
            syncClient = syncClient,
            now = now,
            clockOffsetMs = clockOffsetMs,
            clockSyncedAt = clockSyncedAt,
            sequenceSource = sequenceSource,
            idFactory = idFactory
        )

    suspend fun requestUploadUrl(
        context: MarkerWriteContext,
        input: MarkerPhotoUploadUrlInput
    ): MarkerPhotoUploadResult {
        return when (val result = recorder.requestPhotoUploadUrl(context = context, input = input)) {
            MarkerWriteResult.Blocked -> MarkerPhotoUploadResult.Blocked
            is MarkerWriteResult.Enqueued ->
                MarkerPhotoUploadResult.UploadUrlEnqueued(
                    operationId = result.operationId,
                    outboxId = result.outboxId
                )
        }
    }

    suspend fun uploadObjectAndAttach(
        context: MarkerWriteContext,
        response: MarkerPhotoUploadUrlResponseInput,
        payload: MarkerPhotoUploadPayload,
        parentOperationId: String?
    ): MarkerPhotoUploadResult {
        if (!context.hasRequiredFields()) {
            return MarkerPhotoUploadResult.Blocked
        }
        val markerId = response.markerId?.takeIf(String::isNotBlank) ?: return MarkerPhotoUploadResult.Blocked
        if (payload.markerId != markerId) {
            return MarkerPhotoUploadResult.Blocked
        }
        val photoId = response.photoId?.takeIf(String::isNotBlank) ?: return MarkerPhotoUploadResult.Blocked
        val contentType = payload.contentType?.takeIf(String::isNotBlank) ?: return MarkerPhotoUploadResult.Blocked
        if (!payload.sizeBytes.isAllowedPhotoSize()) {
            return MarkerPhotoUploadResult.Blocked
        }
        val maxSizeBytes = response.maxSizeBytes
        if (maxSizeBytes != null && payload.sizeBytes > maxSizeBytes) {
            return MarkerPhotoUploadResult.Blocked
        }
        if (maxSizeBytes != null && maxSizeBytes <= 0) {
            return MarkerPhotoUploadResult.Blocked
        }
        if (payload.width != null && payload.width <= 0) {
            return MarkerPhotoUploadResult.Blocked
        }
        if (payload.height != null && payload.height <= 0) {
            return MarkerPhotoUploadResult.Blocked
        }
        val uploadUrl = response.uploadUrl.toUploadUri() ?: return MarkerPhotoUploadResult.Blocked

        val put =
            try {
                uploader.put(
                    ObjectStoragePutRequest(
                        uploadUrl = uploadUrl,
                        contentType = contentType,
                        sizeBytes = payload.sizeBytes,
                        bytes = payload.bytes
                    )
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                return MarkerPhotoUploadResult.UploadFailed(reason = exception.message)
            }

        return when (put) {
            is ObjectStoragePutResult.Failure -> MarkerPhotoUploadResult.UploadFailed(reason = put.reason)
            ObjectStoragePutResult.Success -> enqueueAttach(
                context = context,
                input =
                MarkerPhotoAttachInput(
                    markerId = markerId,
                    photoId = photoId,
                    contentType = contentType,
                    sizeBytes = payload.sizeBytes,
                    width = payload.width,
                    height = payload.height,
                    checksumSha256 = payload.checksumSha256,
                    parentOperationId = parentOperationId
                )
            )
        }
    }

    private suspend fun enqueueAttach(
        context: MarkerWriteContext,
        input: MarkerPhotoAttachInput
    ): MarkerPhotoUploadResult {
        return when (val result = recorder.attachPhoto(context = context, input = input)) {
            MarkerWriteResult.Blocked -> MarkerPhotoUploadResult.Blocked
            is MarkerWriteResult.Enqueued ->
                MarkerPhotoUploadResult.AttachedEnqueued(
                    operationId = result.operationId,
                    outboxId = result.outboxId
                )
        }
    }

    private fun Long.isAllowedPhotoSize(): Boolean = this in 1..MAX_PHOTO_BYTES

    private fun MarkerWriteContext.hasRequiredFields(): Boolean =
        !incidentId.isNullOrBlank() && !opId.isNullOrBlank() && !policePhoneId.isNullOrBlank()

    private fun String?.toUploadUri(): URI? {
        val raw = this?.takeIf(String::isNotBlank) ?: return null
        val uri =
            try {
                URI(raw)
            } catch (_: IllegalArgumentException) {
                return null
            }
        val scheme = uri.scheme ?: return null
        if (scheme != "http" && scheme != "https") {
            return null
        }
        if (uri.host.isNullOrBlank()) {
            return null
        }
        return uri
    }

    private companion object {
        const val MAX_PHOTO_BYTES = 10_485_760L
    }
}

private fun String.stringField(name: String): String? =
    JSON_STRING_FIELD.format(name).find(this)?.groupValues?.get(1)?.takeIf(String::isNotBlank)

private fun String.longField(name: String): Long? =
    JSON_LONG_FIELD.format(name).find(this)?.groupValues?.get(1)?.toLongOrNull()

private fun Regex.format(fieldName: String): Regex = Regex(pattern.format(Regex.escape(fieldName)))
