package com.surimap.feature.marker.data

import com.surimap.core.database.LocalMarkerDao
import com.surimap.core.database.LocalMarkerEntity
import com.surimap.core.marker.CreateMarkerCommand
import com.surimap.core.marker.CreateMarkerPhotoCommand
import com.surimap.core.marker.DeleteMarkerCommand
import com.surimap.core.marker.MarkerRepository
import com.surimap.core.marker.PhotoAttachCommand
import com.surimap.core.marker.PhotoUploadUrlCommand
import com.surimap.core.marker.UpdateMarkerCommand
import com.surimap.core.sync.OutboxStatus
import com.surimap.core.sync.SyncClient
import java.time.Instant
import java.util.UUID

data class MarkerWriteContext(
    val incidentId: String?,
    val opId: String?,
    val policePhoneId: String?
)

data class MarkerLocation(
    val lon: Double,
    val lat: Double
)

data class MarkerUpsertInput(
    val markerId: String? = null,
    val type: String? = null,
    val location: MarkerLocation? = null,
    val supportRequestType: String? = null,
    val memo: String? = null,
    val photos: List<MarkerCreatePhotoInput> = emptyList()
)

data class MarkerCreatePhotoInput(
    val photoId: String?,
    val sizeBytes: Long,
    val contentType: String?,
    val width: Int? = null,
    val height: Int? = null,
    val checksumSha256: String? = null
)

data class MarkerPhotoUploadUrlInput(
    val markerId: String?,
    val contentType: String?,
    val sizeBytes: Long,
    val checksumSha256: String? = null
)

data class MarkerPhotoAttachInput(
    val markerId: String?,
    val photoId: String?,
    val contentType: String?,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val checksumSha256: String? = null,
    val parentOperationId: String? = null
)

sealed interface MarkerWriteResult {
    data object Blocked : MarkerWriteResult

    data class Enqueued(
        val operationId: String,
        val outboxId: String
    ) : MarkerWriteResult
}

