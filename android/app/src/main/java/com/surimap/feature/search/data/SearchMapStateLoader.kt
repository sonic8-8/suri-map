package com.surimap.feature.search.data

import com.surimap.core.database.LocalMarkerEntity
import com.surimap.core.database.OutboxStatusSummary
import com.surimap.core.incident.IncidentReadRepository
import com.surimap.core.marker.MarkerReadQuery
import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.offline.OfflinePackageManifestQuery
import com.surimap.core.offline.OfflinePackageRepository
import com.surimap.core.path.SearchPathQuery
import com.surimap.core.path.SearchPathRepository
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
    val currentOpLabel: String? = null,
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
    private val opSearchAreas: suspend (String, String) -> SuriMapApiResponse = { incidentId, opId ->
        SearchAreaReadRepository().list(incidentId = incidentId, opId = opId, status = "ACTIVE")
    },
    private val searchPaths: suspend (SearchPathQuery) -> SuriMapApiResponse = { query ->
        SearchPathRepository().listSearchPaths(query)
    },
    private val liveMarkers: suspend (MarkerReadQuery) -> SuriMapApiResponse = {
        SuriMapApiResponse(statusCode = 404, body = null, errorCode = null)
    },
    private val initialMarkers: suspend (String, String) -> SuriMapApiResponse = { incidentId, policePhoneId ->
        OfflinePackageRepository().manifest(
            OfflinePackageManifestQuery(
                incidentId = incidentId,
                policePhoneId = policePhoneId
            )
        )
    },
    private val outboxSummary: suspend (String, String) -> OutboxStatusSummary? = { _, _ -> null },
    private val pendingMarkers: suspend (String, String) -> List<LocalMarkerEntity> = { _, _ -> emptyList() },
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun load(context: SearchMapSessionContext): SearchMapUiState {
        val areaState = withOpSearchAreas(context, withOverallSearchArea(context, fallback(context)))
        val mapState = withSearchPaths(context, areaState)
        val liveMarkerResult = withLiveMarkers(context, mapState)
        val serverMarkerState =
            if (liveMarkerResult.loaded) {
                liveMarkerResult.state
            } else {
                withInitialMarkers(context, mapState)
            }
        val markerState = withPendingMarkers(context, serverMarkerState)
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return markerState
        val response = runCatching { incidentDetail(incidentId) }.getOrNull() ?: return markerState
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return markerState
        }
        return detailState(context, response.body, markerState)
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
            opLabel = context.currentOpLabel?.takeIf(String::isNotBlank)
                ?: context.currentOpId?.takeIf(String::isNotBlank)?.let { opId -> "OP $opId" }
                ?: "OP 확인 필요",
            dutyShiftLabel =
            context.currentDutyShiftId
                ?.takeIf(String::isNotBlank)
                ?.let { dutyShiftId -> "DutyShift $dutyShiftId" }
                ?: "DutyShift 확인 필요",
            assignmentLabel = "",
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

    private suspend fun withOpSearchAreas(
        context: SearchMapSessionContext,
        state: SearchMapUiState
    ): SearchMapUiState {
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return state
        val opId = context.currentOpId?.takeIf(String::isNotBlank) ?: return state
        val response = runCatching { opSearchAreas(incidentId, opId) }.getOrNull() ?: return state
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return state
        }
        val opLayers = searchAreaLayers(response.body)
        if (opLayers.isEmpty()) {
            return state
        }
        return state.copy(
            assignmentLabel = opLayers.assignmentLabel() ?: state.assignmentLabel,
            layers =
            state.layers
                .filterNot { layer -> layer.kind != SearchLayerKind.Overall && layer.geoJson == null } + opLayers
        )
    }

    private suspend fun withSearchPaths(
        context: SearchMapSessionContext,
        state: SearchMapUiState
    ): SearchMapUiState {
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return state
        val opId = context.currentOpId?.takeIf(String::isNotBlank) ?: return state
        val policePhoneId = context.policePhoneId?.takeIf(String::isNotBlank) ?: return state
        val response =
            runCatching {
                searchPaths(
                    SearchPathQuery(
                        incidentId = incidentId,
                        opId = opId,
                        policePhoneId = policePhoneId,
                        includeGeometry = true,
                        geometryMode = "RENDER_SIMPLIFIED",
                        limit = 500,
                        sort = "startedAtAsc"
                    )
                )
            }.getOrNull()
                ?: return state
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return state
        }
        val pathLayers = searchPathLayers(response.body)
        if (pathLayers.isEmpty()) {
            return state
        }
        return state.copy(
            movementSummary = "경로 ${pathLayers.size}개 표시",
            layers = state.layers + pathLayers
        ).withViewportFromLayers(pathLayers)
    }

    private suspend fun withInitialMarkers(
        context: SearchMapSessionContext,
        state: SearchMapUiState
    ): SearchMapUiState {
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return state
        val policePhoneId = context.policePhoneId?.takeIf(String::isNotBlank) ?: return state
        val response = runCatching { initialMarkers(incidentId, policePhoneId) }.getOrNull() ?: return state
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return state
        }
        val markerLayers = initialMarkerLayers(response.body)
        if (markerLayers.isEmpty()) {
            return state
        }
        return state.copy(layers = state.layers + markerLayers).withViewportFromLayers(markerLayers)
    }

    private suspend fun withLiveMarkers(
        context: SearchMapSessionContext,
        state: SearchMapUiState
    ): LiveMarkerLoadResult {
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return LiveMarkerLoadResult(state, false)
        val opId = context.currentOpId?.takeIf(String::isNotBlank) ?: return LiveMarkerLoadResult(state, false)
        val response =
            runCatching {
                liveMarkers(
                    MarkerReadQuery(
                        incidentId = incidentId,
                        opId = opId
                    )
                )
            }.getOrNull()
                ?: return LiveMarkerLoadResult(state, false)
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return LiveMarkerLoadResult(state, false)
        }
        val markerLayers = liveMarkerLayers(response.body)
        return LiveMarkerLoadResult(
            state.copy(layers = state.layers + markerLayers).withViewportFromLayers(markerLayers),
            true
        )
    }

    private suspend fun withPendingMarkers(
        context: SearchMapSessionContext,
        state: SearchMapUiState
    ): SearchMapUiState {
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return state
        val policePhoneId = context.policePhoneId?.takeIf(String::isNotBlank) ?: return state
        val markerLayers =
            runCatching { pendingMarkers(incidentId, policePhoneId) }
                .getOrDefault(emptyList())
                .pendingMarkerLayers()
        if (markerLayers.isEmpty()) {
            return state
        }
        return state.copy(layers = state.layers + markerLayers).withViewportFromLayers(markerLayers)
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
            opLabel = context.currentOpLabel?.takeIf(String::isNotBlank)
                ?: context.currentOpId?.takeIf(String::isNotBlank)?.let { opId -> "OP $opId" }
                ?: fallback.opLabel,
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

    private fun searchAreaLayers(body: String): List<SearchMapLayerUiState> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val areas = root.optJSONArray("areas") ?: root.optJSONArray("items") ?: return emptyList()
        return buildList {
            repeat(areas.length()) { index ->
                val area = areas.optJSONObject(index) ?: return@repeat
                val geometry = area.optJSONObject("geometry") ?: return@repeat
                val kind = area.searchLayerKind() ?: return@repeat
                val id = area.optString("id").ifBlank { "op-${kind.name.lowercase()}-$index" }
                add(
                    SearchMapLayerUiState(
                        label = area.labelFor(kind),
                        kind = kind,
                        highlighted = kind == SearchLayerKind.Team,
                        overlayId = id,
                        geoJson = geometry.toString()
                    )
                )
            }
        }
    }

    private fun initialMarkerLayers(body: String): List<SearchMapLayerUiState> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val markers = root.optJSONArray("initialMarkers") ?: root.optJSONArray("markers") ?: return emptyList()
        return buildList {
            repeat(markers.length()) { index ->
                val marker = markers.optJSONObject(index) ?: return@repeat
                val status = marker.optString("status").uppercase()
                if (status == "DELETED") {
                    return@repeat
                }
                val location = marker.optJSONObject("location") ?: marker.optJSONObject("geometry") ?: return@repeat
                if (!location.optString("type").equals("Point", ignoreCase = true)) {
                    return@repeat
                }
                add(
                    SearchMapLayerUiState(
                        label = marker.markerLabel(),
                        kind = SearchLayerKind.Marker,
                        highlighted = true,
                        overlayId = marker.optString("id").ifBlank { "initial-marker-$index" },
                        geoJson = location.toString()
                    )
                )
            }
        }
    }

    private fun liveMarkerLayers(body: String): List<SearchMapLayerUiState> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val markers = root.optJSONArray("markers") ?: root.optJSONArray("items") ?: return emptyList()
        return buildList {
            repeat(markers.length()) { index ->
                val marker = markers.optJSONObject(index) ?: return@repeat
                val status = marker.optString("status").uppercase()
                if (status == "DELETED") {
                    return@repeat
                }
                val location = marker.optJSONObject("location") ?: marker.optJSONObject("geometry") ?: return@repeat
                if (!location.optString("type").equals("Point", ignoreCase = true)) {
                    return@repeat
                }
                add(
                    SearchMapLayerUiState(
                        label = marker.markerLabel(),
                        kind = SearchLayerKind.Marker,
                        highlighted = true,
                        overlayId = marker.optString("id").ifBlank { "live-marker-$index" },
                        geoJson = location.toString()
                    )
                )
            }
        }
    }

    private fun List<LocalMarkerEntity>.pendingMarkerLayers(): List<SearchMapLayerUiState> =
        map { marker ->
            SearchMapLayerUiState(
                label = "${marker.type.markerTypeLabel()} · 전송 대기",
                kind = SearchLayerKind.Marker,
                highlighted = true,
                overlayId = marker.localMarkerId,
                geoJson = """{"type":"Point","coordinates":[${marker.lon},${marker.lat}]}"""
            )
        }

    private fun List<SearchMapLayerUiState>.assignmentLabel(): String? =
        firstOrNull { layer -> layer.kind == SearchLayerKind.Team }
            ?.label
            ?.takeIf(String::isNotBlank)

    private fun searchPathLayers(body: String): List<SearchMapLayerUiState> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val paths = root.optJSONArray("paths") ?: root.optJSONArray("items") ?: return emptyList()
        return buildList {
            repeat(paths.length()) { index ->
                val path = paths.optJSONObject(index) ?: return@repeat
                val geometry = path.optJSONObject("geometry") ?: return@repeat
                if (!geometry.optString("type").equals("LineString", ignoreCase = true)) {
                    return@repeat
                }
                val active = path.optString("status").uppercase() in setOf("ACTIVE", "RECORDING")
                add(
                    SearchMapLayerUiState(
                        label = if (active) "현재 경로" else "기존 경로",
                        kind = SearchLayerKind.Path,
                        highlighted = active,
                        overlayId = path.optString("id").ifBlank { "search-path-$index" },
                        geoJson = geometry.toString()
                    )
                )
            }
        }
    }

    private fun JSONObject.searchLayerKind(): SearchLayerKind? {
        val areaLevel =
            optString("areaLevel")
                .ifBlank { optString("area_level") }
                .uppercase()
        return when (areaLevel) {
            "UNIT" -> SearchLayerKind.Unit
            "TEAM" -> SearchLayerKind.Team
            "OVERALL" -> null
            else ->
                if (
                    optString("parentAreaId").isNotBlank() ||
                    optString("parent_area_id").isNotBlank()
                ) {
                    SearchLayerKind.Team
                } else {
                    SearchLayerKind.Unit
                }
        }
    }

    private fun JSONObject.labelFor(kind: SearchLayerKind): String {
        return optString("name")
            .ifBlank { optString("label") }
            .ifBlank { optString("displayName") }
            .ifBlank {
                when (kind) {
                    SearchLayerKind.Overall -> "전체 수색 구역"
                    SearchLayerKind.Unit -> "부대 수색 구역"
                    SearchLayerKind.Team -> "팀 담당 구역"
                    SearchLayerKind.Path -> "수색 경로"
                    SearchLayerKind.Marker -> "마커"
                }
            }
    }

    private fun JSONObject.markerLabel(): String {
        return optString("label")
            .ifBlank { optString("displayName") }
            .ifBlank {
                optString("type").markerTypeLabel()
            }
    }

    private fun String.markerTypeLabel(): String =
        when (uppercase()) {
            "CLUE" -> "단서"
            "PERSON_FOUND" -> "실종자 발견"
            "FIELD_CONDITION" -> "현장 상태"
            "SUPPORT_REQUEST" -> "지원 요청"
            "NOTE" -> "메모"
            else -> "마커"
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

        return viewportBounds(geometry)
    }

    private fun viewportBounds(geometry: JSONObject): SearchMapViewportBounds? {
        val points = mutableListOf<Pair<Double, Double>>()
        geometry.optJSONArray("coordinates")?.collectPositions(points)
        return points.toViewportBounds()
    }

    private fun SearchMapUiState.withViewportFromLayers(
        candidateLayers: List<SearchMapLayerUiState>
    ): SearchMapUiState {
        if (viewportBounds != null) {
            return this
        }
        val points = mutableListOf<Pair<Double, Double>>()
        candidateLayers.forEach { layer ->
            val geoJson = layer.geoJson?.takeIf(String::isNotBlank) ?: return@forEach
            val geometry = runCatching { JSONObject(geoJson) }.getOrNull() ?: return@forEach
            geometry.optJSONArray("coordinates")?.collectPositions(points)
        }
        return points.toViewportBounds()?.let { bounds -> copy(viewportBounds = bounds) } ?: this
    }

    private fun List<Pair<Double, Double>>.toViewportBounds(): SearchMapViewportBounds? {
        if (isEmpty()) {
            return null
        }
        val longitudes = map { it.first }
        val latitudes = map { it.second }
        val south = latitudes.minOrNull() ?: return null
        val west = longitudes.minOrNull() ?: return null
        val north = latitudes.maxOrNull() ?: return null
        val east = longitudes.maxOrNull() ?: return null
        val latDelta = if (south == north) POINT_VIEWPORT_DELTA else 0.0
        val lonDelta = if (west == east) POINT_VIEWPORT_DELTA else 0.0
        return SearchMapViewportBounds(
            south = south - latDelta,
            west = west - lonDelta,
            north = north + latDelta,
            east = east + lonDelta
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
        const val POINT_VIEWPORT_DELTA = 0.003
    }
}

private data class LiveMarkerLoadResult(
    val state: SearchMapUiState,
    val loaded: Boolean
)
