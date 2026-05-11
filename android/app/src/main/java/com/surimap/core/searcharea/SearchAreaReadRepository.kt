package com.surimap.core.searcharea

import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.NoAccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.network.SuriMapApiRequest
import com.surimap.core.network.SuriMapApiResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class SearchAreaReadRepository(
    private val apiClient: SuriMapApiClient = SuriMapApiClient(),
    private val accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
) {
    suspend fun activeOverall(incidentId: String): SuriMapApiResponse = apiClient.execute(
        SuriMapApiRequest(
            method = "GET",
            path = searchAreaPath(
                incidentId = incidentId,
                areaLevel = "OVERALL",
                status = "ACTIVE"
            ),
            accessToken = accessTokenProvider.accessToken()
        )
    )

    suspend fun list(
        incidentId: String,
        opId: String? = null,
        areaLevel: String? = null,
        status: String? = null
    ): SuriMapApiResponse = apiClient.execute(
        SuriMapApiRequest(
            method = "GET",
            path = searchAreaPath(
                incidentId = incidentId,
                opId = opId,
                areaLevel = areaLevel,
                status = status
            ),
            accessToken = accessTokenProvider.accessToken()
        )
    )

    private fun searchAreaPath(
        incidentId: String,
        opId: String? = null,
        areaLevel: String? = null,
        status: String? = null
    ): String {
        val params = buildList {
            add("incidentId=${encodeQueryValue(incidentId)}")
            if (!opId.isNullOrBlank()) add("opId=${encodeQueryValue(opId)}")
            if (!areaLevel.isNullOrBlank()) add("areaLevel=${encodeQueryValue(areaLevel)}")
            if (!status.isNullOrBlank()) add("status=${encodeQueryValue(status)}")
        }
        return "/api/search-areas?${params.joinToString("&")}"
    }

    private fun encodeQueryValue(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
