package com.surimap.core.operationalperiod

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
import com.surimap.core.sync.jsonInstant
import com.surimap.core.sync.jsonObject
import com.surimap.core.sync.jsonString
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

data class StartDutyShiftCommand(
    val operationId: String,
    val incidentId: String,
    val opId: String,
    val policePhoneId: String,
    val idempotencyKey: String,
    val sequence: Long,
    val clientTs: Instant,
    val clockOffsetMs: Long? = null,
    val clockSyncedAt: Instant? = null
)

data class EndDutyShiftCommand(
    val operationId: String,
    val incidentId: String,
    val opId: String,
    val dutyShiftId: String,
    val policePhoneId: String,
    val idempotencyKey: String,
    val sequence: Long,
    val clientTs: Instant,
    val memo: String? = null,
    val clockOffsetMs: Long? = null,
    val clockSyncedAt: Instant? = null
)

data class DutyShiftQuery(
    val incidentId: String,
    val opId: String? = null,
    val policePhoneId: String? = null,
    val accountId: String? = null,
    val status: String? = null
)

class DutyShiftRepository(
    private val syncClient: SyncClient? = null,
    private val apiClient: SuriMapApiClient = SuriMapApiClient(),
    private val accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
) {
    suspend fun startDutyShift(command: StartDutyShiftCommand): EnqueueResult {
        val payload = jsonObject(
            "incidentId" to jsonString(command.incidentId),
            "opId" to jsonString(command.opId),
            "policePhoneId" to jsonString(command.policePhoneId),
            "clientTs" to jsonInstant(command.clientTs)
        )
        return enqueue(
            LocalWriteOperation(
                operationId = command.operationId,
                incidentId = command.incidentId,
                policePhoneId = command.policePhoneId,
                dependencyGroup = DependencyGroup.DUTY_SHIFT,
                sequence = command.sequence,
                method = "POST",
                endpoint = "/api/duty-shifts",
                payload = payload,
                bodyHash = canonicalBodyHash(payload),
                idempotencyKey = command.idempotencyKey,
                clientTs = command.clientTs,
                clockOffsetMs = command.clockOffsetMs,
                clockSyncedAt = command.clockSyncedAt,
                opId = command.opId,
                entityType = "duty_shift"
            )
        )
    }

    suspend fun endDutyShift(command: EndDutyShiftCommand): EnqueueResult {
        val payload = jsonObject(
            "incidentId" to jsonString(command.incidentId),
            "opId" to jsonString(command.opId),
            "action" to jsonString("END"),
            "clientTs" to jsonInstant(command.clientTs),
            "memo" to command.memo?.let(::jsonString)
        )
        return enqueue(
            LocalWriteOperation(
                operationId = command.operationId,
                incidentId = command.incidentId,
                policePhoneId = command.policePhoneId,
                dependencyGroup = DependencyGroup.DUTY_SHIFT,
                sequence = command.sequence,
                method = "PATCH",
                endpoint = "/api/duty-shifts/${encodePathSegment(command.dutyShiftId)}",
                payload = payload,
                bodyHash = canonicalBodyHash(payload),
                idempotencyKey = command.idempotencyKey,
                clientTs = command.clientTs,
                clockOffsetMs = command.clockOffsetMs,
                clockSyncedAt = command.clockSyncedAt,
                opId = command.opId,
                entityId = command.dutyShiftId,
                entityType = "duty_shift"
            )
        )
    }

    suspend fun listDutyShifts(query: DutyShiftQuery): SuriMapApiResponse {
        return apiClient.execute(
            SuriMapApiRequest(
                method = "GET",
                path = dutyShiftQueryPath(query),
                accessToken = accessTokenProvider.accessToken()
            )
        )
    }

    private suspend fun enqueue(operation: LocalWriteOperation): EnqueueResult {
        val client = requireNotNull(syncClient) { "SyncClient is required for duty shift writes" }
        return client.enqueue(operation)
    }

    private fun dutyShiftQueryPath(query: DutyShiftQuery): String {
        val queryPairs = listOfNotNull(
            "incidentId" to query.incidentId,
            query.opId?.let { "opId" to it },
            query.policePhoneId?.let { "policePhoneId" to it },
            query.accountId?.let { "accountId" to it },
            query.status?.let { "status" to it }
        )
        return "/api/duty-shifts?${queryPairs.joinToString("&") { (key, value) ->
            "${encodeQueryValue(key)}=${encodeQueryValue(value)}"
        }}"
    }
}

private fun encodePathSegment(value: String): String = encodeQueryValue(value).replace("+", "%20")

private fun encodeQueryValue(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
