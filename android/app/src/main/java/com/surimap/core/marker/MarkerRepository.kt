package com.surimap.core.marker

import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.NoAccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.network.SuriMapApiRequest
import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.sync.DependencyGroup
import com.surimap.core.sync.EnqueueResult
import com.surimap.core.sync.LocalWriteOperation
import com.surimap.core.sync.SyncClient
import com.surimap.core.sync.canonicalBodyHash
import com.surimap.core.sync.jsonArray
import com.surimap.core.sync.jsonInstant
import com.surimap.core.sync.jsonNumber
import com.surimap.core.sync.jsonObject
import com.surimap.core.sync.jsonString
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

data class CreateMarkerCommand(
    val operationId: String,
    val incidentId: String,
    val opId: String,
    val policePhoneId: String,
    val idempotencyKey: String,
    val sequence: Long,
    val type: String,
    val lon: Double,
    val lat: Double,
    val supportRequestType: String? = null,
    val memo: String? = null,
    val clientTs: Instant,
    val clockOffsetMs: Long? = null,
    val clockSyncedAt: Instant? = null
)

data class UpdateMarkerCommand(
    val operationId: String,
    val incidentId: String,
    val opId: String,
    val markerId: String,
    val policePhoneId: String,
    val idempotencyKey: String,
    val sequence: Long,
    val version: Long,
    val lon: Double? = null,
    val lat: Double? = null,
    val memo: String? = null,
    val type: String? = null,
    val clientTs: Instant,
    val clockOffsetMs: Long? = null,
    val clockSyncedAt: Instant? = null
)

data class DeleteMarkerCommand(
    val operationId: String,
    val incidentId: String,
    val opId: String,
    val markerId: String,
    val policePhoneId: String,
    val idempotencyKey: String,
    val sequence: Long,
    val version: Long,
    val reason: String? = null,
    val clientTs: Instant,
    val clockOffsetMs: Long? = null,
    val clockSyncedAt: Instant? = null
)

data class PhotoUploadUrlCommand(
    val operationId: String,
    val incidentId: String,
    val opId: String,
    val markerId: String,
    val policePhoneId: String,
    val idempotencyKey: String,
    val sequence: Long,
    val contentType: String,
    val sizeBytes: Long,
    val checksumSha256: String? = null,
    val clientTs: Instant,
    val clockOffsetMs: Long? = null,
    val clockSyncedAt: Instant? = null,
    val parentOperationId: String? = null
)

data class PhotoAttachCommand(
    val operationId: String,
    val incidentId: String,
    val opId: String,
    val markerId: String,
    val photoId: String,
    val policePhoneId: String,
    val idempotencyKey: String,
    val sequence: Long,
    val sizeBytes: Long,
    val contentType: String,
    val width: Int? = null,
    val height: Int? = null,
    val checksumSha256: String? = null,
    val clientTs: Instant,
    val clockOffsetMs: Long? = null,
    val clockSyncedAt: Instant? = null,
    val parentOperationId: String? = null
)

data class MarkerReadQuery(
    val incidentId: String,
    val opId: String? = null,
    val type: String? = null,
    val status: String? = null
)

