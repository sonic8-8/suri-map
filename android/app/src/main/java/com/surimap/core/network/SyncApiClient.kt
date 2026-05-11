package com.surimap.core.network

data class SyncClockNetworkRequest(
    val incidentId: String,
    val policePhoneId: String,
    val clientTs: String
)

data class OutboxRequeueNetworkRequest(
    val operationId: String,
    val incidentId: String,
    val reason: String,
    val clientTs: String,
    val clockOffsetMs: Long,
    val clockSyncedAt: String,
    val attemptCount: Int? = null,
    val policePhoneId: String
)

class SyncApiClient(
    private val apiClient: SuriMapApiClient = SuriMapApiClient(),
    private val accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
) {
    suspend fun syncClock(request: SyncClockNetworkRequest): SuriMapApiResponse {
        return apiClient.execute(
            SuriMapApiRequest(
                method = "POST",
                path = "/api/sync/clock",
                body = jsonObject(
                    "incidentId" to jsonString(request.incidentId),
                    "clientTs" to jsonString(request.clientTs)
                ),
                accessToken = accessTokenProvider.accessToken(),
                policePhoneId = request.policePhoneId
            )
        )
    }

    suspend fun requeue(request: OutboxRequeueNetworkRequest): SuriMapApiResponse {
        val fields = mutableListOf(
            "operationId" to jsonString(request.operationId),
            "incidentId" to jsonString(request.incidentId),
            "reason" to jsonString(request.reason),
            "clientTs" to jsonString(request.clientTs),
            "clockOffsetMs" to request.clockOffsetMs.toString(),
            "clockSyncedAt" to jsonString(request.clockSyncedAt)
        )
        request.attemptCount?.let { fields += "attemptCount" to it.toString() }

        return apiClient.execute(
            SuriMapApiRequest(
                method = "POST",
                path = "/api/sync/outbox/requeue",
                body = jsonObject(*fields.toTypedArray()),
                accessToken = accessTokenProvider.accessToken(),
                policePhoneId = request.policePhoneId
            )
        )
    }
}

private fun jsonObject(vararg fields: Pair<String, String>): String {
    return fields.joinToString(separator = ",", prefix = "{", postfix = "}") { (name, value) ->
        "${jsonString(name)}:$value"
    }
}

private fun jsonString(value: String): String {
    return buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
        append('"')
    }
}