class MarkerLocalRecorder(
    syncClient: SyncClient,
    private val localMarkerDao: LocalMarkerDao? = null,
    private val now: () -> Instant = { Instant.now() },
    private val clockOffsetMs: () -> Long? = { 0L },
    private val clockSyncedAt: () -> Instant? = { now() },
    private val sequenceSource: () -> Long = { System.currentTimeMillis() },
    private val idFactory: (String) -> String = { _ -> UUID.randomUUID().toString() }
) {
    private val repository = MarkerRepository(syncClient = syncClient)

    suspend fun createMarker(
        context: MarkerWriteContext,
        input: MarkerUpsertInput
    ): MarkerWriteResult {
        val valid = context.valid() ?: return MarkerWriteResult.Blocked
        val type = input.type?.takeIf(String::isNotBlank) ?: return MarkerWriteResult.Blocked
        val location = input.location?.takeIf { it.isValid() } ?: return MarkerWriteResult.Blocked
        val supportRequestType = input.supportRequestType?.takeIf(String::isNotBlank)
        if (type == SUPPORT_REQUEST_TYPE && supportRequestType == null) {
            return MarkerWriteResult.Blocked
        }
        val operationId = idFactory("op-marker-create")
        val markerId = input.markerId?.takeIf(String::isNotBlank) ?: operationId
        val photos = input.photos.toCreatePhotoCommands() ?: return MarkerWriteResult.Blocked
        val clientTs = now()
        val result =
            repository.createMarker(
                CreateMarkerCommand(
                    operationId = operationId,
                    markerId = markerId,
                    incidentId = valid.incidentId,
                    opId = valid.opId,
                    policePhoneId = valid.policePhoneId,
                    idempotencyKey = "idem-$operationId",
                    sequence = sequenceSource(),
                    type = type,
                    lon = location.lon,
                    lat = location.lat,
                    supportRequestType = supportRequestType,
                    memo = input.memo?.takeIf(String::isNotBlank),
                    clientTs = clientTs,
                    clockOffsetMs = clockOffsetMs(),
                    clockSyncedAt = clockSyncedAt(),
                    photos = photos
                )
            )
        if (result.status != OutboxStatus.FAILED_FINAL) {
            val createdAt = clientTs.toEpochMilli()
            val localMirrorStatus =
                localMarkerDao?.findOutboxLocalMirrorStatus(result.outboxId)
                    ?.takeIf(String::isNotBlank)
                    ?: result.harnessStatus.name
                localMarkerDao?.upsert(
                LocalMarkerEntity(
                    localMarkerId = markerId,
                    outboxId = result.outboxId,
                    operationId = operationId,
                    incidentId = valid.incidentId,
                    opId = valid.opId,
                    policePhoneId = valid.policePhoneId,
                    type = type,
                    supportRequestType = supportRequestType,
                    memo = input.memo?.takeIf(String::isNotBlank),
                    lon = location.lon,
                    lat = location.lat,
                    syncStatus = localMirrorStatus,
                    createdAtMillis = createdAt,
                    updatedAtMillis = createdAt
                )
            )
        }
        return result.enqueued()
    }

    suspend fun updateMarker(
        context: MarkerWriteContext,
        markerId: String?,
        version: Long,
        input: MarkerUpsertInput
    ): MarkerWriteResult {
        val valid = context.valid() ?: return MarkerWriteResult.Blocked
        val id = markerId?.takeIf(String::isNotBlank) ?: return MarkerWriteResult.Blocked
        if (version <= 0) {
            return MarkerWriteResult.Blocked
        }
        val location = input.location
        if (location != null && !location.isValid()) {
            return MarkerWriteResult.Blocked
        }
        val operationId = idFactory("op-marker-update")
        val clientTs = now()
        val result =
            repository.updateMarker(
                UpdateMarkerCommand(
                    operationId = operationId,
                    incidentId = valid.incidentId,
                    opId = valid.opId,
                    markerId = id,
                    policePhoneId = valid.policePhoneId,
                    idempotencyKey = "idem-$operationId",
                    sequence = sequenceSource(),
                    version = version,
                    lon = location?.lon,
                    lat = location?.lat,
                    memo = input.memo?.takeIf(String::isNotBlank),
                    type = input.type?.takeIf(String::isNotBlank),
                    clientTs = clientTs,
                    clockOffsetMs = clockOffsetMs(),
                    clockSyncedAt = clockSyncedAt()
                )
            )
        return result.enqueued()
    }

    suspend fun deleteMarker(
        context: MarkerWriteContext,
        markerId: String?,
        version: Long,
        reason: String? = null
    ): MarkerWriteResult {
        val valid = context.valid() ?: return MarkerWriteResult.Blocked
        val id = markerId?.takeIf(String::isNotBlank) ?: return MarkerWriteResult.Blocked
        if (version <= 0) {
            return MarkerWriteResult.Blocked
        }
        val operationId = idFactory("op-marker-delete")
        val clientTs = now()
        val result =
            repository.deleteMarker(
                DeleteMarkerCommand(
                    operationId = operationId,
                    incidentId = valid.incidentId,
                    opId = valid.opId,
                    markerId = id,
                    policePhoneId = valid.policePhoneId,
                    idempotencyKey = "idem-$operationId",
                    sequence = sequenceSource(),
                    version = version,
                    reason = reason?.takeIf(String::isNotBlank),
                    clientTs = clientTs,
                    clockOffsetMs = clockOffsetMs(),
                    clockSyncedAt = clockSyncedAt()
                )
            )
        return result.enqueued()
    }

    suspend fun requestPhotoUploadUrl(
        context: MarkerWriteContext,
        input: MarkerPhotoUploadUrlInput
    ): MarkerWriteResult {
        val valid = context.valid() ?: return MarkerWriteResult.Blocked
        val markerId = input.markerId?.takeIf(String::isNotBlank) ?: return MarkerWriteResult.Blocked
        val contentType = input.contentType?.takeIf(String::isNotBlank) ?: return MarkerWriteResult.Blocked
        if (!input.sizeBytes.isAllowedPhotoSize()) {
            return MarkerWriteResult.Blocked
        }
        val operationId = idFactory("op-photo-upload-url")
        val clientTs = now()
        val result =
            repository.requestPhotoUploadUrl(
                PhotoUploadUrlCommand(
                    operationId = operationId,
                    incidentId = valid.incidentId,
                    opId = valid.opId,
                    markerId = markerId,
                    policePhoneId = valid.policePhoneId,
                    idempotencyKey = "idem-$operationId",
                    sequence = sequenceSource(),
                    contentType = contentType,
                    sizeBytes = input.sizeBytes,
                    checksumSha256 = input.checksumSha256?.takeIf(String::isNotBlank),
                    clientTs = clientTs,
                    clockOffsetMs = clockOffsetMs(),
                    clockSyncedAt = clockSyncedAt()
                )
            )
        return result.enqueued()
    }

    suspend fun attachPhoto(
        context: MarkerWriteContext,
        input: MarkerPhotoAttachInput
    ): MarkerWriteResult {
        val valid = context.valid() ?: return MarkerWriteResult.Blocked
        val markerId = input.markerId?.takeIf(String::isNotBlank) ?: return MarkerWriteResult.Blocked
        val photoId = input.photoId?.takeIf(String::isNotBlank) ?: return MarkerWriteResult.Blocked
        val contentType = input.contentType?.takeIf(String::isNotBlank) ?: return MarkerWriteResult.Blocked
        if (!input.sizeBytes.isAllowedPhotoSize()) {
            return MarkerWriteResult.Blocked
        }
        if (input.width != null && input.width <= 0) {
            return MarkerWriteResult.Blocked
        }
        if (input.height != null && input.height <= 0) {
            return MarkerWriteResult.Blocked
        }
        val operationId = idFactory("op-photo-attach")
        val clientTs = now()
        val result =
            repository.attachPhoto(
                PhotoAttachCommand(
                    operationId = operationId,
                    incidentId = valid.incidentId,
                    opId = valid.opId,
                    markerId = markerId,
                    photoId = photoId,
                    policePhoneId = valid.policePhoneId,
                    idempotencyKey = "idem-$operationId",
                    sequence = sequenceSource(),
                    sizeBytes = input.sizeBytes,
                    contentType = contentType,
                    width = input.width,
                    height = input.height,
                    checksumSha256 = input.checksumSha256?.takeIf(String::isNotBlank),
                    clientTs = clientTs,
                    clockOffsetMs = clockOffsetMs(),
                    clockSyncedAt = clockSyncedAt(),
                    parentOperationId = input.parentOperationId?.takeIf(String::isNotBlank)
                )
            )
        return result.enqueued()
    }

    private fun MarkerWriteContext.valid(): RequiredMarkerContext? {
        val incidentId = incidentId?.takeIf(String::isNotBlank) ?: return null
        val opId = opId?.takeIf(String::isNotBlank) ?: return null
        val policePhoneId = policePhoneId?.takeIf(String::isNotBlank) ?: return null
        return RequiredMarkerContext(
            incidentId = incidentId,
            opId = opId,
            policePhoneId = policePhoneId
        )
    }

    private fun MarkerLocation.isValid(): Boolean =
        lon.isFinite() && lat.isFinite() && lon in -180.0..180.0 && lat in -90.0..90.0

    private fun Long.isAllowedPhotoSize(): Boolean = this in 1..MAX_PHOTO_BYTES

    private fun List<MarkerCreatePhotoInput>.toCreatePhotoCommands(): List<CreateMarkerPhotoCommand>? {
        if (size > MAX_PHOTO_COUNT) {
            return null
        }
        val seenPhotoIds = mutableSetOf<String>()
        return map { photo ->
            val photoId = photo.photoId?.takeIf(String::isNotBlank) ?: return null
            val contentType = photo.contentType?.takeIf(String::isNotBlank) ?: return null
            if (!seenPhotoIds.add(photoId) || !photo.sizeBytes.isAllowedPhotoSize()) {
                return null
            }
            if (photo.width != null && photo.width <= 0) {
                return null
            }
            if (photo.height != null && photo.height <= 0) {
                return null
            }
            CreateMarkerPhotoCommand(
                photoId = photoId,
                sizeBytes = photo.sizeBytes,
                contentType = contentType,
                width = photo.width,
                height = photo.height,
                checksumSha256 = photo.checksumSha256?.takeIf(String::isNotBlank)
            )
        }
    }

    private fun com.surimap.core.sync.EnqueueResult.enqueued(): MarkerWriteResult.Enqueued =
        MarkerWriteResult.Enqueued(
            operationId = operationId,
            outboxId = outboxId
        )

    private data class RequiredMarkerContext(
        val incidentId: String,
        val opId: String,
        val policePhoneId: String
    )

    private companion object {
        const val SUPPORT_REQUEST_TYPE = "SUPPORT_REQUEST"
        const val MAX_PHOTO_COUNT = 10
        const val MAX_PHOTO_BYTES = 10_485_760L
    }
}
