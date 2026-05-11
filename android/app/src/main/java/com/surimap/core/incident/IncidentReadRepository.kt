package com.surimap.core.incident

import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.NoAccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.network.SuriMapApiRequest
import com.surimap.core.network.SuriMapApiResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class IncidentReadRepository(
    private val apiClient: SuriMapApiClient = SuriMapApiClient(),
    private val accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
) {
    suspend fun list(status: String? = null): SuriMapApiResponse {
        return apiClient.execute(
            SuriMapApiRequest(
                method = "GET",
                path = incidentListPath(status),
                accessToken = accessTokenProvider.accessToken()
            )
        )
    }

    suspend fun detail(incidentId: String): SuriMapApiResponse {
        return apiClient.execute(
            SuriMapApiRequest(
                method = "GET",
                path = "/api/incidents/${encodePathSegment(incidentId)}",
                accessToken = accessTokenProvider.accessToken()
            )
        )
    }

    private fun incidentListPath(status: String?): String {
        if (status.isNullOrBlank()) {
            return "/api/incidents"
        }
        return "/api/incidents?status=${encodeQueryValue(status)}"
    }

    private fun encodePathSegment(value: String): String = encodeQueryValue(value).replace("+", "%20")

    private fun encodeQueryValue(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    }
}
