package com.surimap.core.path

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
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

data class StartSearchPathCommand(
    val operationId: String,
    val searchPathId: String,
    val incidentId: String,
    val opId: String,
    val policePhoneId: String,
    val idempotencyKey: String,
    val sequence: Long,
    val clientTs: Instant,
    val clockOffsetMs: Long? = null,
    val clockSyncedAt: Instant? = null
)

data class EndSearchPathCommand(
    val operationId: String,
    val incidentId: String,
    val opId: String,
    val searchPathId: String,
    val policePhoneId: String,
    val idempotencyKey: String,
    val sequence: Long,
    val clientTs: Instant,
    val clockOffsetMs: Long? = null,
    val clockSyncedAt: Instant? = null
)

enum class SearchPathLifecycleAction {
    PAUSE,
    RESUME,
    END
}

data class PatchSearchPathCommand(
    val operationId: String,
    val incidentId: String,
    val opId: String,
    val searchPathId: String,
    val policePhoneId: String,
    val action: SearchPathLifecycleAction,
    val idempotencyKey: String,
    val sequence: Long,
    val clientTs: Instant,
    val clockOffsetMs: Long? = null,
    val clockSyncedAt: Instant? = null
)

data class AppendPathBatchCommand(
    val operationId: String,
    val incidentId: String,
    val opId: String,
    val searchPathId: String,
    val policePhoneId: String,
    val idempotencyKey: String,
    val sequence: Long,
    val points: List<PathPoint>,
    val clockOffsetMs: Long? = null,
    val clockSyncedAt: Instant? = null
)

data class PathPoint(
    val pointId: String,
    val lon: Double,
    val lat: Double,
    val speedMps: Double? = null,
    val horizontalAccuracyM: Int? = null,
    val clientTs: Instant
)

data class SearchPathQuery(
    val incidentId: String,
    val opId: String? = null,
    val accountId: String? = null,
    val includeGeometry: Boolean? = null,
    val geometryMode: String? = null,
    val sinceVersion: Long? = null,
    val limit: Int? = null,
    val sort: String? = null,
    val movementType: String? = null
)

