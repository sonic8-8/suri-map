package com.surimap.core.operationalperiod

import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.NoAccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.network.SuriMapApiRequest
import com.surimap.core.network.SuriMapApiResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class HandoverTimelineQuery(
    val incidentId: String,
    val scopeType: String? = null,
    val dutyShiftId: String? = null,
    val startAt: String? = null,
    val endAt: String? = null,
    val includeOtherActors: Boolean? = null
)

class HandoverTimelineReadRepository(
    private val apiClient: SuriMapApiClient = SuriMapApiClient(),
    private val accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
) {
    suspend fun get(
        operationalPeriodId: String,
        query: HandoverTimelineQuery
    ): SuriMapApiResponse {
        return apiClient.execute(
            SuriMapApiRequest(
                method = "GET",
                path = timelinePath(operationalPeriodId, query),
                accessToken = accessTokenProvider.accessToken()
            )
        )
    }

    private fun timelinePath(operationalPeriodId: String, query: HandoverTimelineQuery): String {
        val queryPairs = listOfNotNull(
            "incidentId" to query.incidentId,
            query.scopeType?.let { "scopeType" to it },
            query.dutyShiftId?.let { "dutyShiftId" to it },
            query.startAt?.let { "startAt" to it },
            query.endAt?.let { "endAt" to it },
            query.includeOtherActors?.let { "includeOtherActors" to it.toString() }
        )
        val queryString = queryPairs.joinToString("&") { (key, value) ->
            "${encodeQueryValue(key)}=${encodeQueryValue(value)}"
        }
        return "/api/operational-periods/${encodePathSegment(operationalPeriodId)}/handover-timeline?$queryString"
    }
}

private fun encodePathSegment(value: String): String = encodeQueryValue(value).replace("+", "%20")

private fun encodeQueryValue(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
