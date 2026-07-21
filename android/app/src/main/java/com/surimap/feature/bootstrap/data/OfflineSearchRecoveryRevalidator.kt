package com.surimap.feature.bootstrap.data

import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.network.SuriMapNetworkException
import org.json.JSONArray
import org.json.JSONObject

enum class OfflineSearchInvalidReason {
    IncidentUnavailable,
    OperationalPeriodChanged
}

sealed interface OfflineSearchRevalidationResult {
    data object Valid : OfflineSearchRevalidationResult

    data object Retry : OfflineSearchRevalidationResult

    data class Invalid(
        val reason: OfflineSearchInvalidReason
    ) : OfflineSearchRevalidationResult
}

class OfflineSearchRecoveryRevalidator(
    private val assignedIncidents: suspend () -> SuriMapApiResponse,
    private val operationalPeriods: suspend (String) -> SuriMapApiResponse
) {
    suspend fun validate(
        incidentId: String,
        opId: String
    ): OfflineSearchRevalidationResult {
        val incidentsResponse =
            try {
                assignedIncidents()
            } catch (_: SuriMapNetworkException) {
                return OfflineSearchRevalidationResult.Retry
            }
        if (incidentsResponse.errorCode in INCIDENT_ACCESS_ERRORS) {
            return OfflineSearchRevalidationResult.Invalid(
                OfflineSearchInvalidReason.IncidentUnavailable
            )
        }
        if (!incidentsResponse.isSuccessful || incidentsResponse.body.isNullOrBlank()) {
            return OfflineSearchRevalidationResult.Retry
        }
        val incidents = incidentsResponse.body.parseItems()
            ?: return OfflineSearchRevalidationResult.Retry
        val incident = incidents
            .firstOrNull { item ->
                item.optString("incidentId").ifBlank { item.optString("id") } == incidentId
            }
            ?: return OfflineSearchRevalidationResult.Invalid(
                OfflineSearchInvalidReason.IncidentUnavailable
            )
        val currentOpId =
            incident.optString("currentOpId").takeIf(String::isNotBlank)
                ?: loadCurrentOpId(incidentId)
                ?: return OfflineSearchRevalidationResult.Retry

        return if (currentOpId == opId) {
            OfflineSearchRevalidationResult.Valid
        } else {
            OfflineSearchRevalidationResult.Invalid(
                OfflineSearchInvalidReason.OperationalPeriodChanged
            )
        }
    }

    private suspend fun loadCurrentOpId(incidentId: String): String? {
        val response =
            try {
                operationalPeriods(incidentId)
            } catch (_: SuriMapNetworkException) {
                return null
            }
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return null
        }
        return runCatching {
            JSONObject(response.body).optString("currentOpId").takeIf(String::isNotBlank)
        }.getOrNull()
    }

    private fun String.parseItems(): List<JSONObject>? =
        runCatching {
            val body = trim()
            val items =
                if (body.startsWith("[")) {
                    JSONArray(body)
                } else {
                    JSONObject(body).optJSONArray("items") ?: JSONArray()
                }
            List(items.length()) { index -> items.getJSONObject(index) }
        }.getOrNull()

    private companion object {
        val INCIDENT_ACCESS_ERRORS =
            setOf(
                "incident_closed",
                "police_phone_not_assigned",
                "incident_access_denied",
                "team_not_assigned"
            )
    }
}
