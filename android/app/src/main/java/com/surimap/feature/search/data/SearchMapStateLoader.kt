package com.surimap.feature.search.data

import com.surimap.core.incident.IncidentReadRepository
import com.surimap.core.network.SuriMapApiResponse
import com.surimap.feature.search.ui.SearchLayerKind
import com.surimap.feature.search.ui.SearchLifecycleStatus
import com.surimap.feature.search.ui.SearchMapLayerUiState
import com.surimap.feature.search.ui.SearchMapSyncStatus
import com.surimap.feature.search.ui.SearchMapUiState
import org.json.JSONObject

data class SearchMapSessionContext(
    val incidentId: String?,
    val currentOpId: String?,
    val currentDutyShiftId: String?
)

class SearchMapStateLoader(
    private val incidentDetail: suspend (String) -> SuriMapApiResponse = { incidentId ->
        IncidentReadRepository().detail(incidentId)
    }
) {
    suspend fun load(context: SearchMapSessionContext): SearchMapUiState {
        val fallback = fallback(context)
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return fallback
        val response = runCatching { incidentDetail(incidentId) }.getOrNull() ?: return fallback
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return fallback
        }
        return detailState(context, response.body, fallback)
    }

    fun fallback(context: SearchMapSessionContext): SearchMapUiState {
        val incidentTitle = context.incidentId?.takeIf(String::isNotBlank) ?: "선택한 사건"
        val hasCurrentOp = !context.currentOpId.isNullOrBlank()
        return SearchMapUiState(
            incidentTitle = incidentTitle,
            missingPersonSummary = "실종자 정보 확인 중",
            opLabel = context.currentOpId?.takeIf(String::isNotBlank)?.let { opId -> "OP $opId" } ?: "OP 확인 필요",
            dutyShiftLabel =
            context.currentDutyShiftId
                ?.takeIf(String::isNotBlank)
                ?.let { dutyShiftId -> "DutyShift $dutyShiftId" }
                ?: "DutyShift 확인 필요",
            assignmentLabel = "담당 구역 확인 중",
            syncStatus = SearchMapSyncStatus.Idle,
            lifecycleStatus =
            if (hasCurrentOp) {
                SearchLifecycleStatus.Active
            } else {
                SearchLifecycleStatus.OpRequired
            },
            unsentCount = 0,
            oldestPendingMinutes = null,
            blockedOutboxCount = 0,
            elapsedLabel = "00:00",
            movementSummary = "경로 기록 대기",
            layers =
            listOf(
                SearchMapLayerUiState("전체 수색 구역", SearchLayerKind.Overall),
                SearchMapLayerUiState("담당 구역 확인 중", SearchLayerKind.Team, highlighted = true)
            ),
            handoverPrompt = null,
            incidentAlert = null
        )
    }

    private fun detailState(
        context: SearchMapSessionContext,
        body: String,
        fallback: SearchMapUiState
    ): SearchMapUiState {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return fallback
        return fallback.copy(
            incidentTitle = json.optString("title").ifBlank {
                json.optString("incidentId").ifBlank {
                    json.optString("id").ifBlank { fallback.incidentTitle }
                }
            },
            missingPersonSummary = missingPersonSummary(json.optJSONObject("missingPerson")),
            opLabel = context.currentOpId?.takeIf(String::isNotBlank)?.let { opId -> "OP $opId" } ?: fallback.opLabel,
            dutyShiftLabel =
            context.currentDutyShiftId
                ?.takeIf(String::isNotBlank)
                ?.let { dutyShiftId -> "DutyShift $dutyShiftId" }
                ?: fallback.dutyShiftLabel
        )
    }

    private fun missingPersonSummary(missingPerson: JSONObject?): String {
        if (missingPerson == null) {
            return "실종자 정보 없음"
        }
        val displayName =
            missingPerson.optString("displayName")
                .ifBlank { missingPerson.optString("name") }
                .ifBlank { "실종자" }
        val appearanceText = missingPerson.optString("appearanceText")
        return listOf(displayName, appearanceText)
            .filter(String::isNotBlank)
            .joinToString(" · ")
    }
}