class MarkerRepository(
    private val syncClient: SyncClient? = null,
    private val apiClient: SuriMapApiClient = SuriMapApiClient(),
    private val accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
) {
    suspend fun createMarker(command: CreateMarkerCommand): EnqueueResult {
        val payload = jsonObject(
            "incidentId" to jsonString(command.incidentId),
            "opId" to jsonString(command.opId),
            "type" to jsonString(command.type),
            "location" to geoJsonPoint(command.lon, command.lat),
            "supportRequestType" to command.supportRequestType?.let(::jsonString),
            "memo" to command.memo?.let(::jsonString),
            "clientTs" to jsonInstant(command.clientTs),
            "clockOffsetMs" to command.clockOffsetMs?.let(::jsonNumber)
        )
        return enqueue(
            command.toOperation(
                dependencyGroup = DependencyGroup.MARKER,
                method = "POST",
                endpoint = "/api/markers",
                payload = payload,
                entityId = null,
                entityType = "marker"
            )
        )
    }

    suspend fun updateMarker(command: UpdateMarkerCommand): EnqueueResult {
        val payload = jsonObject(
            "version" to jsonNumber(command.version),
            "location" to markerLocation(command.lon, command.lat),
            "memo" to command.memo?.let(::jsonString),
            "type" to command.type?.let(::jsonString)
        )
        return enqueue(
            command.toOperation(
                method = "PATCH",
                endpoint = "/api/markers/${encodePathSegment(command.markerId)}",
                payload = payload
            )
        )
    }

    suspend fun deleteMarker(command: DeleteMarkerCommand): EnqueueResult {
        val payload = jsonObject(
            "version" to jsonNumber(command.version),
            "reason" to command.reason?.let(::jsonString)
        )
        return enqueue(
            command.toOperation(
                method = "DELETE",
                endpoint = "/api/markers/${encodePathSegment(command.markerId)}",
                payload = payload
            )
        )
    }

    suspend fun requestPhotoUploadUrl(command: PhotoUploadUrlCommand): EnqueueResult {
        val payload = jsonObject(
            "contentType" to jsonString(command.contentType),
            "sizeBytes" to jsonNumber(command.sizeBytes),
            "checksumSha256" to command.checksumSha256?.let(::jsonString)
        )
        return enqueue(
            command.toOperation(
                method = "POST",
                endpoint = "/api/markers/${encodePathSegment(command.markerId)}/photos/upload-url",
                payload = payload,
                entityId = command.markerId
            )
        )
    }

    suspend fun attachPhoto(command: PhotoAttachCommand): EnqueueResult {
        val payload = jsonObject(
            "sizeBytes" to jsonNumber(command.sizeBytes),
            "contentType" to jsonString(command.contentType),
            "width" to command.width?.let(::jsonNumber),
            "height" to command.height?.let(::jsonNumber),
            "checksumSha256" to command.checksumSha256?.let(::jsonString)
        )
        return enqueue(
            command.toOperation(
                method = "POST",
                endpoint = "/api/markers/${encodePathSegment(command.markerId)}/photos/${encodePathSegment(command.photoId)}/attach",
                payload = payload
            )
        )
    }

    suspend fun listMarkers(query: MarkerReadQuery): SuriMapApiResponse {
        return apiClient.execute(
            SuriMapApiRequest(
                method = "GET",
                path = markerReadQueryPath(query),
                accessToken = accessTokenProvider.accessToken()
            )
        )
    }

    private suspend fun enqueue(operation: LocalWriteOperation): EnqueueResult {
        val client = requireNotNull(syncClient) { "SyncClient is required for marker writes" }
        return client.enqueue(operation)
    }

    private fun CreateMarkerCommand.toOperation(
        dependencyGroup: DependencyGroup,
        method: String,
        endpoint: String,
        payload: String,
        entityId: String?,
        entityType: String
    ): LocalWriteOperation {
        return LocalWriteOperation(
            operationId = operationId,
            incidentId = incidentId,
            policePhoneId = policePhoneId,
            dependencyGroup = dependencyGroup,
            sequence = sequence,
            method = method,
            endpoint = endpoint,
            payload = payload,
            bodyHash = canonicalBodyHash(payload),
            idempotencyKey = idempotencyKey,
            clientTs = clientTs,
            clockOffsetMs = clockOffsetMs,
            clockSyncedAt = clockSyncedAt,
            opId = opId,
            entityId = entityId,
            entityType = entityType
        )
    }

    private fun UpdateMarkerCommand.toOperation(
        method: String,
        endpoint: String,
        payload: String
    ): LocalWriteOperation {
        return LocalWriteOperation(
            operationId = operationId,
            incidentId = incidentId,
            policePhoneId = policePhoneId,
            dependencyGroup = DependencyGroup.MARKER,
            sequence = sequence,
            method = method,
            endpoint = endpoint,
            payload = payload,
            bodyHash = canonicalBodyHash(payload),
            idempotencyKey = idempotencyKey,
            clientTs = clientTs,
            clockOffsetMs = clockOffsetMs,
            clockSyncedAt = clockSyncedAt,
            opId = opId,
            entityId = markerId,
            entityType = "marker"
        )
    }

    private fun DeleteMarkerCommand.toOperation(
        method: String,
        endpoint: String,
        payload: String
    ): LocalWriteOperation {
        return LocalWriteOperation(
            operationId = operationId,
            incidentId = incidentId,
            policePhoneId = policePhoneId,
            dependencyGroup = DependencyGroup.MARKER,
            sequence = sequence,
            method = method,
            endpoint = endpoint,
            payload = payload,
            bodyHash = canonicalBodyHash(payload),
            idempotencyKey = idempotencyKey,
            clientTs = clientTs,
            clockOffsetMs = clockOffsetMs,
            clockSyncedAt = clockSyncedAt,
            opId = opId,
            entityId = markerId,
            entityType = "marker"
        )
    }

    private fun PhotoUploadUrlCommand.toOperation(
        method: String,
        endpoint: String,
        payload: String,
        entityId: String
    ): LocalWriteOperation {
        return LocalWriteOperation(
            operationId = operationId,
            incidentId = incidentId,
            policePhoneId = policePhoneId,
            dependencyGroup = DependencyGroup.PHOTO,
            sequence = sequence,
            method = method,
            endpoint = endpoint,
            payload = payload,
            bodyHash = canonicalBodyHash(payload),
            idempotencyKey = idempotencyKey,
            clientTs = clientTs,
            clockOffsetMs = clockOffsetMs,
            clockSyncedAt = clockSyncedAt,
            opId = opId,
            entityId = entityId,
            entityType = "marker_photo",
            parentOperationId = parentOperationId
        )
    }

    private fun PhotoAttachCommand.toOperation(
        method: String,
        endpoint: String,
        payload: String
    ): LocalWriteOperation {
        return LocalWriteOperation(
            operationId = operationId,
            incidentId = incidentId,
            policePhoneId = policePhoneId,
            dependencyGroup = DependencyGroup.PHOTO,
            sequence = sequence,
            method = method,
            endpoint = endpoint,
            payload = payload,
            bodyHash = canonicalBodyHash(payload),
            idempotencyKey = idempotencyKey,
            clientTs = clientTs,
            clockOffsetMs = clockOffsetMs,
            clockSyncedAt = clockSyncedAt,
            opId = opId,
            entityId = photoId,
            entityType = "marker_photo",
            parentOperationId = parentOperationId
        )
    }

    private fun markerReadQueryPath(query: MarkerReadQuery): String {
        val queryPairs = listOfNotNull(
            "incidentId" to query.incidentId,
            query.opId?.let { "opId" to it },
            query.type?.let { "type" to it },
            query.status?.let { "status" to it }
        )
        return "/api/markers?${queryPairs.joinToString("&") { (key, value) ->
            "${encodeQueryValue(key)}=${encodeQueryValue(value)}"
        }}"
    }
}

private fun markerLocation(lon: Double?, lat: Double?): String? {
    if (lon == null || lat == null) {
        return null
    }
    return geoJsonPoint(lon, lat)
}

private fun geoJsonPoint(lon: Double, lat: Double): String {
    return jsonObject(
        "type" to jsonString("Point"),
        "coordinates" to jsonArray(listOf(jsonNumber(lon), jsonNumber(lat)))
    )
}

private fun encodePathSegment(value: String): String = encodeQueryValue(value).replace("+", "%20")

private fun encodeQueryValue(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
