package com.surimap.core.searcharea

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
import java.time.Instant

data class CreateSearchAreaBoundaryAlertCommand(
    val operationId: String,
    val incidentId: String,
    val opId: String,
    val searchAreaId: String,
    val policePhoneId: String,
    val alertType: String,
    val lon: Double,
    val lat: Double,
    val searchPathId: String? = null,
    val idempotencyKey: String,
    val sequence: Long,
    val clientTs: Instant,
    val clockOffsetMs: Long? = null,
    val clockSyncedAt: Instant? = null
)

class SearchAreaBoundaryAlertRepository(
    private val syncClient: SyncClient
) {
    suspend fun create(command: CreateSearchAreaBoundaryAlertCommand): EnqueueResult {
        val payload = jsonObject(
            "incidentId" to jsonString(command.incidentId),
            "opId" to jsonString(command.opId),
            "searchAreaId" to jsonString(command.searchAreaId),
            "alertType" to jsonString(command.alertType),
            "location" to jsonObject(
                "type" to jsonString("Point"),
                "coordinates" to jsonArray(
                    listOf(
                        jsonCoordinate(command.lon),
                        jsonCoordinate(command.lat)
                    )
                )
            ),
            "clientTs" to jsonInstant(command.clientTs),
            "pathId" to command.searchPathId?.takeIf(String::isNotBlank)?.let(::jsonString),
            "clockOffsetMs" to command.clockOffsetMs?.let(::jsonNumber)
        )
        val operation =
            LocalWriteOperation(
                operationId = command.operationId,
                incidentId = command.incidentId,
                policePhoneId = command.policePhoneId,
                dependencyGroup = DependencyGroup.PATH,
                sequence = command.sequence,
                method = "POST",
                endpoint = "/api/search-area-boundary-alerts",
                payload = payload,
                bodyHash = canonicalBodyHash(payload),
                idempotencyKey = command.idempotencyKey,
                clientTs = command.clientTs,
                clockOffsetMs = command.clockOffsetMs,
                clockSyncedAt = command.clockSyncedAt,
                opId = command.opId,
                entityType = "search_area_boundary_alert"
            )
        return syncClient.enqueue(operation)
    }

    private fun jsonCoordinate(value: Double): String {
        return BigDecimal.valueOf(value)
            .setScale(GPS_COORDINATE_SCALE, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    private companion object {
        const val GPS_COORDINATE_SCALE = 6
    }
}
