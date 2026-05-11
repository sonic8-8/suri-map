package com.surimap.feature.incidents.data

import com.surimap.core.incident.IncidentReadRepository
import com.surimap.core.network.SuriMapNetworkException
import com.surimap.feature.incidents.ui.AssignedIncidentUiModel
import com.surimap.feature.incidents.ui.IncidentListStatus
import com.surimap.feature.incidents.ui.IncidentListUiState
import org.json.JSONArray
import org.json.JSONObject

class IncidentListStateLoader(
    private val repository: IncidentReadRepository = IncidentReadRepository(),
    private val policePhoneLabel: String
) {
    suspend fun load(): IncidentListUiState {
        return try {
            val response = repository.list(status = "OPEN")
            when {
                response.isSuccessful -> successState(response.body)
                response.errorCode == "police_phone_not_assigned" ->
                    IncidentListUiState.empty(
                        policePhoneLabel = policePhoneLabel,
                        shouldClearIncidentContext = true
                    )
                response.errorCode == "incident_closed" ->
                    IncidentListUiState.empty(
                        policePhoneLabel = policePhoneLabel,
                        showClosedDialog = true,
                        shouldClearIncidentContext = true
                    )
                else ->
                    IncidentListUiState.error(
                        policePhoneLabel = policePhoneLabel,
                        message = "사건 목록을 불러오지 못했습니다"
                    )
            }
        } catch (_: SuriMapNetworkException) {
            IncidentListUiState.offline(policePhoneLabel = policePhoneLabel)
        }
    }

    private fun successState(body: String?): IncidentListUiState {
        val incidents = parseIncidents(body)
        return if (incidents.isEmpty()) {
            IncidentListUiState.empty(policePhoneLabel = policePhoneLabel)
        } else {
            IncidentListUiState.ready(
                policePhoneLabel = policePhoneLabel,
                incidents = incidents
            )
        }
    }

    private fun parseIncidents(body: String?): List<AssignedIncidentUiModel> {
        if (body.isNullOrBlank()) {
            return emptyList()
        }
        val items = parseItems(body)
        return List(items.length()) { index ->
            val item = items.getJSONObject(index)
            val incidentId = item.optString("incidentId").ifBlank { item.optString("id") }
            val status = item.optString("status").ifBlank { "UNKNOWN" }
            val version = item.optInt("version", -1)
            AssignedIncidentUiModel(
                incidentId = incidentId,
                currentOpId = item.optString("currentOpId").takeIf(String::isNotBlank),
                currentDutyShiftId = item.optString("currentDutyShiftId").takeIf(String::isNotBlank),
                title = item.optString("title").ifBlank { incidentId },
                summary =
                if (version >= 0) {
                    "상태 $status · v$version"
                } else {
                    "상태 $status"
                },
                packageStatus = "오프라인 패키지 확인 전",
                assignmentStatus = "이 폴리폰에서 선택 가능"
            )
        }
    }

    private fun parseItems(body: String): JSONArray {
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) {
            return JSONArray(trimmed)
        }
        return JSONObject(trimmed).optJSONArray("items") ?: JSONArray()
    }
}
