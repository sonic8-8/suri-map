package com.surimap.feature.incidents.data

import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.operationalperiod.OperationalPeriodReadRepository
import com.surimap.feature.incidents.ui.AssignedIncidentUiModel
import com.surimap.ui.navigation.IncidentContext
import org.json.JSONObject

class IncidentSessionContextResolver(
    private val operationalPeriods: suspend (String) -> SuriMapApiResponse = { incidentId ->
        OperationalPeriodReadRepository().list(incidentId)
    }
) {
    suspend fun resolve(incident: AssignedIncidentUiModel): IncidentContext {
        val currentContext = incident.toIncidentContext()
        if (!currentContext.currentOpId.isNullOrBlank()) {
            return currentContext
        }

        val currentOpId = loadCurrentOpId(incident.incidentId) ?: return currentContext
        return currentContext.copy(currentOpId = currentOpId)
    }

    private suspend fun loadCurrentOpId(incidentId: String): String? {
        val response = runCatching { operationalPeriods(incidentId) }.getOrNull() ?: return null
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return null
        }
        return runCatching {
            JSONObject(response.body).optString("currentOpId").takeIf(String::isNotBlank)
        }.getOrNull()
    }
}
