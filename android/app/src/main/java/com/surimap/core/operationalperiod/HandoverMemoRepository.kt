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

data class CreateHandoverMemoCommand(
    val operationId: String,
    val incidentId: String,
    val opId: String,
    val policePhoneId: String,
    val idempotencyKey: String,
    val sequence: Long,
    val memoTargetType: String,
    val memoTargetId: String? = null,
    val content: String,
    val clientTs: Instant,
    val clockOffsetMs: Long? = null,
    val clockSyncedAt: Instant? = null
)

data class HandoverMemoQuery(
    val incidentId: String,
    val opId: String? = null,
    val memoTargetType: String? = null,
    val memoTargetId: String? = null
)

class HandoverMemoRepository(
    private val syncClient: SyncClient? = null,
    private val apiClient: SuriMapApiClient = SuriMapApiClient(),
    private val accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
) {
    suspend fun createHandoverMemo(command: CreateHandoverMemoCommand): EnqueueResult {
        val payload = jsonObject(
            "incidentId" to jsonString(command.incidentId),
            "opId" to jsonString(command.opId),
            "memoTargetType" to jsonString(command.memoTargetType),
            "memoTargetId" to command.memoTargetId?.let(::jsonString),
            "content" to jsonString(command.content),
            "clientTs" to jsonInstant(command.clientTs)
        )
        return enqueue(
            LocalWriteOperation(
                operationId = command.operationId,
                incidentId = command.incidentId,
                policePhoneId = command.policePhoneId,
                dependencyGroup = DependencyGroup.HANDOVER_MEMO,
                sequence = command.sequence,
                method = "POST",
                endpoint = "/api/handover-memos",
                payload = payload,
                bodyHash = canonicalBodyHash(payload),
                idempotencyKey = command.idempotencyKey,
                clientTs = command.clientTs,
                clockOffsetMs = command.clockOffsetMs,
                clockSyncedAt = command.clockSyncedAt,
                opId = command.opId,
                entityType = "handover_memo"
            )
        )
    }

    suspend fun listHandoverMemos(query: HandoverMemoQuery): SuriMapApiResponse {
        return apiClient.execute(
            SuriMapApiRequest(
                method = "GET",
                path = handoverMemoQueryPath(query),
                accessToken = accessTokenProvider.accessToken()
            )
        )
    }

    private suspend fun enqueue(operation: LocalWriteOperation): EnqueueResult {
        val client = requireNotNull(syncClient) { "SyncClient is required for handover memo writes" }
        return client.enqueue(operation)
    }

    private fun handoverMemoQueryPath(query: HandoverMemoQuery): String {
        val queryPairs = listOfNotNull(
            "incidentId" to query.incidentId,
            query.opId?.let { "opId" to it },
            query.memoTargetType?.let { "memoTargetType" to it },
            query.memoTargetId?.let { "memoTargetId" to it }
        )
        return "/api/handover-memos?${queryPairs.joinToString("&") { (key, value) ->
            "${encodeQueryValue(key)}=${encodeQueryValue(value)}"
        }}"
    }
}

private fun encodeQueryValue(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
