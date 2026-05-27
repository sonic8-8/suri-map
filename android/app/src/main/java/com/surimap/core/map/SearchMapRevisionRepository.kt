package com.surimap.core.map

import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.NoAccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.network.SuriMapApiRequest
import com.surimap.core.network.SuriMapApiResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class SearchMapRevisionQuery(
    val incidentId: String,
    val opId: String? = null,
    val policePhoneId: String? = null
)

class SearchMapRevisionRepository(
    private val apiClient: SuriMapApiClient = SuriMapApiClient(),
    private val accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
) {
    suspend fun revisions(query: SearchMapRevisionQuery): SuriMapApiResponse {
        return apiClient.execute(
            SuriMapApiRequest(
                method = "GET",
                path = mapRevisionPath(query),
                accessToken = accessTokenProvider.accessToken()
            )
        )
    }

    private fun mapRevisionPath(query: SearchMapRevisionQuery): String {
        val params = buildList {
            query.opId?.takeIf(String::isNotBlank)?.let { add("opId=${encodeQueryValue(it)}") }
            query.policePhoneId?.takeIf(String::isNotBlank)?.let {
                add("policePhoneId=${encodeQueryValue(it)}")
            }
        }
        val queryString =
            params.takeIf(List<String>::isNotEmpty)?.joinToString("&")?.let { "?$it" }.orEmpty()
        return "/api/incidents/${encodePathSegment(query.incidentId)}/map-revisions$queryString"
    }

    private fun encodePathSegment(value: String): String = encodeQueryValue(value).replace("+", "%20")

    private fun encodeQueryValue(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    }
}
