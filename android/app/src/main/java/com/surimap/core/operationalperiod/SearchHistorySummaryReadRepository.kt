package com.surimap.core.operationalperiod

import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.NoAccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.network.SuriMapApiRequest
import com.surimap.core.network.SuriMapApiResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class SearchHistorySummaryQuery(
    val incidentId: String,
    val scopeType: String? = null,
    val scopeId: String? = null,
    val dutyShiftId: String? = null,
    val status: String? = null
)

class SearchHistorySummaryReadRepository(
    private val apiClient: SuriMapApiClient = SuriMapApiClient(),
    private val accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
) {
    suspend fun list(
        operationalPeriodId: String,
        query: SearchHistorySummaryQuery
    ): SuriMapApiResponse {
        return apiClient.execute(
            SuriMapApiRequest(
                method = "GET",
                path = summaryPath(operationalPeriodId, query),
                accessToken = accessTokenProvider.accessToken()
            )
        )
    }

    private fun summaryPath(operationalPeriodId: String, query: SearchHistorySummaryQuery): String {
        val queryPairs = listOfNotNull(
            "incidentId" to query.incidentId,
            query.scopeType?.let { "scopeType" to it },
            query.scopeId?.let { "scopeId" to it },
            query.dutyShiftId?.let { "dutyShiftId" to it },
            query.status?.let { "status" to it }
        )
        val queryString = queryPairs.joinToString("&") { (key, value) ->
            "${encodeQueryValue(key)}=${encodeQueryValue(value)}"
        }
        return "/api/operational-periods/${encodePathSegment(operationalPeriodId)}/search-history-summaries?$queryString"
    }
}

private fun encodePathSegment(value: String): String = encodeQueryValue(value).replace("+", "%20")

private fun encodeQueryValue(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
