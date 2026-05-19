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
            AssignedIncidentUiModel(
                incidentId = incidentId,
                currentOpId = item.optString("currentOpId").takeIf(String::isNotBlank),
                currentDutyShiftId = item.optString("currentDutyShiftId").takeIf(String::isNotBlank),
                title = item.optString("title").userFacingIncidentTitle(incidentId),
                summary = item.incidentSummary(),
                packageStatus = "오프라인 패키지 확인 전",
                assignmentStatus = "이 폴리폰에서 선택 가능"
            )
        }
    }

    private fun JSONObject.incidentSummary(): String {
        val missingPerson = optJSONObject("missingPerson")
        val displayName =
            missingPerson
                ?.optString("displayName")
                ?.ifBlank { missingPerson.optString("name") }
                ?.withoutMissingPersonCode()
                .orEmpty()
        val appearance = missingPerson?.optString("appearanceText").orEmpty()
        return listOf(displayName, appearance)
            .filter(String::isNotBlank)
            .joinToString(" · ")
            .ifBlank { "현장 수색 진행 중" }
    }

    private fun String.userFacingIncidentTitle(incidentId: String): String {
        val cleaned =
            replace(UUID_LIKE_TEXT, "")
                .replace(ZERO_ID_TEXT, "")
                .replace(Regex("""\s*[#·|/-]\s*$"""), "")
                .trim()
        if (cleaned.isBlank() || cleaned == incidentId || UUID_LIKE_TEXT.matches(cleaned)) {
            return "배정 사건"
        }
        return cleaned
    }

    private fun String.withoutMissingPersonCode(): String =
        replace(MISSING_PERSON_CODE_TEXT, "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim(' ', '·', '-', '_')

    private fun parseItems(body: String): JSONArray {
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) {
            return JSONArray(trimmed)
        }
        return JSONObject(trimmed).optJSONArray("items") ?: JSONArray()
    }

    private companion object {
        val UUID_LIKE_TEXT = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
        val ZERO_ID_TEXT = Regex("""0{4,}""")
        val MISSING_PERSON_CODE_TEXT = Regex("""\b[A-Z]\d+-[가-힣A-Za-z0-9]+-\d+\b""")
    }
}
