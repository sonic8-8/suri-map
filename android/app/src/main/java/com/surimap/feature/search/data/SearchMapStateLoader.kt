package com.surimap.feature.search.data

import com.surimap.core.incident.IncidentReadRepository
import com.surimap.core.database.OutboxStatusSummary
import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.searcharea.SearchAreaReadRepository
import com.surimap.feature.search.ui.SearchLayerKind
import com.surimap.feature.search.ui.SearchLifecycleStatus
import com.surimap.feature.search.ui.SearchMapLayerUiState
import com.surimap.feature.search.ui.SearchMapSyncStatus
import com.surimap.feature.search.ui.SearchMapUiState
import com.surimap.feature.search.ui.SearchMapViewportBounds
import org.json.JSONArray
import org.json.JSONObject

data class SearchMapSessionContext(
    val incidentId: String?,
    val currentOpId: String?,
    val currentDutyShiftId: String?,
    val policePhoneId: String? = null
)

class SearchMapStateLoader(
    private val incidentDetail: suspend (String) -> SuriMapApiResponse = { incidentId ->
        IncidentReadRepository().detail(incidentId)
    },
    private val overallSearchArea: suspend (String) -> SuriMapApiResponse = { incidentId ->
        SearchAreaReadRepository().activeOverall(incidentId)
    },
    private val outboxSummary: suspend (String, String) -> OutboxStatusSummary? = { _, _ -> null },
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun load(context: SearchMapSessionContext): SearchMapUiState {
        val areaState = withOverallSearchArea(context, fallback(context))
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return areaState
        val response = runCatching { incidentDetail(incidentId) }.getOrNull() ?: return areaState
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return areaState
        }
        return detailState(context, response.body, areaState)
    }

    fun fallbackForRemember(context: SearchMapSessionContext): SearchMapUiState = fallbackState(context, summary = null)

    suspend fun fallback(context: SearchMapSessionContext): SearchMapUiState {
        return fallbackState(context, summary = context.outboxSummaryOrNull())
    }

    private fun fallbackState(
        context: SearchMapSessionContext,
        summary: OutboxStatusSummary?
    ): SearchMapUiState {
        val incidentTitle = context.incidentId?.takeIf(String::isNotBlank) ?: "선택한 사건"
        val hasCurrentOp = !context.currentOpId.isNullOrBlank()
        val normalUnsentCount = summary?.normalUnsentCount ?: 0
        val oldestPendingMinutes = summary?.oldestPendingClientRequestedAt?.let(::oldestPendingMinutes)
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
            syncStatus =
            if (normalUnsentCount > 0) {
                SearchMapSyncStatus.Offline
            } else {
                SearchMapSyncStatus.Idle
            },
            lifecycleStatus =
            if (hasCurrentOp) {
                SearchLifecycleStatus.Active
            } else {
                SearchLifecycleStatus.OpRequired
            },
            unsentCount = normalUnsentCount,
            oldestPendingMinutes = oldestPendingMinutes,
            blockedOutboxCount = summary?.finalFailedCount ?: 0,
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

    private suspend fun withOverallSearchArea(
        context: SearchMapSessionContext,
        fallback: SearchMapUiState
    ): SearchMapUiState {
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return fallback
        val response = runCatching { overallSearchArea(incidentId) }.getOrNull() ?: return fallback
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return fallback
        }
        val area = runCatching { JSONObject(response.body) }.getOrNull() ?: return fallback
        val geometry = area.optJSONObject("geometry") ?: return fallback
        val bounds = viewportBounds(area, geometry) ?: return fallback
        return fallback.copy(
            viewportBounds = bounds,
            layers =
            listOf(
                SearchMapLayerUiState(
                    label = "전체 수색 구역",
                    kind = SearchLayerKind.Overall,
                    overlayId = area.optString("id").ifBlank { "overall-search-area" },
                    geoJson = geometry.toString()
                )
            )
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

    private suspend fun SearchMapSessionContext.outboxSummaryOrNull(): OutboxStatusSummary? {
        val incidentId = incidentId?.takeIf(String::isNotBlank) ?: return null
        val policePhoneId = policePhoneId?.takeIf(String::isNotBlank) ?: return null
        return runCatching { outboxSummary(incidentId, policePhoneId) }.getOrNull()
    }

    private fun oldestPendingMinutes(clientRequestedAt: Long): Int {
        return ((nowMs() - clientRequestedAt).coerceAtLeast(0L) / MILLIS_PER_MINUTE).toInt()
    }

    private fun viewportBounds(area: JSONObject, geometry: JSONObject): SearchMapViewportBounds? {
        area.optJSONArray("bbox")?.let { bbox ->
            if (bbox.length() >= 4) {
                return SearchMapViewportBounds(
                    south = bbox.optDouble(1),
                    west = bbox.optDouble(0),
                    north = bbox.optDouble(3),
                    east = bbox.optDouble(2)
                ).takeIf { it.hasFiniteEdges() }
            }
        }

        val points = mutableListOf<Pair<Double, Double>>()
        geometry.optJSONArray("coordinates")?.collectPositions(points)
        if (points.isEmpty()) {
            return null
        }
        val longitudes = points.map { it.first }
        val latitudes = points.map { it.second }
        return SearchMapViewportBounds(
            south = latitudes.minOrNull() ?: return null,
            west = longitudes.minOrNull() ?: return null,
            north = latitudes.maxOrNull() ?: return null,
            east = longitudes.maxOrNull() ?: return null
        ).takeIf { it.hasFiniteEdges() }
    }

    private fun JSONArray.collectPositions(points: MutableList<Pair<Double, Double>>) {
        if (length() >= 2 && opt(0) is Number && opt(1) is Number) {
            val longitude = optDouble(0)
            val latitude = optDouble(1)
            if (longitude.isFinite() && latitude.isFinite()) {
                points.add(longitude to latitude)
            }
            return
        }
        repeat(length()) { index ->
            optJSONArray(index)?.collectPositions(points)
        }
    }

    private fun SearchMapViewportBounds.hasFiniteEdges(): Boolean {
        return south.isFinite() && west.isFinite() && north.isFinite() && east.isFinite()
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