class SearchPathRepository(
    private val syncClient: SyncClient? = null,
    private val apiClient: SuriMapApiClient = SuriMapApiClient(),
    private val accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
) {
    suspend fun startSearchPath(command: StartSearchPathCommand): EnqueueResult {
        val payload = jsonObject(
            "searchPathId" to jsonString(command.searchPathId),
            "incidentId" to jsonString(command.incidentId),
            "opId" to jsonString(command.opId),
            "clientTs" to jsonInstant(command.clientTs),
            "clockOffsetMs" to command.clockOffsetMs?.let(::jsonNumber)
        )
        return enqueue(
            command.toOperation(
                dependencyGroup = DependencyGroup.PATH,
                method = "POST",
                endpoint = "/api/search-paths",
                payload = payload,
                entityType = "search_path",
                entityId = command.searchPathId
            )
        )
    }

    suspend fun endSearchPath(command: EndSearchPathCommand): EnqueueResult {
        return patchSearchPath(
            PatchSearchPathCommand(
                operationId = command.operationId,
                incidentId = command.incidentId,
                opId = command.opId,
                searchPathId = command.searchPathId,
                policePhoneId = command.policePhoneId,
                action = SearchPathLifecycleAction.END,
                idempotencyKey = command.idempotencyKey,
                sequence = command.sequence,
                clientTs = command.clientTs,
                clockOffsetMs = command.clockOffsetMs,
                clockSyncedAt = command.clockSyncedAt
            )
        )
    }

    suspend fun patchSearchPath(command: PatchSearchPathCommand): EnqueueResult {
        val payload = jsonObject(
            "action" to jsonString(command.action.name),
            "clientTs" to jsonInstant(command.clientTs),
            "clockOffsetMs" to command.clockOffsetMs?.let(::jsonNumber)
        )
        return enqueue(
            command.toOperation(
                method = "PATCH",
                endpoint = "/api/search-paths/${encodePathSegment(command.searchPathId)}",
                payload = payload
            )
        )
    }

    suspend fun appendPathBatch(command: AppendPathBatchCommand): EnqueueResult {
        val payload = jsonObject(
            "incidentId" to jsonString(command.incidentId),
            "opId" to jsonString(command.opId),
            "pathId" to jsonString(command.searchPathId),
            "points" to jsonArray(command.points.map(::pathPointJson)),
            "clockOffsetMs" to command.clockOffsetMs?.let(::jsonNumber)
        )
        return enqueue(
            command.toOperation(
                method = "POST",
                endpoint = "/api/search-paths/batch",
                payload = payload
            )
        )
    }

    suspend fun listSearchPaths(query: SearchPathQuery): SuriMapApiResponse {
        return apiClient.execute(
            SuriMapApiRequest(
                method = "GET",
                path = searchPathQueryPath(query),
                accessToken = accessTokenProvider.accessToken()
            )
        )
    }

    private suspend fun enqueue(operation: LocalWriteOperation): EnqueueResult {
        val client = requireNotNull(syncClient) { "SyncClient is required for field writes" }
        return client.enqueue(operation)
    }

    private fun StartSearchPathCommand.toOperation(
        dependencyGroup: DependencyGroup,
        method: String,
        endpoint: String,
        payload: String,
        entityType: String,
        entityId: String?
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

    private fun EndSearchPathCommand.toOperation(
        method: String,
        endpoint: String,
        payload: String
    ): LocalWriteOperation =
        PatchSearchPathCommand(
            operationId = operationId,
            incidentId = incidentId,
            opId = opId,
            searchPathId = searchPathId,
            policePhoneId = policePhoneId,
            action = SearchPathLifecycleAction.END,
            idempotencyKey = idempotencyKey,
            sequence = sequence,
            clientTs = clientTs,
            clockOffsetMs = clockOffsetMs,
            clockSyncedAt = clockSyncedAt
        ).toOperation(method, endpoint, payload)

    private fun PatchSearchPathCommand.toOperation(
        method: String,
        endpoint: String,
        payload: String
    ): LocalWriteOperation {
        return LocalWriteOperation(
            operationId = operationId,
            incidentId = incidentId,
            policePhoneId = policePhoneId,
            dependencyGroup = DependencyGroup.PATH,
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
            entityId = searchPathId,
            entityType = "search_path"
        )
    }

    private fun AppendPathBatchCommand.toOperation(
        method: String,
        endpoint: String,
        payload: String
    ): LocalWriteOperation {
        return LocalWriteOperation(
            operationId = operationId,
            incidentId = incidentId,
            policePhoneId = policePhoneId,
            dependencyGroup = DependencyGroup.PATH,
            sequence = sequence,
            method = method,
            endpoint = endpoint,
            payload = payload,
            bodyHash = canonicalBodyHash(payload),
            idempotencyKey = idempotencyKey,
            clientTs = points.firstOrNull()?.clientTs ?: Instant.EPOCH,
            clockOffsetMs = clockOffsetMs,
            clockSyncedAt = clockSyncedAt,
            opId = opId,
            entityId = searchPathId,
            entityType = "search_path"
        )
    }

    private fun pathPointJson(point: PathPoint): String {
        return jsonObject(
            "pointId" to jsonString(point.pointId),
            "lon" to jsonCoordinate(point.lon),
            "lat" to jsonCoordinate(point.lat),
            "speedMps" to point.speedMps?.let(::jsonNumber),
            "horizontalAccuracyM" to point.horizontalAccuracyM?.let(::jsonNumber),
            "clientTs" to jsonInstant(point.clientTs)
        )
    }

    private fun jsonCoordinate(value: Double): String {
        return BigDecimal.valueOf(value)
            .setScale(GPS_COORDINATE_SCALE, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    private fun searchPathQueryPath(query: SearchPathQuery): String {
        val queryPairs = listOfNotNull(
            "incidentId" to query.incidentId,
            query.opId?.let { "opId" to it },
            query.accountId?.let { "accountId" to it },
            query.includeGeometry?.let { "includeGeometry" to it.toString() },
            query.geometryMode?.let { "geometryMode" to it },
            query.sinceVersion?.let { "sinceVersion" to it.toString() },
            query.limit?.let { "limit" to it.toString() },
            query.sort?.let { "sort" to it },
            query.movementType?.let { "movementType" to it }
        )
        return "/api/search-paths?${queryPairs.joinToString("&") { (key, value) ->
            "${encodeQueryValue(key)}=${encodeQueryValue(value)}"
        }}"
    }
}

private fun encodePathSegment(value: String): String = encodeQueryValue(value).replace("+", "%20")

private fun encodeQueryValue(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}

private const val GPS_COORDINATE_SCALE = 6
