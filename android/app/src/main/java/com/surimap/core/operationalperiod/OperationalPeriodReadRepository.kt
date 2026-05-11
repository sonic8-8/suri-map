package com.surimap.core.operationalperiod

import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.NoAccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.network.SuriMapApiRequest
import com.surimap.core.network.SuriMapApiResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class OperationalPeriodReadRepository(
    private val apiClient: SuriMapApiClient = SuriMapApiClient(),
    private val accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
) {
    suspend fun list(incidentId: String): SuriMapApiResponse = apiClient.execute(
        SuriMapApiRequest(
            method = "GET",
            path = "/api/incidents/${encodePathSegment(incidentId)}/operational-periods",
            accessToken = accessTokenProvider.accessToken()
        )
    )

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
