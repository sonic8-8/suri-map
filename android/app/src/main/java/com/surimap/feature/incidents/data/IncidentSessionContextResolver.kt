package com.surimap.feature.incidents.data

import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.operationalperiod.DutyShiftQuery
import com.surimap.core.operationalperiod.DutyShiftRepository
import com.surimap.core.operationalperiod.OperationalPeriodReadRepository
import com.surimap.feature.incidents.ui.AssignedIncidentUiModel
import com.surimap.ui.navigation.IncidentContext
import org.json.JSONArray
import org.json.JSONObject

class IncidentSessionContextResolver(
    private val operationalPeriods: suspend (String) -> SuriMapApiResponse = { incidentId ->
        OperationalPeriodReadRepository().list(incidentId)
    },
    private val dutyShifts: suspend (DutyShiftQuery) -> SuriMapApiResponse = { query ->
        DutyShiftRepository().listDutyShifts(query)
    }
) {
    suspend fun resolve(incident: AssignedIncidentUiModel, policePhoneId: String? = null): IncidentContext {
        val currentContext = incident.toIncidentContext()
        val resolvedCurrentOp = loadCurrentOp(incident.incidentId)
        val currentOpId = currentContext.currentOpId?.takeIf(String::isNotBlank) ?: resolvedCurrentOp?.id
        val currentOpLabel = currentContext.currentOpLabel?.takeIf(String::isNotBlank) ?: resolvedCurrentOp?.label
        val opContext =
            if (currentOpId != null || currentOpLabel != null) {
                currentContext.copy(
                    currentOpId = currentOpId,
                    currentOpLabel = currentOpLabel
                )
            } else {
                currentContext
            }
        if (!opContext.currentDutyShiftId.isNullOrBlank()) {
            return opContext
        }

        val dutyShiftId = loadActiveDutyShiftId(
            incidentId = incident.incidentId,
            opId = currentOpId,
            policePhoneId = policePhoneId
        ) ?: return opContext
        return opContext.copy(currentDutyShiftId = dutyShiftId)
    }

    private suspend fun loadCurrentOp(incidentId: String): ResolvedCurrentOp? {
        val response = runCatching { operationalPeriods(incidentId) }.getOrNull() ?: return null
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return null
        }
        return runCatching {
            val json = JSONObject(response.body)
            val currentOpId = json.optString("currentOpId").takeIf(String::isNotBlank) ?: return@runCatching null
            ResolvedCurrentOp(
                id = currentOpId,
                label = resolveCurrentOpLabel(json.optJSONArray("items"), currentOpId)
            )
        }.getOrNull()
    }

    private suspend fun loadActiveDutyShiftId(
        incidentId: String,
        opId: String?,
        policePhoneId: String?
    ): String? {
        val normalizedOpId = opId?.takeIf(String::isNotBlank) ?: return null
        val normalizedPolicePhoneId = policePhoneId?.takeIf(String::isNotBlank) ?: return null
        val response =
            runCatching {
                dutyShifts(
                    DutyShiftQuery(
                        incidentId = incidentId,
                        opId = normalizedOpId,
                        policePhoneId = normalizedPolicePhoneId,
                        status = "ACTIVE"
                    )
                )
            }.getOrNull() ?: return null
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return null
        }
        return runCatching {
            val items = JSONObject(response.body).optJSONArray("items") ?: JSONArray()
            activeDutyShiftId(items)
        }.getOrNull()
    }

    private fun activeDutyShiftId(items: JSONArray): String? {
        repeat(items.length()) { index ->
            val item = items.optJSONObject(index) ?: return@repeat
            val status = item.optString("status")
            if (status.isBlank() || status.equals("ACTIVE", ignoreCase = true)) {
                val dutyShiftId = item.optString("id")
                    .ifBlank { item.optString("dutyShiftId") }
                if (dutyShiftId.isNotBlank()) {
                    return dutyShiftId
                }
            }
        }
        return null
    }

    private fun resolveCurrentOpLabel(items: JSONArray?, currentOpId: String): String? {
        if (items == null) {
            return null
        }
        repeat(items.length()) { index ->
            val item = items.optJSONObject(index) ?: return@repeat
            if (item.optString("id") != currentOpId) {
                return@repeat
            }
            val sequenceNumber = item.optInt("sequenceNumber", -1)
            if (sequenceNumber > 0) {
                return "OP ${sequenceNumber}차"
            }
        }
        return null
    }

    private data class ResolvedCurrentOp(
        val id: String,
        val label: String?
    )
}
